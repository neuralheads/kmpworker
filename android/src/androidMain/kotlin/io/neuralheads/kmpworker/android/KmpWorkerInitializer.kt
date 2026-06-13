package io.neuralheads.kmpworker.android

import android.content.Context
import androidx.startup.Initializer
import io.neuralheads.kmpworker.core.KmpWorkerLogger

/**
 * Jetpack App Startup initializer for KMPWorker.
 *
 * Automatically triggered when the app starts — no manual setup required
 * in `Application.onCreate()`. The library registers with WorkManager and
 * validates the runtime environment silently.
 *
 * If you want to opt out of auto-initialization, add the following to your
 * `AndroidManifest.xml`:
 *
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="io.neuralheads.kmpworker.android.KmpWorkerInitializer"
 *         tools:node="remove" />
 * </provider>
 * ```
 *
 * Then initialize manually:
 * ```kotlin
 * AppInitializer.getInstance(context)
 *     .initializeComponent(KmpWorkerInitializer::class.java)
 * ```
 */
class KmpWorkerInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // Eagerly initialize WorkManager to avoid first-use cold start latency.
        // This also validates that the WorkManager configuration is correct at startup
        // rather than at first enqueue().
        androidx.work.WorkManager.getInstance(context)
        KmpWorkerLogger.d("KMPWorker initialized via App Startup")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
