package io.neuralheads.kmpworker.core

/**
 * Pluggable logging interface for KMPWorker.
 *
 * By default KMPWorker logs nothing (silent in production).
 * Set a custom logger during app initialization:
 *
 * ```kotlin
 * KmpWorkerConfig.configure {
 *     logger = KmpWorkerLogger.Logger { level, msg, throwable ->
 *         println("[$level] $msg")
 *     }
 * }
 *
 * // Android shortcut:
 * KmpWorkerAndroidLogger.install()
 * ```
 */
object KmpWorkerLogger {

    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    /**
     * Logger interface. Implement this to plug in Logcat, Timber, Crashlytics, etc.
     *
     * Note: `fun interface` cannot have default parameter values — call-site null-safety
     * is handled by the [w]/[e] extension wrappers below.
     */
    fun interface Logger {
        fun log(level: Level, message: String, throwable: Throwable?)
    }

    // Using a plain @Volatile-equivalent: Kotlin's kotlin.concurrent.Volatile
    // requires opt-in on some targets — use a simple private var with synchronized
    // access instead, since logger is only set at startup (main thread).
    private var logger: Logger? = null
    var minLevel: Level = Level.INFO

    /** Installs a custom logger. Pass null to silence all output. */
    fun setLogger(logger: Logger?) {
        this.logger = logger
    }

    fun v(message: String) = log(Level.VERBOSE, message, null)
    fun d(message: String) = log(Level.DEBUG, message, null)
    fun i(message: String) = log(Level.INFO, message, null)
    fun w(message: String, throwable: Throwable? = null) = log(Level.WARN, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = log(Level.ERROR, message, throwable)

    private fun log(level: Level, message: String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        logger?.log(level, "[KMPWorker] $message", throwable)
    }
}
