package io.neuralheads.kmpworker.ios

import io.neuralheads.kmpworker.core.ChainPolicy
import io.neuralheads.kmpworker.core.ChainRepository
import io.neuralheads.kmpworker.core.EventStore
import io.neuralheads.kmpworker.core.ExecutionRecord
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskChain
import io.neuralheads.kmpworker.core.TaskChainExecutor
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * iOS concrete implementation of [KmpWorker].
 *
 * **Required startup**: Call [initialize] from your `AppDelegate` BEFORE the app
 * finishes launching. This registers all task identifiers with BGTaskScheduler.
 *
 * ```swift
 * // AppDelegate.swift
 * let kmpWorker = IOSKmpWorker()
 *
 * func application(
 *     _ application: UIApplication,
 *     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
 * ) -> Bool {
 *     kmpWorker.initialize()
 *     return true
 * }
 *
 * // Required for NSURLSession background downloads to survive termination:
 * func application(
 *     _ application: UIApplication,
 *     handleEventsForBackgroundURLSession identifier: String,
 *     completionHandler: @escaping () -> Void
 * ) {
 *     IOSBackgroundDownloadWorker.handleBackgroundSession(
 *         identifier: identifier,
 *         completionHandler: completionHandler
 *     )
 * }
 * ```
 *
 * @param eventStore  Optional. Install to persist terminal events for cold-launch replay.
 * @param chainRepo   Optional. Required for [enqueueChain] support.
 *
 * ⚠️ See docs/ios-limitations.md for BGTaskScheduler execution constraints.
 */
class IOSKmpWorker(
    eventStore: EventStore? = null,
    chainRepo: ChainRepository? = null,
    private val telemetry: TelemetryCollector? = null
) : KmpWorker {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scheduler = IOSTaskScheduler()
    private val chainExecutor = chainRepo?.let { TaskChainExecutor(this, it, appScope) }

    /** Exposes the download worker, pre-wired to the EventStore for persistence. */
    val backgroundDownloads = IOSBackgroundDownloadWorker(eventStore)

    init {
        if (eventStore != null) {
            TaskMonitor.install(eventStore)
        }
    }

    /**
     * Registers all task IDs currently in [TaskRegistry] with BGTaskScheduler,
     * replays any persisted events from the last session, and restores in-progress chains.
     *
     * Must be called from AppDelegate before the app finishes launching.
     */
    fun initialize() {
        val ids = TaskRegistry.registeredIds().toList()
        KmpWorkerLogger.i("IOSKmpWorker: initializing ${ids.size} task(s) with BGTaskScheduler")
        BackgroundInitializer.initialize(ids)

        // Replay persisted events and prune old ones
        appScope.launch {
            TaskMonitor.replayPendingEvents()
            TaskMonitor.pruneOldEvents()
        }
    }

    override suspend fun enqueue(request: TaskRequest) =
        scheduler.enqueue(request)

    override suspend fun cancel(taskId: String) {
        scheduler.cancel(taskId)
        TaskMonitor.cancel(taskId)
        KmpWorkerLogger.i("IOSKmpWorker: cancelled '$taskId'")
    }

    override suspend fun cancelByTag(tag: String) {
        TaskRegistry.registeredIds().forEach { id -> scheduler.cancel(id) }
        KmpWorkerLogger.w("IOSKmpWorker: cancelByTag('$tag') — iOS cancels by identifier. Cancelled all registered tasks.")
    }

    override fun observe(taskId: String): Flow<TaskState> = scheduler.observe(taskId)

    override fun observeAll(): Flow<Pair<String, TaskState>> = TaskMonitor.observeAll()

    override fun register(taskId: String, block: suspend () -> Unit) {
        scheduler.register(taskId, block)
    }

    override fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit) {
        TaskRegistry.register(taskId, block)
    }

    override suspend fun enqueueChain(chain: TaskChain, policy: ChainPolicy) {
        val executor = chainExecutor
            ?: error("enqueueChain() requires a ChainRepository. Pass chainRepo to IOSKmpWorker constructor.")
        executor.execute(chain, policy)
    }

    override fun observeChain(chainId: String): Flow<TaskState> = TaskMonitor.observe(chainId)

    override suspend fun getExecutionHistory(limit: Int): List<ExecutionRecord> =
        telemetry?.getHistory(limit) ?: emptyList()

    override suspend fun clearExecutionHistory() {
        telemetry?.clearHistory()
    }
}
