package io.neuralheads.kmpworker.core

/**
 * Thread-safe global registry mapping task IDs to their suspend handler functions.
 *
 * Tasks must be registered before being enqueued. KMPWorker intentionally avoids
 * reflection and annotation processing in favour of explicit registration.
 *
 * **Threading contract**: `register()` and `unregister()` should be called from
 * the main thread during app initialization, before any background tasks start.
 * This matches the standard pattern used by WorkManager and BGTaskScheduler —
 * workers are always registered at startup, not mid-execution.
 *
 * Usage:
 * ```kotlin
 * TaskRegistry.register("sync-users") { ctx ->
 *     println("Running task ${ctx.taskId}, attempt ${ctx.retryCount}")
 *     repository.syncUsers()
 * }
 * TaskRegistry.execute("sync-users", TaskExecutionContext("sync-users", retryCount = 0))
 * ```
 */
object TaskRegistry {

    // Plain mutableMapOf is correct here — registration happens at app startup
    // (main thread, before any concurrent background access). This avoids a
    // JVM-only ConcurrentHashMap dependency in commonMain.
    private val handlers = mutableMapOf<String, suspend TaskExecutionContext.() -> Unit>()

    /**
     * Registers a suspend handler for the given task ID.
     *
     * The handler receives a [TaskExecutionContext] with the task ID, retry count,
     * and any payload attached to the [TaskRequest]. Unused if you don't need it:
     *
     * ```kotlin
     * // Simple — context available but ignored
     * register("sync") { repository.sync() }
     *
     * // With context
     * register("sync") { ctx -> println("attempt ${ctx.retryCount}") }
     * ```
     *
     * Overwrites any existing handler for the same ID.
     */
    fun register(id: String, block: suspend TaskExecutionContext.() -> Unit) {
        handlers[id] = block
        KmpWorkerLogger.d("TaskRegistry: registered '$id'")
    }

    /**
     * Executes the registered handler for the given task ID with the provided context.
     *
     * @throws IllegalStateException if no handler is registered for [id].
     */
    suspend fun execute(id: String, context: TaskExecutionContext = TaskExecutionContext(id)) {
        val handler = handlers[id]
            ?: throw IllegalStateException(
                "No handler registered for task '$id'. " +
                "Call KmpWorker.register(\"$id\") { ... } before enqueue()."
            )
        KmpWorkerLogger.d("TaskRegistry: executing '$id' (attempt ${context.retryCount + 1})")
        handler.invoke(context)
    }

    /** Returns true if a handler is registered for the given task ID. */
    fun isRegistered(id: String): Boolean = handlers.containsKey(id)

    /** Returns all currently registered task IDs. Snapshot — safe to iterate. */
    fun registeredIds(): Set<String> = handlers.keys.toSet()

    /** Returns the handler for [id], or null if not registered. Used by [TaskChainExecutor]. */
    fun handlerFor(id: String): (suspend TaskExecutionContext.() -> Unit)? = handlers[id]

    /** Removes the handler for the given task ID. */
    fun unregister(id: String) {
        handlers.remove(id)
        KmpWorkerLogger.d("TaskRegistry: unregistered '$id'")
    }

    /** Clears all registered handlers. For use in tests only. */
    fun clearAll() {
        handlers.clear()
    }
}
