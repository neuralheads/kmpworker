package io.neuralheads.kmpworker.core

import kotlin.time.Duration

/**
 * Global configuration for KMPWorker behaviour.
 *
 * Configure once during app startup before any tasks are enqueued:
 * ```kotlin
 * KmpWorkerConfig.configure {
 *     maxRetries = 5
 *     logLevel = KmpWorkerLogger.Level.DEBUG
 *     logger = KmpWorkerLogger.Logger { level, msg, t ->
 *         println("[$level] $msg")
 *     }
 * }
 * ```
 */
data class KmpWorkerConfig(
    /**
     * Global maximum number of retry attempts for [RetryPolicy.Exponential].
     * Individual task policies override this if they set their own [RetryPolicy.Exponential.maxRetries].
     */
    val maxRetries: Int = 10,

    /**
     * Minimum log level to emit. Defaults to [KmpWorkerLogger.Level.INFO].
     * Set to [KmpWorkerLogger.Level.DEBUG] during development.
     */
    val logLevel: KmpWorkerLogger.Level = KmpWorkerLogger.Level.INFO,

    /**
     * Optional custom logger. If null, all log output is suppressed.
     */
    val logger: KmpWorkerLogger.Logger? = null,

    /**
     * Maximum wall-clock time a single task execution may run before it is
     * automatically cancelled and reported as [TaskState.TimedOut].
     *
     * Set to `null` (the default) to allow tasks to run indefinitely.
     * Recommended value for network tasks: `Duration.seconds(30)`.
     */
    val taskTimeout: Duration? = null
) {

    companion object {
        // Not using @Volatile — config is set once at startup from main thread.
        // Thread-safety during startup is the caller's responsibility (standard practice).
        private var current: KmpWorkerConfig = KmpWorkerConfig()

        /** Returns the current active configuration. */
        fun current(): KmpWorkerConfig = current

        /**
         * Applies a new configuration. Should be called once during app startup
         * before any background tasks are enqueued.
         */
        fun configure(block: Builder.() -> Unit) {
            val config = Builder(current).apply(block).build()
            current = config
            KmpWorkerLogger.minLevel = config.logLevel
            KmpWorkerLogger.setLogger(config.logger)
        }
    }

    class Builder(base: KmpWorkerConfig = KmpWorkerConfig()) {
        var maxRetries: Int = base.maxRetries
        var logLevel: KmpWorkerLogger.Level = base.logLevel
        var logger: KmpWorkerLogger.Logger? = base.logger
        var taskTimeout: Duration? = base.taskTimeout

        fun build() = KmpWorkerConfig(maxRetries, logLevel, logger, taskTimeout)
    }
}
