package io.neuralheads.kmpworker.ios

import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Wraps a Kotlin [Flow] for easy consumption from Swift.
 *
 * Swift cannot directly consume Kotlin Flows. This wrapper provides a
 * callback-based API that Swift can use natively.
 *
 * ```swift
 * // Swift usage:
 * let wrapper = FlowWrapper(flow: kmpWorker.observe(taskId: "sync"))
 * wrapper.collect { state in
 *     print("State: \(state)")
 * }
 *
 * // Stop collecting:
 * wrapper.cancel()
 * ```
 *
 * @param flow The Kotlin Flow to wrap.
 */
class FlowWrapper<T : Any>(
    private val flow: Flow<T>
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Starts collecting the flow and calls [onEach] for every emitted value.
     * Call [cancel] to stop collecting.
     *
     * @param onEach Called for each emitted value.
     * @param onComplete Called when the flow completes (optional).
     * @param onError Called if the flow throws an error (optional).
     */
    fun collect(
        onEach: (T) -> Unit,
        onComplete: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        job?.cancel()
        job = scope.launch {
            try {
                flow.collect { value ->
                    onEach(value)
                }
                onComplete?.invoke()
            } catch (e: Throwable) {
                onError?.invoke(e)
            }
        }
    }

    /** Stops collecting the flow. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Returns true if the wrapper is actively collecting. */
    val isActive: Boolean get() = job?.isActive == true
}

/**
 * Convenience factory for creating a [FlowWrapper] from [TaskState] Flows.
 *
 * ```swift
 * let observer = TaskStateObserver(worker: kmpWorker, taskId: "sync")
 * observer.onStateChange { state in
 *     if state is TaskState.Success { print("Done!") }
 * }
 * ```
 */
class TaskStateObserver(
    private val flow: Flow<TaskState>
) {
    private val wrapper = FlowWrapper(flow)

    /** Observes state changes. */
    fun onStateChange(callback: (TaskState) -> Unit) {
        wrapper.collect(onEach = callback)
    }

    /** Stops observing. */
    fun stop() = wrapper.cancel()
}
