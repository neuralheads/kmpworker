package io.neuralheads.kmpworker.android.instrumented

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [KmpTaskWorker] running on a real Android process.
 *
 * Requires a connected device or emulator.
 * Run with: ./gradlew :android:connectedDebugAndroidTest
 *
 * These complement the Robolectric tests by verifying behavior in the
 * actual Android runtime (Dalvik/ART), not a JVM simulation.
 */
@RunWith(AndroidJUnit4::class)
class KmpTaskWorkerInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        TaskRegistry.clearAll()
        TaskMonitor.resetAll()
    }

    @After
    fun tearDown() {
        TaskRegistry.clearAll()
        TaskMonitor.resetAll()
    }

    @Test
    fun workerSucceeds_whenHandlerRegistered() = runBlocking {
        TaskRegistry.register("inst-task-ok") { /* no-op */ }

        val worker = TestListenableWorkerBuilder<io.neuralheads.kmpworker.android.KmpTaskWorker>(context)
            .setInputData(workDataOf(
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_TASK_ID to "inst-task-ok",
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_RETRY_POLICY_TYPE to
                    io.neuralheads.kmpworker.android.KmpTaskWorker.POLICY_NONE
            ))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun workerFails_whenNoHandlerRegistered() = runBlocking {
        // No register() call
        val worker = TestListenableWorkerBuilder<io.neuralheads.kmpworker.android.KmpTaskWorker>(context)
            .setInputData(workDataOf(
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_TASK_ID to "inst-no-handler",
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_RETRY_POLICY_TYPE to
                    io.neuralheads.kmpworker.android.KmpTaskWorker.POLICY_NONE
            ))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun workerRetries_onFailureWithLinearPolicy() = runBlocking {
        TaskRegistry.register("inst-retry") {
            throw RuntimeException("Instrumented failure")
        }

        val worker = TestListenableWorkerBuilder<io.neuralheads.kmpworker.android.KmpTaskWorker>(context)
            .setInputData(workDataOf(
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_TASK_ID to "inst-retry",
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_RETRY_POLICY_TYPE to
                    io.neuralheads.kmpworker.android.KmpTaskWorker.POLICY_LINEAR,
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_RETRY_POLICY_DELAY to 1_000L
            ))
            .setRunAttemptCount(0)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun payload_reachesHandler_onRealRuntime() = runBlocking {
        var capturedPayload: String? = null

        // TaskRegistry.register takes suspend TaskExecutionContext.() -> Unit
        // so payload/tags/taskId are accessible as `this.payload` etc.
        TaskRegistry.register("inst-payload") {
            capturedPayload = payload  // `this` is TaskExecutionContext
        }

        val worker = TestListenableWorkerBuilder<io.neuralheads.kmpworker.android.KmpTaskWorker>(context)
            .setInputData(workDataOf(
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_TASK_ID to "inst-payload",
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_PAYLOAD to "real-payload-data",
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_RETRY_POLICY_TYPE to
                    io.neuralheads.kmpworker.android.KmpTaskWorker.POLICY_NONE
            ))
            .build()

        worker.doWork()
        assertEquals("real-payload-data", capturedPayload)
    }

    @Test
    fun successState_emittedToTaskMonitor() = runBlocking {
        TaskRegistry.register("inst-monitor") { /* no-op */ }

        var successEmitted = false
        val job = kotlinx.coroutines.GlobalScope.launch {
            TaskMonitor.observe("inst-monitor").collect { state ->
                if (state is TaskState.Success) successEmitted = true
            }
        }

        TestListenableWorkerBuilder<io.neuralheads.kmpworker.android.KmpTaskWorker>(context)
            .setInputData(workDataOf(
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_TASK_ID to "inst-monitor",
                io.neuralheads.kmpworker.android.KmpTaskWorker.KEY_RETRY_POLICY_TYPE to
                    io.neuralheads.kmpworker.android.KmpTaskWorker.POLICY_NONE
            ))
            .build()
            .doWork()

        job.cancel()
        assertTrue("Success state must be emitted to TaskMonitor on real device", successEmitted)
    }
}
