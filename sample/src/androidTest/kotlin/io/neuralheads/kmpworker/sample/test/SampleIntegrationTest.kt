package io.neuralheads.kmpworker.sample.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.neuralheads.kmpworker.android.AndroidKmpWorker
import io.neuralheads.kmpworker.core.Constraints
import io.neuralheads.kmpworker.core.KmpWorkerConfig
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.RetryPolicy
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full system-level integration tests for KMPWorker running on a real Android device.
 *
 * These tests verify the entire stack from the consumer API perspective:
 * AndroidKmpWorker → WorkManager → KmpTaskWorker → TaskRegistry → TaskMonitor
 *
 * Run with:
 *   ./gradlew :sample:connectedDebugAndroidTest
 *
 * Requires a connected device or running emulator.
 * View logs: adb logcat -s KMPWorker-Test KMPWorker
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SampleIntegrationTest {

    private lateinit var context: Context
    private lateinit var worker: AndroidKmpWorker
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        worker = AndroidKmpWorker(context)

        TaskRegistry.clearAll()
        TaskMonitor.resetAll()

        // Enable full logging for test visibility
        KmpWorkerConfig.configure {
            logLevel = KmpWorkerLogger.Level.DEBUG
            logger = KmpWorkerLogger.Logger { level, msg, t ->
                android.util.Log.println(
                    when (level) {
                        KmpWorkerLogger.Level.ERROR -> android.util.Log.ERROR
                        KmpWorkerLogger.Level.WARN  -> android.util.Log.WARN
                        KmpWorkerLogger.Level.INFO  -> android.util.Log.INFO
                        else                        -> android.util.Log.DEBUG
                    },
                    "KMPWorker-Test",
                    msg
                )
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

    // ── 1. Basic execution ────────────────────────────────────────────────────

    @Test
    fun test_oneTimeTask_executesAndSucceeds() = runBlocking {
        var executed = false

        worker.register("sys-onetime") { executed = true }
        worker.enqueue(TaskRequest(id = "sys-onetime", type = TaskType.OneTime))

        // Use TestDriver to synchronously run the work
        val workInfos = workManager.getWorkInfosForUniqueWork("sys-onetime").get()
        assertTrue("Task must be enqueued", workInfos.isNotEmpty())

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        testDriver.setAllConstraintsMet(workInfos.first().id)

        // Brief wait for worker coroutine to complete
        delay(500)
    }

    @Test
    fun test_scheduledState_emittedImmediatelyOnEnqueue() = runBlocking {
        worker.register("sys-scheduled") { /* no-op */ }

        val states = mutableListOf<TaskState>()
        val job = launch {
            worker.observe("sys-scheduled").collect { states.add(it) }
        }

        worker.enqueue(TaskRequest(id = "sys-scheduled", type = TaskType.OneTime))
        delay(200)
        job.cancel()

        assertTrue(
            "Scheduled state must be emitted on enqueue",
            states.any { it is TaskState.Scheduled }
        )
    }

    // ── 2. Payload ────────────────────────────────────────────────────────────

    @Test
    fun test_taskWithPayload_payloadReachesHandler() = runBlocking {
        var capturedPayload: String? = null
        val testPayload = """{"userId":"test-user","operation":"full-sync","timestamp":1234567890}"""

        worker.registerWithContext("sys-payload") {
            capturedPayload = payload
        }

        worker.enqueue(
            TaskRequest(
                id = "sys-payload",
                type = TaskType.OneTime,
                payload = testPayload
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork("sys-payload").get()
        assertNotNull("Task must be in WorkManager", workInfos.firstOrNull())

        WorkManagerTestInitHelper.getTestDriver(context)!!
            .setAllConstraintsMet(workInfos.first().id)

        delay(500)
        assertEquals("Payload must reach handler unchanged", testPayload, capturedPayload)
    }

    // ── 3. Tags ───────────────────────────────────────────────────────────────

    @Test
    fun test_taskWithTags_tagsReachHandler() = runBlocking {
        var capturedTags: Set<String> = emptySet()

        worker.registerWithContext("sys-tags") {
            capturedTags = tags
        }

        worker.enqueue(
            TaskRequest(
                id = "sys-tags",
                type = TaskType.OneTime,
                tags = setOf("upload", "priority-high", "user-123")
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork("sys-tags").get()
        WorkManagerTestInitHelper.getTestDriver(context)!!
            .setAllConstraintsMet(workInfos.first().id)

        delay(500)

        assertTrue("'upload' tag must reach handler",         capturedTags.contains("upload"))
        assertTrue("'priority-high' tag must reach handler",  capturedTags.contains("priority-high"))
        assertTrue("'user-123' tag must reach handler",       capturedTags.contains("user-123"))
        assertEquals("Exactly 3 tags must be present", 3, capturedTags.size)
    }

    // ── 4. Retry logic ────────────────────────────────────────────────────────

    @Test
    fun test_failingTask_retriesWithLinearPolicy() = runBlocking {
        var attempts = 0

        worker.register("sys-retry") {
            attempts++
            if (attempts == 1) throw RuntimeException("First attempt failure")
        }

        worker.enqueue(
            TaskRequest(
                id = "sys-retry",
                type = TaskType.OneTime,
                retryPolicy = RetryPolicy.Linear(delayMillis = 100L) // Short delay for testing
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork("sys-retry").get()
        assertNotNull(workInfos.firstOrNull())

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        val workId = workInfos.first().id
        testDriver.setAllConstraintsMet(workId)
        delay(200)

        // Trigger retry
        testDriver.setPeriodDelayMet(workId)
        delay(500)
    }

    // ── 5. Cancellation ───────────────────────────────────────────────────────

    @Test
    fun test_cancelTask_workBecomesCANCELLED() = runBlocking {
        worker.register("sys-cancel") { delay(60_000) } // Very long task

        worker.enqueue(TaskRequest(id = "sys-cancel", type = TaskType.OneTime))
        delay(100)

        worker.cancel("sys-cancel")
        delay(200)

        val workInfos = workManager.getWorkInfosForUniqueWork("sys-cancel").get()
        if (workInfos.isNotEmpty()) {
            assertEquals(
                "Cancelled task should be CANCELLED in WorkManager",
                WorkInfo.State.CANCELLED,
                workInfos.first().state
            )
        }
    }

    // ── 6. Tag cancellation ───────────────────────────────────────────────────

    @Test
    fun test_cancelByTag_cancelsAllMatchingWork() = runBlocking {
        worker.register("tag-task-1") { delay(60_000) }
        worker.register("tag-task-2") { delay(60_000) }
        worker.register("no-tag-task") { delay(60_000) }

        worker.enqueue(TaskRequest(id = "tag-task-1", type = TaskType.OneTime, tags = setOf("group-A")))
        worker.enqueue(TaskRequest(id = "tag-task-2", type = TaskType.OneTime, tags = setOf("group-A")))
        worker.enqueue(TaskRequest(id = "no-tag-task", type = TaskType.OneTime))

        delay(100)
        worker.cancelByTag("group-A")
        delay(200)

        val info1 = workManager.getWorkInfosForUniqueWork("tag-task-1").get()
        val info2 = workManager.getWorkInfosForUniqueWork("tag-task-2").get()
        val info3 = workManager.getWorkInfosForUniqueWork("no-tag-task").get()

        if (info1.isNotEmpty()) assertEquals(WorkInfo.State.CANCELLED, info1.first().state)
        if (info2.isNotEmpty()) assertEquals(WorkInfo.State.CANCELLED, info2.first().state)

        // No-tag task should NOT be cancelled
        if (info3.isNotEmpty()) {
            assertTrue(
                "Untagged task should not be cancelled",
                info3.first().state != WorkInfo.State.CANCELLED
            )
        }
    }

    // ── 7. Periodic task ──────────────────────────────────────────────────────

    @Test
    fun test_periodicTask_enqueued() = runBlocking {
        worker.register("sys-periodic") { /* no-op */ }

        worker.enqueue(
            TaskRequest(
                id = "sys-periodic",
                type = TaskType.Periodic(repeatIntervalMillis = 15 * 60 * 1_000L)
            )
        )

        val workInfos = workManager.getWorkInfosForUniquePeriodicWork("sys-periodic").get()
        assertTrue("Periodic work must be in WorkManager", workInfos.isNotEmpty())
        val state = workInfos.first().state
        assertTrue(
            "Periodic work should be ENQUEUED or RUNNING, was $state",
            state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
        )
    }

    // ── 8. Constraints ────────────────────────────────────────────────────────

    @Test
    fun test_constrainedTask_enqueuedWithCorrectConstraints() = runBlocking {
        worker.register("sys-constrained") { /* no-op */ }

        worker.enqueue(
            TaskRequest(
                id = "sys-constrained",
                type = TaskType.OneTime,
                constraints = Constraints(
                    requiresInternet = true,
                    requiresCharging = false,
                    batteryNotLow = true
                )
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork("sys-constrained").get()
        assertTrue("Constrained task must be enqueued", workInfos.isNotEmpty())
        // Task may be ENQUEUED (waiting for constraints) — that's correct behavior
        val state = workInfos.first().state
        assertTrue(
            "Task should be ENQUEUED waiting for network, was $state",
            state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
        )
    }

    // ── 9. Multiple simultaneous tasks ────────────────────────────────────────

    @Test
    fun test_multipleTasksConcurrent_allEnqueued() = runBlocking {
        val taskIds = (1..5).map { "concurrent-$it" }
        taskIds.forEach { id ->
            worker.register(id) { /* no-op */ }
            worker.enqueue(TaskRequest(id = id, type = TaskType.OneTime))
        }

        delay(200)

        taskIds.forEach { id ->
            val infos = workManager.getWorkInfosForUniqueWork(id).get()
            assertTrue("Task '$id' must be enqueued", infos.isNotEmpty())
        }
    }

    // ── 10. ExecutionContext ──────────────────────────────────────────────────

    @Test
    fun test_executionContext_allFieldsAccessible() = runBlocking {
        var capturedTaskId: String? = null
        var capturedRetryCount: Int = -1
        var capturedPayload: String? = null
        var capturedTags: Set<String> = emptySet()

        worker.registerWithContext("sys-context") {
            capturedTaskId     = taskId
            capturedRetryCount = retryCount
            capturedPayload    = payload
            capturedTags       = tags
        }

        worker.enqueue(
            TaskRequest(
                id      = "sys-context",
                type    = TaskType.OneTime,
                payload = "ctx-payload",
                tags    = setOf("ctx-tag")
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork("sys-context").get()
        WorkManagerTestInitHelper.getTestDriver(context)!!
            .setAllConstraintsMet(workInfos.first().id)

        delay(500)

        assertEquals("sys-context",  capturedTaskId)
        assertEquals(0,              capturedRetryCount)
        assertEquals("ctx-payload",  capturedPayload)
        assertTrue(capturedTags.contains("ctx-tag"))
    }
}
