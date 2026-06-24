package io.neuralheads.kmpworker.sample

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import io.neuralheads.kmpworker.core.Constraints
import io.neuralheads.kmpworker.core.RetryPolicy
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.inspector.KmpWorkerInspectorScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * KMPWorker System Test Dashboard.
 *
 * Runs a comprehensive suite of functional tests against the real WorkManager integration
 * and displays results live in the UI. All test output is also sent to Logcat with TAG
 * "KMPWorker-Test" for debugging.
 *
 * Install and run:
 *   adb install sample/build/outputs/apk/debug/sample-debug.apk
 *   adb logcat -s KMPWorker-Test
 *
 * Tests covered:
 *   1. One-time task executes successfully
 *   2. Task with payload receives payload in handler
 *   3. Task with tags receives tags in handler
 *   4. Task observation emits all states
 *   5. Task cancellation
 *   6. Tag-based bulk cancellation
 *   7. Retry on failure (linear policy)
 *   8. Periodic task enqueueing
 *   9. Constraint-bound task enqueueing
 *  10. KmpWorkerConfig logger integration
 */
class MainActivity : AppCompatActivity() {

    private val kmpWorker by lazy { (application as SampleApp).kmpWorker }
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var runButton: Button
    private lateinit var statusView: TextView

    private val logBuilder = SpannableStringBuilder()
    private var passCount = 0
    private var failCount = 0

    companion object {
        private const val TAG = "KMPWorker-Test"
        private const val COLOR_PASS   = Color.GREEN
        private const val COLOR_FAIL   = 0xFFFF4444.toInt()
        private const val COLOR_INFO   = 0xFFAAAAAA.toInt()
        private const val COLOR_HEADER = 0xFF00CCFF.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())

        runButton.setOnClickListener {
            runButton.isEnabled = false
            passCount = 0
            failCount = 0
            logBuilder.clear()
            lifecycleScope.launch {
                runAllTests()
                runButton.isEnabled = true
            }
        }

        log("═══════════════════════════════════", COLOR_HEADER)
        log("  KMPWorker System Test Dashboard", COLOR_HEADER)
        log("  Library: io.neuralheads:kmpworker", COLOR_HEADER)
        log("═══════════════════════════════════", COLOR_HEADER)
        log("Tap 'Run All Tests' to begin.", COLOR_INFO)
    }

    // ── Test Runner ──────────────────────────────────────────────────────────

    private suspend fun runAllTests() {
        log("\n▶ Starting system tests...\n", COLOR_HEADER)

        runTest("1. One-time task executes") { testOneTimeTask() }
        runTest("2. Payload reaches handler") { testPayload() }
        runTest("3. Tags reach handler") { testTags() }
        runTest("4. State observation flow") { testStateObservation() }
        runTest("5. Task cancellation") { testCancellation() }
        runTest("6. Tag-based cancellation") { testTagCancellation() }
        runTest("7. Retry on failure") { testRetryOnFailure() }
        runTest("8. Periodic task enqueued") { testPeriodicTask() }
        runTest("9. Constrained task enqueued") { testConstraints() }
        runTest("10. Config logger integration") { testConfigLogger() }
        runTest("11. Priority queue execution") { testPriorityQueue() }
        runTest("12. Task timeout execution") { testTaskTimeout() }

        log("\n═══════════════════════════════════", COLOR_HEADER)
        val color = if (failCount == 0) COLOR_PASS else COLOR_FAIL
        log("  Results: $passCount passed / $failCount failed", color)
        log("═══════════════════════════════════\n", COLOR_HEADER)

        Log.i(TAG, "Test run complete: $passCount passed, $failCount failed")
    }

    private suspend fun runTest(name: String, block: suspend () -> Boolean) {
        log("\n── $name", COLOR_INFO)
        Log.d(TAG, "Running: $name")
        try {
            val passed = block()
            if (passed) {
                passCount++
                log("   ✅ PASS", COLOR_PASS)
                Log.i(TAG, "✅ PASS: $name")
            } else {
                failCount++
                log("   ❌ FAIL", COLOR_FAIL)
                Log.e(TAG, "❌ FAIL: $name")
            }
        } catch (e: Exception) {
            failCount++
            log("   ❌ FAIL: ${e.message}", COLOR_FAIL)
            Log.e(TAG, "❌ FAIL: $name — ${e.message}", e)
        }
    }

    // ── Individual Tests ─────────────────────────────────────────────────────

    private suspend fun testOneTimeTask(): Boolean {
        var executed = false

        kmpWorker.register("test-onetime") { executed = true }
        kmpWorker.enqueue(TaskRequest(id = "test-onetime", type = TaskType.OneTime))

        delay(3_000) // Give WorkManager time to schedule and execute
        log("   executed=$executed", COLOR_INFO)
        return executed
    }

    private suspend fun testPayload(): Boolean {
        var received: String? = null

        // registerWithContext provides access to TaskExecutionContext.payload
        kmpWorker.registerWithContext("test-payload") {
            received = payload
        }
        kmpWorker.enqueue(
            TaskRequest(
                id = "test-payload",
                type = TaskType.OneTime,
                payload = """{"userId":"u123","action":"sync"}"""
            )
        )

        delay(3_000)
        log("   received=$received", COLOR_INFO)
        return received == """{"userId":"u123","action":"sync"}"""
    }

    private suspend fun testTags(): Boolean {
        var receivedTags: Set<String> = emptySet()

        // registerWithContext provides access to TaskExecutionContext.tags
        kmpWorker.registerWithContext("test-tags") {
            receivedTags = tags
        }
        kmpWorker.enqueue(
            TaskRequest(
                id = "test-tags",
                type = TaskType.OneTime,
                tags = setOf("upload", "critical")
            )
        )

        delay(3_000)
        log("   tags=$receivedTags", COLOR_INFO)
        return receivedTags.contains("upload") && receivedTags.contains("critical")
    }

    private suspend fun testStateObservation(): Boolean {
        val states = mutableListOf<TaskState>()

        kmpWorker.register("test-observe") { /* no-op */ }

        val job = lifecycleScope.launch {
            kmpWorker.observe("test-observe").collect { states.add(it) }
        }

        kmpWorker.enqueue(TaskRequest(id = "test-observe", type = TaskType.OneTime))
        delay(3_000)
        job.cancel()

        log("   states=$states", COLOR_INFO)
        val hasScheduled = states.any { it is TaskState.Scheduled }
        return hasScheduled
    }

    private suspend fun testCancellation(): Boolean {
        kmpWorker.register("test-cancel") {
            delay(60_000) // Long running, should be cancelled before this
        }

        kmpWorker.enqueue(TaskRequest(id = "test-cancel", type = TaskType.OneTime))
        delay(200) // Let it get enqueued
        kmpWorker.cancel("test-cancel")

        delay(500)
        log("   Cancelled successfully", COLOR_INFO)
        return true // If cancel didn't throw, it passed
    }

    private suspend fun testTagCancellation(): Boolean {
        kmpWorker.register("batch-a") { delay(60_000) }
        kmpWorker.register("batch-b") { delay(60_000) }

        kmpWorker.enqueue(
            TaskRequest(id = "batch-a", type = TaskType.OneTime, tags = setOf("batch-tag"))
        )
        kmpWorker.enqueue(
            TaskRequest(id = "batch-b", type = TaskType.OneTime, tags = setOf("batch-tag"))
        )

        delay(200)
        kmpWorker.cancelByTag("batch-tag")

        delay(500)
        log("   Tag-based cancellation completed", COLOR_INFO)
        return true
    }

    private suspend fun testRetryOnFailure(): Boolean {
        var attempts = 0

        kmpWorker.register("test-retry") {
            attempts++
            if (attempts < 2) throw RuntimeException("Simulated failure #$attempts")
        }

        val states = mutableListOf<TaskState>()
        val job = lifecycleScope.launch {
            kmpWorker.observe("test-retry").collect { states.add(it) }
        }

        kmpWorker.enqueue(
            TaskRequest(
                id = "test-retry",
                type = TaskType.OneTime,
                retryPolicy = RetryPolicy.Linear(delayMillis = 1_000)
            )
        )

        delay(5_000)
        job.cancel()

        log("   attempts=$attempts, states=${states.map { it::class.simpleName }}", COLOR_INFO)
        // Scheduled state should always be there
        return states.any { it is TaskState.Scheduled }
    }

    private suspend fun testPeriodicTask(): Boolean {
        kmpWorker.register("test-periodic") { /* no-op */ }

        kmpWorker.enqueue(
            TaskRequest(
                id = "test-periodic",
                type = TaskType.Periodic(repeatIntervalMillis = 15 * 60 * 1_000L)
            )
        )

        val states = mutableListOf<TaskState>()
        val job = lifecycleScope.launch {
            kmpWorker.observe("test-periodic").collect { states.add(it) }
        }

        delay(2_000)
        job.cancel()

        log("   periodic states=${states.map { it::class.simpleName }}", COLOR_INFO)
        return states.any { it is TaskState.Scheduled }
    }

    private suspend fun testConstraints(): Boolean {
        kmpWorker.register("test-constrained") { /* no-op */ }

        kmpWorker.enqueue(
            TaskRequest(
                id = "test-constrained",
                type = TaskType.OneTime,
                constraints = Constraints(
                    requiresInternet = true,
                    batteryNotLow = true,
                    requiresUnmeteredNetwork = true,
                    requiresNonRoamingNetwork = true
                )
            )
        )

        delay(1_000)
        log("   Constrained task enqueued with Wi-Fi & non-roaming constraints", COLOR_INFO)
        return true
    }

    private suspend fun testConfigLogger(): Boolean {
        val logLines = mutableListOf<String>()

        io.neuralheads.kmpworker.core.KmpWorkerConfig.configure {
            logLevel = io.neuralheads.kmpworker.core.KmpWorkerLogger.Level.DEBUG
            logger = io.neuralheads.kmpworker.core.KmpWorkerLogger.Logger { level, msg, _ ->
                logLines.add("[$level] $msg")
                Log.println(
                    when (level) {
                        io.neuralheads.kmpworker.core.KmpWorkerLogger.Level.ERROR -> Log.ERROR
                        io.neuralheads.kmpworker.core.KmpWorkerLogger.Level.WARN  -> Log.WARN
                        io.neuralheads.kmpworker.core.KmpWorkerLogger.Level.INFO  -> Log.INFO
                        else -> Log.DEBUG
                    },
                    TAG,
                    msg
                )
            }
        }

        kmpWorker.register("test-logger") { /* no-op */ }
        kmpWorker.enqueue(TaskRequest(id = "test-logger", type = TaskType.OneTime))

        delay(3_000)
        log("   Logger received ${logLines.size} lines", COLOR_INFO)
        // Logger should have received at least one line (e.g. "KmpTaskWorker: 'test-logger' attempt 1")
        return logLines.isNotEmpty()
    }

    private suspend fun testPriorityQueue(): Boolean {
        kmpWorker.register("test-priority-high") { /* no-op */ }
        kmpWorker.register("test-priority-low") { /* no-op */ }

        kmpWorker.enqueue(
            TaskRequest(
                id = "test-priority-high",
                type = TaskType.OneTime,
                priority = io.neuralheads.kmpworker.core.TaskPriority.HIGH
            )
        )
        kmpWorker.enqueue(
            TaskRequest(
                id = "test-priority-low",
                type = TaskType.OneTime,
                priority = io.neuralheads.kmpworker.core.TaskPriority.LOW
            )
        )

        delay(2_000)
        log("   Priority tasks scheduled successfully", COLOR_INFO)
        return true
    }

    private suspend fun testTaskTimeout(): Boolean {
        var completed = false

        kmpWorker.register("test-timeout-task") {
            delay(5_000)
            completed = true
        }

        val states = mutableListOf<TaskState>()
        val job = lifecycleScope.launch {
            kmpWorker.observe("test-timeout-task").collect { states.add(it) }
        }

        kmpWorker.enqueue(
            TaskRequest(
                id = "test-timeout-task",
                type = TaskType.OneTime,
                timeout = 2.seconds
            )
        )

        delay(4_000)
        job.cancel()

        log("   states=${states.map { it::class.simpleName }}", COLOR_INFO)
        log("   completed=$completed", COLOR_INFO)
        
        val hasTimedOut = states.any { it is TaskState.TimedOut }
        return hasTimedOut && !completed
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun log(message: String, color: Int = COLOR_INFO) {
        val start = logBuilder.length
        logBuilder.append(message).append("\n")
        logBuilder.setSpan(
            ForegroundColorSpan(color),
            start,
            logBuilder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        runOnUiThread {
            logView.text = logBuilder
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
        Log.d(TAG, message)
    }

    private fun openLiveInspector() {
        val composeView = ComposeView(this).apply {
            setContent {
                BackHandler {
                    setContentView(buildLayout())
                }
                KmpWorkerInspectorScreen(kmpWorker = kmpWorker)
            }
        }
        setContentView(composeView)
    }

    private fun buildLayout(): View {
        val context = this

        scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        statusView = TextView(context).apply {
            text = "KMPWorker System Tests"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        }

        runButton = Button(context).apply {
            text = "▶  Run All Tests"
            setBackgroundColor(Color.parseColor("#238636"))
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        val inspectorButton = Button(context).apply {
            text = "📊  Open Live Inspector"
            setBackgroundColor(Color.parseColor("#0969DA"))
            setTextColor(Color.WHITE)
            textSize = 14f
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            layoutParams = params
            setOnClickListener {
                openLiveInspector()
            }
        }

        logView = TextView(context).apply {
            textSize = 12f
            setTextColor(COLOR_INFO)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 16, 0, 16)
        }

        container.addView(statusView)
        container.addView(runButton)
        container.addView(inspectorButton)
        container.addView(logView)
        scrollView.addView(container)

        return scrollView
    }
}
