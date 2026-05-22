package io.neuralheads.kmpworker.ios

import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler

/**
 * Handles one-time iOS BGTaskScheduler setup.
 *
 * MUST be called from your AppDelegate BEFORE the app finishes launching:
 *
 * ```swift
 * // AppDelegate.swift
 * func application(
 *     _ application: UIApplication,
 *     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
 * ) -> Bool {
 *     BackgroundInitializerKt.initialize()
 *     return true
 * }
 * ```
 *
 * Also register all task identifiers in Info.plist:
 * ```xml
 * <key>BGTaskSchedulerPermittedIdentifiers</key>
 * <array>
 *     <string>sync-users</string>
 *     <string>upload-logs</string>
 * </array>
 * ```
 *
 * Internally registers handlers for every task ID in [TaskRegistry].
 * Each handler:
 * - Emits [TaskState.Running]
 * - Executes the registered suspend function
 * - Emits [TaskState.Success] or [TaskState.Failed]
 * - Sets task completion via [BGTask.setTaskCompletedWithSuccess]
 * - Attaches an expiration handler to gracefully handle OS interruption
 */
object BackgroundInitializer {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Registers all task identifiers with BGTaskScheduler.
     *
     * Call this exactly once during app startup.
     * Calling it multiple times is safe but redundant.
     */
    fun initialize(taskIds: List<String>) {
        taskIds.forEach { taskId ->
            BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
                identifier = taskId,
                usingQueue = null
            ) { task ->
                task ?: return@registerForTaskWithIdentifier
                handleTask(task, taskId)
            }
        }
    }

    private fun handleTask(task: BGTask, taskId: String) {
        val job = scope.launch {
            try {
                TaskMonitor.emit(taskId, TaskState.Running)
                TaskRegistry.execute(taskId)
                TaskMonitor.emit(taskId, TaskState.Success)
                task.setTaskCompletedWithSuccess(true)
            } catch (e: Exception) {
                TaskMonitor.emit(taskId, TaskState.Failed(throwable = e))
                task.setTaskCompletedWithSuccess(false)
            }
        }

        // iOS may terminate the task at any time — handle expiration gracefully.
        // Cancel the coroutine to avoid leaking work.
        task.expirationHandler = {
            job.cancel()
            TaskMonitor.tryEmit(taskId, TaskState.Failed(
                throwable = Exception("Task '$taskId' expired — iOS terminated the background window.")
            ))
            task.setTaskCompletedWithSuccess(false)
        }
    }
}
