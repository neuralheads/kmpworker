package io.neuralheads.kmpworker.android

import android.util.Log
import io.neuralheads.kmpworker.core.KmpWorkerLogger

/**
 * Convenience Android logger that bridges [KmpWorkerLogger] to `android.util.Log`.
 *
 * Install during app startup:
 * ```kotlin
 * // In Application.onCreate():
 * KmpWorkerConfig.configure {
 *     logLevel = KmpWorkerLogger.Level.DEBUG
 *     logger = KmpWorkerAndroidLogger
 * }
 * ```
 *
 * Or use the one-liner:
 * ```kotlin
 * KmpWorkerAndroidLogger.install(minLevel = KmpWorkerLogger.Level.DEBUG)
 * ```
 */
object KmpWorkerAndroidLogger : KmpWorkerLogger.Logger {

    private const val TAG = "KMPWorker"

    override fun log(level: KmpWorkerLogger.Level, message: String, throwable: Throwable?) {
        when (level) {
            KmpWorkerLogger.Level.VERBOSE -> Log.v(TAG, message, throwable)
            KmpWorkerLogger.Level.DEBUG   -> Log.d(TAG, message, throwable)
            KmpWorkerLogger.Level.INFO    -> Log.i(TAG, message, throwable)
            KmpWorkerLogger.Level.WARN    -> Log.w(TAG, message, throwable)
            KmpWorkerLogger.Level.ERROR   -> Log.e(TAG, message, throwable)
        }
    }

    /**
     * Installs this logger into [KmpWorkerConfig] in one call.
     *
     * @param minLevel Minimum level to emit. Defaults to [KmpWorkerLogger.Level.DEBUG].
     */
    fun install(minLevel: KmpWorkerLogger.Level = KmpWorkerLogger.Level.DEBUG) {
        io.neuralheads.kmpworker.core.KmpWorkerConfig.configure {
            this.logLevel = minLevel
            this.logger = KmpWorkerAndroidLogger
        }
    }
}
