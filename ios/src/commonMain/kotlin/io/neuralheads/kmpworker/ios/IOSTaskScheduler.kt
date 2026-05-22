package io.neuralheads.kmpworker.ios

import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.scheduler.TaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
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
        val bgScheduler = BGTaskScheduler.sharedScheduler

        when (request.type) {
            is TaskType.OneTime -> {
                val taskRequest = BGAppRefreshTaskRequest(identifier = request.id)
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

    override fun observe(taskId: String): Flow<TaskState> =
        TaskMonitor.observe(taskId)

    override fun register(taskId: String, block: suspend () -> Unit) {
        TaskRegistry.register(taskId) { block() }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun submitRequest(
        request: platform.BackgroundTasks.BGTaskRequest,
        taskId: String
    ) {
        var error: NSError? = null
        val success = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error)
        if (!success) {
            val msg = error?.localizedDescription ?: "Unknown error"
            TaskMonitor.tryEmit(taskId, TaskState.Failed(
                throwable = Exception("BGTaskScheduler submit failed: $msg")
            ))
        }
    }
}
