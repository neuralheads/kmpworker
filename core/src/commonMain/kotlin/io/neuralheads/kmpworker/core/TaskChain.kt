package io.neuralheads.kmpworker.core

/**
 * Defines a sequential chain of background tasks where each step must succeed
 * before the next one begins. Progress is persisted at every step boundary, so
 * if the app is killed mid-chain (e.g., iOS 30-second limit), execution resumes
 * from the correct step on next launch.
 *
 * ## Example
 * ```kotlin
 * val chain = TaskChain(
 *     id    = "user-onboarding",
 *     steps = listOf(
 *         TaskRequest(id = "fetch-profile",  type = TaskType.OneTime),
 *         TaskRequest(id = "process-data",   type = TaskType.OneTime),
 *         TaskRequest(id = "upload-results", type = TaskType.OneTime,
 *             constraints = Constraints(requiresInternet = true))
 *     )
 * )
 * kmpWorker.enqueueChain(chain)
 * kmpWorker.observeChain("user-onboarding").collect { state -> ... }
 * ```
 *
 * ## Retry within steps
 * Each step respects its own [TaskRequest.retryPolicy]. The chain only advances
 * to the next step when a step reaches [TaskState.Success]. If a step exhausts
 * its retries, the chain enters [TaskState.Failed].
 *
 * @param id Unique identifier for the chain. Used to observe and cancel the chain.
 * @param steps Ordered list of tasks to execute sequentially.
 */
data class TaskChain(
    val id: String,
    val steps: List<TaskRequest>
) {
    init {
        require(steps.isNotEmpty()) { "A TaskChain must have at least one step." }
        require(steps.all { it.id.isNotBlank() }) { "All step task IDs must be non-blank." }
    }

    /** Total number of steps in this chain. */
    val totalSteps: Int get() = steps.size

    /** Returns the step-specific task ID used internally by [TaskChainExecutor]. */
    internal fun stepId(index: Int): String = "${id}:step:${index}"

    /** Returns the [TaskRequest] for [index] with the step-specific ID injected. */
    internal fun stepRequest(index: Int): TaskRequest =
        steps[index].copy(id = stepId(index))
}
