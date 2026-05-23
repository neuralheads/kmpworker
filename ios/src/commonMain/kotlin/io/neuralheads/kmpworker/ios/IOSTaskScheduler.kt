@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.neuralheads.kmpworker.ios

import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.scheduler.TaskScheduler
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError

/**
 * iOS implementation of [TaskScheduler] backed by BGTaskScheduler.
 *
 * ⚠️ iOS Background Execution Limitations:
 * - Apple's OS controls when tasks actually run. KMPWorker cannot override this.
 * - BGAppRefreshTask (OneTime) has a strict time limit (~30 seconds).
 * - BGProcessingTask (Periodic) may only run when device is idle and charging.
 * - Tasks are NOT guaranteed to execute at requested intervals.
 * - Always handle [TaskState.Failed] — the OS may kill the task at any time.
 *
 * Required setup in AppDelegate:
 * ```swift
 * BackgroundInitializerKt.initialize()
 * ```
 *
 * Required in Info.plist:
 * ```xml
 * <key>BGTaskSchedulerPermittedIdentifiers</key>
 * <array>
 *     <string>your-task-id</string>
 * </array>
 * ```
 */
class IOSTaskScheduler : TaskScheduler {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun enqueue(request: TaskRequest) {
        when (request.type) {
            is TaskType.OneTime -> {
                val taskRequest = BGAppRefreshTaskRequest(identifier = request.id)
                submitRequest(taskRequest, request.id)
            }

            is TaskType.ExactTime -> {
                // BGAppRefreshTaskRequest with earliestBeginDate = best-effort exact on iOS.
                // iOS guarantees it will NOT run BEFORE this date, but may delay further.
                val taskRequest = BGAppRefreshTaskRequest(identifier = request.id).apply {
                    val epochSeconds = request.type.let { it as TaskType.ExactTime }.runAtMillis / 1000.0
                    earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds)
                }
                submitRequest(taskRequest, request.id)
            }

            is TaskType.Periodic -> {
                val taskRequest = BGProcessingTaskRequest(identifier = request.id).apply {
                    requiresNetworkConnectivity = request.constraints.requiresInternet
                    requiresExternalPower = request.constraints.requiresCharging
                }
                submitRequest(taskRequest, request.id)
            }
        }

        TaskMonitor.tryEmit(request.id, TaskState.Scheduled)
    }

    override suspend fun cancel(taskId: String) {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(taskId)
    }

    override suspend fun cancelByTag(tag: String) {
        // iOS uses task IDs not tags; cancel all registered tasks as best-effort
        TaskRegistry.registeredIds().forEach { id ->
            BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(id)
        }
    }

    override fun observe(taskId: String): Flow<TaskState> =
        TaskMonitor.observe(taskId)

    override fun observeAll(): Flow<Pair<String, TaskState>> =
        TaskMonitor.observeAll()

    override fun register(taskId: String, block: suspend () -> Unit) {
        TaskRegistry.register(taskId) { block() }
    }

    override fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit) {
        TaskRegistry.register(taskId, block)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun submitRequest(
        request: platform.BackgroundTasks.BGTaskRequest,
        taskId: String
    ) {
        // NSError** interop requires memScoped + ObjCObjectVar pattern
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val success = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, errorPtr.ptr)
            if (!success) {
                val msg = errorPtr.value?.localizedDescription ?: "Unknown error"
                TaskMonitor.tryEmit(
                    taskId, TaskState.Failed(
                        throwable = Exception("BGTaskScheduler submit failed: $msg")
                    )
                )
            }
        }
    }
}
