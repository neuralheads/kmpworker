package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.Flow

/**
 * Fluent builder for constructing a configured [KmpWorker] instance.
 *
 * Use this when you want a single place to configure tasks, constraints,
 * logging, and retry policies before creating the worker.
 *
 * ```kotlin
 * val kmpWorker = KmpWorkerBuilder(AndroidKmpWorker(context))
 *     .configure {
 *         maxRetries = 5
 *         logLevel = KmpWorkerLogger.Level.DEBUG
 *         logger = KmpWorkerAndroidLogger
 *     }
 *     .task("sync-users") {
 *         repository.syncUsers()
 *     }
 *     .task("upload-logs") {
 *         logUploader.upload()
 *     }
 *     .build()
 * ```
 *
 * @param worker The platform-specific [KmpWorker] to configure.
 */
class KmpWorkerBuilder(private val worker: KmpWorker) {

    private val registrations = mutableListOf<Pair<String, suspend () -> Unit>>()
    private val contextRegistrations = mutableListOf<Pair<String, suspend TaskExecutionContext.() -> Unit>>()
    private var configBlock: (KmpWorkerConfig.Builder.() -> Unit)? = null

    /**
     * Applies global [KmpWorkerConfig] settings.
     */
    fun configure(block: KmpWorkerConfig.Builder.() -> Unit): KmpWorkerBuilder {
        configBlock = block
        return this
    }

    /**
     * Registers a task handler (no context).
     */
    fun task(id: String, block: suspend () -> Unit): KmpWorkerBuilder {
        registrations.add(id to block)
        return this
    }

    /**
     * Registers a task handler with [TaskExecutionContext].
     */
    fun taskWithContext(id: String, block: suspend TaskExecutionContext.() -> Unit): KmpWorkerBuilder {
        contextRegistrations.add(id to block)
        return this
    }

    /**
     * Applies all configuration and registrations, returning the configured [KmpWorker].
     */
    fun build(): KmpWorker {
        configBlock?.let { KmpWorkerConfig.configure(it) }
        registrations.forEach { (id, block) -> worker.register(id, block) }
        contextRegistrations.forEach { (id, block) -> worker.registerWithContext(id, block) }
        KmpWorkerLogger.i("KmpWorkerBuilder: built with ${registrations.size + contextRegistrations.size} registered task(s)")
        return worker
    }
}
