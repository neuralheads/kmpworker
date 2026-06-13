package io.neuralheads.kmpworker.core

/**
 * Fluent DSL builder for constructing [TaskChain]s.
 *
 * ```kotlin
 * kmpWorker.chain("user-onboarding") {
 *     beginWith("fetch-profile")
 *     then("process-data") {
 *         retryPolicy = RetryPolicy.Exponential(5_000, 3)
 *     }
 *     then("upload-results") {
 *         constraints = Constraints(requiresInternet = true)
 *         priority = TaskPriority.HIGH
 *     }
 * }
 * ```
 */
class TaskChainBuilder(private val chainId: String) {

    private val steps = mutableListOf<TaskRequest>()

    /** Adds the first step of the chain. */
    fun beginWith(taskId: String, configure: TaskRequestBuilder.() -> Unit = {}) {
        steps.add(TaskRequestBuilder(taskId).apply(configure).build())
    }

    /** Adds a subsequent step that runs after all previous steps succeed. */
    fun then(taskId: String, configure: TaskRequestBuilder.() -> Unit = {}) {
        steps.add(TaskRequestBuilder(taskId).apply(configure).build())
    }

    internal fun build(): TaskChain = TaskChain(id = chainId, steps = steps)
}

/**
 * Builder for configuring individual [TaskRequest]s within a chain DSL.
 */
class TaskRequestBuilder(private val taskId: String) {
    var constraints: Constraints = Constraints()
    var retryPolicy: RetryPolicy = RetryPolicy.None
    var priority: TaskPriority = TaskPriority.NORMAL
    var tags: Set<String> = emptySet()
    var payload: String? = null

    internal fun build(): TaskRequest = TaskRequest(
        id = taskId,
        type = TaskType.OneTime,
        constraints = constraints,
        retryPolicy = retryPolicy,
        priority = priority,
        tags = tags,
        payload = payload
    )
}
