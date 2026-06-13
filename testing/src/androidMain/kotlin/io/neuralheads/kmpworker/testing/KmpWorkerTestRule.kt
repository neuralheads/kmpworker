package io.neuralheads.kmpworker.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit4 test rule that sets up a test environment for KMPWorker on Android.
 *
 * Initializes WorkManager in test mode and provides a [FakeKmpWorker]
 * for assertions. Automatically resets state between tests.
 *
 * ```kotlin
 * class MyWorkerTest {
 *     @get:Rule
 *     val kmpWorkerRule = KmpWorkerTestRule()
 *
 *     @Test
 *     fun testSync() = runTest {
 *         kmpWorkerRule.worker.register("sync") { repo.sync() }
 *         kmpWorkerRule.worker.enqueue(TaskRequest("sync", TaskType.OneTime))
 *         assertEquals(TaskState.Success, kmpWorkerRule.worker.lastStateFor("sync"))
 *     }
 * }
 * ```
 */
class KmpWorkerTestRule : TestRule {

    /** The fake worker for testing — register handlers and assert state. */
    val worker = FakeKmpWorker()

    /** The fake network monitor — control online/offline state. */
    val networkMonitor = FakeNetworkMonitor(initiallyOnline = true)

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Initialize WorkManager in test mode
                try {
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    val config = Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .build()
                    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
                } catch (_: Exception) {
                    // WorkManager may already be initialized — safe to ignore
                }

                try {
                    base.evaluate()
                } finally {
                    worker.reset()
                    networkMonitor.setOnline(true)
                }
            }
        }
    }
}
