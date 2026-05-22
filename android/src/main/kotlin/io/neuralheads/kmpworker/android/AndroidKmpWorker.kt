package io.neuralheads.kmpworker.android

import android.content.Context
import io.neuralheads.kmpworker.core.ChainRepository
import io.neuralheads.kmpworker.core.EventStore
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskChain
import io.neuralheads.kmpworker.core.TaskChainExecutor
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Android concrete implementation of [KmpWorker].
 *
 * Instantiate once (e.g. in your Application class) and inject throughout the app.
 * Jetpack Startup (`KmpWorkerInitializer`) pre-warms WorkManager automatically —
 * no manual initialization required beyond creating this instance.
 *
 * ```kotlin
 * class MyApp : Application() {
 *     val kmpWorker: KmpWorker by lazy {
 *         AndroidKmpWorker(
 *             context     = this,
 *             eventStore  = SqlDelightEventStore(database),     // optional, enables cold-launch replay
 *             chainRepo   = SqlDelightChainRepository(database) // optional, enables TaskChain support
 *         )
 *     }
 * }
 * ```
 *
 * @param context    Application context.
 * @param eventStore Optional persistent event store. Install to enable cold-launch completion replay.
 * @param chainRepo  Optional chain repository. Required for [enqueueChain] support.
 */
class AndroidKmpWorker(
    context: Context,
    eventStore: EventStore? = null,
    chainRepo: ChainRepository? = null
) : KmpWorker {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scheduler = AndroidTaskScheduler(context.applicationContext)
    private val chainExecutor = chainRepo?.let { TaskChainExecutor(this, it, appScope) }

    init {
        // Install EventStore for persistent cold-launch event replay
        if (eventStore != null) {
            TaskMonitor.install(eventStore)
            appScope.launch {
                TaskMonitor.replayPendingEvents()
                TaskMonitor.pruneOldEvents()
            }
        }
    }

    override suspend fun enqueue(request: TaskRequest) =
        scheduler.enqueue(request)

    override suspend fun cancel(taskId: String) {
        scheduler.cancel(taskId)
        TaskMonitor.cancel(taskId)
        KmpWorkerLogger.i("AndroidKmpWorker: cancelled '$taskId'")
    }

    override suspend fun cancelByTag(tag: String) {
        // WorkManager supports tag-based cancellation natively
        androidx.work.WorkManager.getInstance(
            (scheduler as AndroidTaskScheduler).context
        ).cancelAllWorkByTag(tag)
        KmpWorkerLogger.i("AndroidKmpWorker: cancelled all tasks with tag '$tag'")
    }

    override fun observe(taskId: String): Flow<TaskState> =
        scheduler.observe(taskId)

    override fun observeAll(): Flow<Pair<String, TaskState>> =
        TaskMonitor.observeAll()

    override fun register(taskId: String, block: suspend () -> Unit) =
        scheduler.register(taskId, block)

    override fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit) {
        TaskRegistry.register(taskId, block)
    }

    override suspend fun enqueueChain(chain: TaskChain) {
        val executor = chainExecutor
            ?: error("enqueueChain() requires a ChainRepository. Pass chainRepo to AndroidKmpWorker constructor.")
        executor.execute(chain)
    }

    override fun observeChain(chainId: String): Flow<TaskState> =
        TaskMonitor.observe(chainId)
}
