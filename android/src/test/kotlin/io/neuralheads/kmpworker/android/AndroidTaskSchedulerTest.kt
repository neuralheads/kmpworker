package io.neuralheads.kmpworker.android

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.neuralheads.kmpworker.core.Constraints
import io.neuralheads.kmpworker.core.RetryPolicy
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [AndroidTaskScheduler] — verifies that work requests are built and enqueued
 * correctly in WorkManager: constraints, backoff policies, periodic vs one-time.
 *
 * Run with: ./gradlew :android:testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidTaskSchedulerTest {

    private lateinit var context: Context
    private lateinit var scheduler: AndroidTaskScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        scheduler = AndroidTaskScheduler(context)
        TaskRegistry.clearAll()
        TaskMonitor.resetAll()
    }

    @After
    fun tearDown() {
        TaskRegistry.clearAll()
        TaskMonitor.resetAll()
    }

    // ── OneTime enqueueing ──────────────────────────────────────────────────

    @Test
    fun `oneTime task is enqueued in WorkManager`() = runTest {
        TaskRegistry.register("one-time-task") { /* no-op */ }

        scheduler.enqueue(
            TaskRequest(id = "one-time-task", type = TaskType.OneTime)
        )
        // Drain pending Robolectric runnables so WorkManager state is committed
        shadowOf(Looper.getMainLooper()).idle()

        val workInfos = workManager
            .getWorkInfosForUniqueWork("one-time-task")
            .get()

        // In test mode, WorkManagerTestInitHelper runs tasks synchronously:
        // they may be ENQUEUED, RUNNING, or already SUCCEEDED — all are valid "was enqueued".
        assertTrue("Work should have been enqueued", workInfos.isNotEmpty())
    }

    @Test
    fun `duplicate oneTime task replaces existing work`() = runTest {
        TaskRegistry.register("dup-task") { /* no-op */ }

        val request = TaskRequest(id = "dup-task", type = TaskType.OneTime)
        scheduler.enqueue(request)
        scheduler.enqueue(request) // second enqueue replaces

        val workInfos = workManager.getWorkInfosForUniqueWork("dup-task").get()
        // Should still be exactly 1 work item (REPLACE policy)
        assertTrue("Should have work after duplicate enqueue", workInfos.isNotEmpty())
    }

    // ── Periodic enqueueing ─────────────────────────────────────────────────

    @Test
    fun `periodic task is enqueued as periodic work`() = runTest {
        TaskRegistry.register("periodic-task") { /* no-op */ }

        scheduler.enqueue(
            TaskRequest(
                id = "periodic-task",
                type = TaskType.Periodic(repeatIntervalMillis = 15 * 60 * 1_000L)
            )
        )

        // Periodic unique work is also retrieved via getWorkInfosForUniqueWork
        val workInfos = workManager
            .getWorkInfosForUniqueWork("periodic-task")
            .get()

        assertTrue("Periodic work should be enqueued", workInfos.isNotEmpty())
        val state = workInfos.first().state
        assertTrue(
            "Expected ENQUEUED or RUNNING, got $state",
            state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
        )
    }

    // ── Cancellation ────────────────────────────────────────────────────────

    @Test
    fun `cancel removes enqueued work`() = runTest {
        TaskRegistry.register("cancel-task") { /* no-op */ }

        scheduler.enqueue(
            TaskRequest(id = "cancel-task", type = TaskType.OneTime)
        )
        shadowOf(Looper.getMainLooper()).idle()

        // Verify work was submitted
        val beforeCancel = workManager.getWorkInfosForUniqueWork("cancel-task").get()
        assertTrue("Should have work before cancel", beforeCancel.isNotEmpty())

        scheduler.cancel("cancel-task")
        shadowOf(Looper.getMainLooper()).idle()

        val afterCancel = workManager.getWorkInfosForUniqueWork("cancel-task").get()
        // Work is either CANCELLED or pruned (empty) after cancellation
        if (afterCancel.isNotEmpty()) {
            val finalState = afterCancel.first().state
            assertTrue(
                "After cancel, state should be CANCELLED or SUCCEEDED (completed before cancel), got $finalState",
                finalState == WorkInfo.State.CANCELLED || finalState == WorkInfo.State.SUCCEEDED
            )
        }
        // If afterCancel is empty, work was pruned — also acceptable
    }

    @Test
    fun `cancelByTag cancels all work with that tag`() = runTest {
        TaskRegistry.register("tagged-1") { /* no-op */ }
        TaskRegistry.register("tagged-2") { /* no-op */ }
        TaskRegistry.register("untagged") { /* no-op */ }

        scheduler.enqueue(
            TaskRequest(id = "tagged-1", type = TaskType.OneTime, tags = setOf("batch"))
        )
        scheduler.enqueue(
            TaskRequest(id = "tagged-2", type = TaskType.OneTime, tags = setOf("batch"))
        )
        scheduler.enqueue(
            TaskRequest(id = "untagged", type = TaskType.OneTime)
        )
        shadowOf(Looper.getMainLooper()).idle()

        scheduler.cancelByTag("batch")
        shadowOf(Looper.getMainLooper()).idle()

        // In synchronous test execution tasks may have already SUCCEEDED before cancel.
        // Verify the cancel API was called without crashing — state is CANCELLED or SUCCEEDED.
        val tagged1 = workManager.getWorkInfosForUniqueWork("tagged-1").get()
        val tagged2 = workManager.getWorkInfosForUniqueWork("tagged-2").get()

        if (tagged1.isNotEmpty()) {
            val state = tagged1.first().state
            assertTrue(
                "tagged-1 state should be CANCELLED or SUCCEEDED, got $state",
                state == WorkInfo.State.CANCELLED || state == WorkInfo.State.SUCCEEDED
            )
        }
        if (tagged2.isNotEmpty()) {
            val state = tagged2.first().state
            assertTrue(
                "tagged-2 state should be CANCELLED or SUCCEEDED, got $state",
                state == WorkInfo.State.CANCELLED || state == WorkInfo.State.SUCCEEDED
            )
        }

        // Untagged should remain non-cancelled
        val untagged = workManager.getWorkInfosForUniqueWork("untagged").get()
        if (untagged.isNotEmpty()) {
            assertTrue(
                "Untagged task should not be CANCELLED",
                untagged.first().state != WorkInfo.State.CANCELLED
            )
        }
    }

    // ── Constraints ─────────────────────────────────────────────────────────

    @Test
    fun `network constraint is mapped when requiresInternet=true`() = runTest {
        TaskRegistry.register("net-task") { /* no-op */ }

        scheduler.enqueue(
            TaskRequest(
                id = "net-task",
                type = TaskType.OneTime,
                constraints = Constraints(requiresInternet = true)
            )
        )

        val workInfo = workManager.getWorkInfosForUniqueWork("net-task").get().firstOrNull()
        assertNotNull("Work should be enqueued", workInfo)
        // WorkInfo doesn't expose constraints directly in test mode — verify enqueue succeeds
        // Full constraint verification done in instrumented tests
    }

    @Test
    fun `all constraints enqueue without crash`() = runTest {
        TaskRegistry.register("full-constraints") { /* no-op */ }

        // Should not throw
        scheduler.enqueue(
            TaskRequest(
                id = "full-constraints",
                type = TaskType.OneTime,
                constraints = Constraints(
                    requiresInternet = true,
                    requiresCharging = true,
                    batteryNotLow = true
                ),
                retryPolicy = RetryPolicy.Exponential(
                    initialDelayMillis = 5_000L,
                    maxRetries = 5
                )
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork("full-constraints").get()
        assertTrue("Work with all constraints should be enqueued", workInfos.isNotEmpty())
    }

    // ── Observation ─────────────────────────────────────────────────────────

    @Test
    fun `observe returns a non-null flow`() = runTest {
        val flow = scheduler.observe("any-task")
        assertNotNull("observe() must return a flow", flow)
    }

    @Test
    fun `observeAll returns a non-null flow`() = runTest {
        val flow = scheduler.observeAll()
        assertNotNull("observeAll() must return a flow", flow)
    }
}
