package io.neuralheads.kmpworker.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.neuralheads.kmpworker.core.KmpWorkerConfig
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.take
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive unit tests for [KmpTaskWorker] using Robolectric.
 *
 * Runs on JVM with simulated Android — no device needed.
 * Covers: successful execution, failure, retry policies, payload/tag passing, state emissions.
 *
 * Run with: ./gradlew :android:testDebugUnitTest
 *
 * ## Var capture note
 * Local `var` captured by lambdas in Robolectric coroutine tests can be unreliable
 * due to the Kotlin Ref wrapper and Robolectric's coroutine dispatcher interactions.
 * We use single-element arrays as a workaround — the array reference itself is stable,
 * so mutation inside the handler is always visible to the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KmpTaskWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        TaskRegistry.clearAll()
        TaskMonitor.resetAll()
        KmpWorkerConfig.configure {
            logLevel = KmpWorkerLogger.Level.DEBUG
            logger = KmpWorkerLogger.Logger { level, msg, t ->
                println("[${level.name}] $msg")
                t?.printStackTrace()
            }
        }
    }

    @After
    fun tearDown() {
        TaskRegistry.clearAll()
        TaskMonitor.resetAll()
        KmpWorkerConfig.configure { logger = null }
    }

    // ── Successful execution ─────────────────────────────────────────────────

    @Test
    fun `successful task returns SUCCESS result`() = runTest {
        TaskRegistry.register("task-ok") { /* no-op */ }
        val result = buildWorker("task-ok").doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `successful task emits Running then Success states`() = runTest {
        val states = mutableListOf<TaskState>()
        TaskRegistry.register("task-states") { /* no-op */ }

        // UnconfinedTestDispatcher runs the collector eagerly — it subscribes
        // to the SharedFlow immediately and processes emissions as they come in.
        val stateJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            TaskMonitor.observe("task-states").collect { states.add(it) }
        }

        buildWorker("task-states").doWork()
        advanceUntilIdle()

        stateJob.cancel()

        assertTrue("Running must be emitted", states.any { it is TaskState.Running })
        assertTrue("Success must be emitted", states.any { it is TaskState.Success })
    }

    // ── Missing inputs ────────────────────────────────────────────────────────

    @Test
    fun `missing taskId returns FAILURE immediately`() = runTest {
        val worker = TestListenableWorkerBuilder<KmpTaskWorker>(context)
            .setInputData(workDataOf(KmpTaskWorker.KEY_RETRY_POLICY_TYPE to KmpTaskWorker.POLICY_NONE))
            .build()
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun `no registered handler returns FAILURE`() = runTest {
        assertEquals(ListenableWorker.Result.failure(), buildWorker("unregistered").doWork())
    }

    // ── Failure and retry ─────────────────────────────────────────────────────

    @Test
    fun `failing task with NONE policy returns FAILURE`() = runTest {
        TaskRegistry.register("task-fail-none") { throw RuntimeException("fail") }
        assertEquals(
            ListenableWorker.Result.failure(),
            buildWorker("task-fail-none", policyType = KmpTaskWorker.POLICY_NONE).doWork()
        )
    }

    @Test
    fun `failing task with LINEAR policy returns RETRY on first attempt`() = runTest {
        TaskRegistry.register("task-fail-linear") { throw RuntimeException("fail") }
        assertEquals(
            ListenableWorker.Result.retry(),
            buildWorker("task-fail-linear", policyType = KmpTaskWorker.POLICY_LINEAR,
                policyDelay = 1_000L, runAttemptCount = 0).doWork()
        )
    }

    @Test
    fun `failing task with EXPONENTIAL policy returns RETRY before maxRetries`() = runTest {
        TaskRegistry.register("task-exp") { throw RuntimeException("fail") }
        assertEquals(
            ListenableWorker.Result.retry(),
            buildWorker("task-exp", policyType = KmpTaskWorker.POLICY_EXPONENTIAL,
                policyDelay = 1_000L, policyMax = 5, runAttemptCount = 2).doWork()
        )
    }

    @Test
    fun `exhausted retries returns FAILURE not RETRY`() = runTest {
        TaskRegistry.register("task-exhausted") { throw RuntimeException("fail") }
        assertEquals(
            ListenableWorker.Result.failure(),
            buildWorker("task-exhausted", policyType = KmpTaskWorker.POLICY_EXPONENTIAL,
                policyDelay = 1_000L, policyMax = 3, runAttemptCount = 3).doWork()
        )
    }

    @Test
    fun `failing task emits Failed state with willRetry=true for linear policy`() = runTest {
        val capturedState = arrayOfNulls<TaskState.Failed>(1)
        TaskRegistry.register("task-fail-state") { throw RuntimeException("fail") }

        // Subscribe eagerly so emissions aren't missed
        val stateJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            TaskMonitor.observe("task-fail-state").collect { state ->
                if (state is TaskState.Failed) capturedState[0] = state
            }
        }

        buildWorker("task-fail-state", policyType = KmpTaskWorker.POLICY_LINEAR,
            policyDelay = 1_000L, runAttemptCount = 0).doWork()
        advanceUntilIdle()

        stateJob.cancel()

        assertNotNull("Failed state should be emitted", capturedState[0])
        assertTrue("willRetry should be true for linear policy", capturedState[0]!!.willRetry)
    }

    // ── Payload ───────────────────────────────────────────────────────────────

    @Test
    fun `payload is passed correctly to TaskExecutionContext`() = runTest {
        // Array trick: single-element array avoids Kotlin Ref/coroutine closure capture issues
        val receivedPayload = arrayOfNulls<String>(1)
        TaskRegistry.register("task-payload") { receivedPayload[0] = payload }

        TestListenableWorkerBuilder<KmpTaskWorker>(context)
            .setInputData(workDataOf(
                KmpTaskWorker.KEY_TASK_ID to "task-payload",
                KmpTaskWorker.KEY_PAYLOAD to """{"user":"alice"}""",
                KmpTaskWorker.KEY_RETRY_POLICY_TYPE to KmpTaskWorker.POLICY_NONE
            ))
            .build()
            .doWork()

        assertEquals("""{"user":"alice"}""", receivedPayload[0])
    }

    // ── Tags ──────────────────────────────────────────────────────────────────

    @Test
    fun `tags are parsed and passed to TaskExecutionContext`() = runTest {
        val receivedTags = arrayOfNulls<Set<String>>(1)
        TaskRegistry.register("task-tags") { receivedTags[0] = tags }

        TestListenableWorkerBuilder<KmpTaskWorker>(context)
            .setInputData(workDataOf(
                KmpTaskWorker.KEY_TASK_ID to "task-tags",
                KmpTaskWorker.KEY_TAGS to "upload,critical,user-data",
                KmpTaskWorker.KEY_RETRY_POLICY_TYPE to KmpTaskWorker.POLICY_NONE
            ))
            .build()
            .doWork()

        assertNotNull("tags should not be null after handler runs", receivedTags[0])
        assertTrue("upload",    receivedTags[0]!!.contains("upload"))
        assertTrue("critical",  receivedTags[0]!!.contains("critical"))
        assertTrue("user-data", receivedTags[0]!!.contains("user-data"))
        assertEquals(3, receivedTags[0]!!.size)
    }

    @Test
    fun `empty tags string results in empty set`() = runTest {
        val receivedTags = arrayOfNulls<Set<String>>(1)
        TaskRegistry.register("task-no-tags") { receivedTags[0] = tags }

        TestListenableWorkerBuilder<KmpTaskWorker>(context)
            .setInputData(workDataOf(
                KmpTaskWorker.KEY_TASK_ID to "task-no-tags",
                KmpTaskWorker.KEY_TAGS to "",
                KmpTaskWorker.KEY_RETRY_POLICY_TYPE to KmpTaskWorker.POLICY_NONE
            ))
            .build()
            .doWork()

        assertNotNull("tags holder set by handler", receivedTags[0])
        assertTrue("Tags should be empty for blank input", receivedTags[0]!!.isEmpty())
    }

    // ── Retry count ───────────────────────────────────────────────────────────

    @Test
    fun `retryCount is accessible in TaskExecutionContext`() = runTest {
        val receivedRetryCount = IntArray(1) { -1 }
        // Use LINEAR policy so handler actually runs (POLICY_NONE + runAttemptCount>0 = exhausted branch)
        TaskRegistry.register("task-retry-ctx") { receivedRetryCount[0] = retryCount }

        TestListenableWorkerBuilder<KmpTaskWorker>(context)
            .setInputData(workDataOf(
                KmpTaskWorker.KEY_TASK_ID to "task-retry-ctx",
                KmpTaskWorker.KEY_RETRY_POLICY_TYPE to KmpTaskWorker.POLICY_LINEAR,
                KmpTaskWorker.KEY_RETRY_POLICY_DELAY to 1_000L
            ))
            .setRunAttemptCount(1)
            .build()
            .doWork()

        assertEquals("retryCount should match runAttemptCount", 1, receivedRetryCount[0])
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildWorker(
        taskId: String,
        policyType: String = KmpTaskWorker.POLICY_NONE,
        policyDelay: Long = 0L,
        policyMax: Int = 0,
        runAttemptCount: Int = 0
    ): KmpTaskWorker {
        return TestListenableWorkerBuilder<KmpTaskWorker>(context)
            .setInputData(workDataOf(
                KmpTaskWorker.KEY_TASK_ID to taskId,
                KmpTaskWorker.KEY_RETRY_POLICY_TYPE to policyType,
                KmpTaskWorker.KEY_RETRY_POLICY_DELAY to policyDelay,
                KmpTaskWorker.KEY_RETRY_POLICY_MAX to policyMax
            ))
            .setRunAttemptCount(runAttemptCount)
            .build()
    }
}
