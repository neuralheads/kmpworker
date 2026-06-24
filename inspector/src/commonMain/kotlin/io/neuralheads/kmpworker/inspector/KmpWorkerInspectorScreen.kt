package io.neuralheads.kmpworker.inspector

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.neuralheads.kmpworker.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Elegant Glassmorphic Dark Colors
private val ColorDarkBg = Color(0xFF0B0F19)
private val ColorCardBg = Color(0x99161B22)
private val ColorBorder = Color(0x3358A6FF)
private val ColorAccentBlue = Color(0xFF58A6FF)
private val ColorAccentGreen = Color(0xFF3FB950)
private val ColorAccentRed = Color(0xFFF85149)
private val ColorAccentOrange = Color(0xFFE3B341)
private val ColorAccentPurple = Color(0xFF8957E5)
private val ColorTextPrimary = Color(0xFFF0F6FC)
private val ColorTextSecondary = Color(0xFF8B949E)

@Composable
fun KmpWorkerInspectorScreen(
    kmpWorker: KmpWorker,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Live states for monitoring
    val activeTasks = remember { mutableStateMapOf<String, TaskState>() }
    var historyRecords by remember { mutableStateOf<List<ExecutionRecord>>(emptyList()) }
    var isSimulationRunning by remember { mutableStateOf(false) }

    // Dialog state for enqueuing a new task
    var showEnqueueDialog by remember { mutableStateOf(false) }

    // Simulated task chain state for DAG Visualizer
    var simulatedChainSteps by remember {
        mutableStateOf<List<Pair<String, TaskState>>>(
            listOf(
                "fetch-payload" to TaskState.Scheduled,
                "decrypt-content" to TaskState.Scheduled,
                "render-assets" to TaskState.Scheduled
            )
        )
    }

    // Load initial history and subscribe to all live updates
    LaunchedEffect(kmpWorker) {
        historyRecords = kmpWorker.getExecutionHistory(50)

        // Observe all live task state changes
        kmpWorker.observeAll().collectLatest { (taskId, state) ->
            activeTasks[taskId] = state
            // Refresh history if state is terminal to show new logs immediately
            if (state.isTerminal) {
                delay(200) // Small delay to allow db write
                historyRecords = kmpWorker.getExecutionHistory(50)
            }
        }
    }

    val refreshHistory = {
        coroutineScope.launch {
            historyRecords = kmpWorker.getExecutionHistory(50)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ColorDarkBg,
            surface = ColorCardBg,
            primary = ColorAccentBlue,
            secondary = ColorTextSecondary
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = ColorDarkBg
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth > 800.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Section
                    HeaderView(
                        onRefresh = { refreshHistory() },
                        onClearHistory = {
                            coroutineScope.launch {
                                kmpWorker.clearExecutionHistory()
                                refreshHistory()
                            }
                        },
                        onOpenEnqueue = { showEnqueueDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metrics Row
                    MetricsGrid(
                        activeCount = activeTasks.values.count { !it.isTerminal },
                        history = historyRecords
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left Column: Task Registry & DAG Visualizer
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                TaskRegistryCard(
                                    registeredIds = TaskRegistry.registeredIds(),
                                    onRunTask = { taskId ->
                                        coroutineScope.launch {
                                            kmpWorker.enqueue(TaskRequest(id = taskId, type = TaskType.OneTime))
                                        }
                                    }
                                )

                                DagVisualizerCard(
                                    steps = simulatedChainSteps,
                                    isRunning = isSimulationRunning,
                                    onTriggerSimulation = {
                                        if (!isSimulationRunning) {
                                            coroutineScope.launch {
                                                isSimulationRunning = true
                                                // Reset simulation
                                                simulatedChainSteps = simulatedChainSteps.map { it.first to TaskState.Scheduled }
                                                
                                                // Step 1
                                                simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                    this[0] = this[0].first to TaskState.Running()
                                                }
                                                delay(1500)
                                                simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                    this[0] = this[0].first to TaskState.Success
                                                    this[1] = this[1].first to TaskState.Running()
                                                }

                                                // Step 2
                                                delay(2000)
                                                simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                    this[1] = this[1].first to TaskState.Success
                                                    this[2] = this[2].first to TaskState.Running()
                                                }

                                                // Step 3
                                                delay(1500)
                                                simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                    this[2] = this[2].first to TaskState.Success
                                                }
                                                isSimulationRunning = false
                                                
                                                // Add a mock execution record to the history for visual completeness
                                                val record = ExecutionRecord(
                                                    taskId = "simulated-dag-chain",
                                                    startedAt = 0L,
                                                    completedAt = 5000L,
                                                    durationMs = 5000,
                                                    state = "SUCCESS",
                                                    retryCount = 0,
                                                    error = null
                                                )
                                                historyRecords = listOf(record) + historyRecords
                                            }
                                        }
                                    }
                                )
                            }

                            // Right Column: Live Queue & Telemetry Logs
                            Column(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LiveQueueCard(activeTasks = activeTasks)
                                TelemetryLogsCard(history = historyRecords)
                            }
                        }
                    } else {
                        // Narrow Layout (Vertical Scroll)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TaskRegistryCard(
                                registeredIds = TaskRegistry.registeredIds(),
                                onRunTask = { taskId ->
                                    coroutineScope.launch {
                                        kmpWorker.enqueue(TaskRequest(id = taskId, type = TaskType.OneTime))
                                    }
                                }
                            )

                            DagVisualizerCard(
                                steps = simulatedChainSteps,
                                isRunning = isSimulationRunning,
                                onTriggerSimulation = {
                                    if (!isSimulationRunning) {
                                        coroutineScope.launch {
                                            isSimulationRunning = true
                                            simulatedChainSteps = simulatedChainSteps.map { it.first to TaskState.Scheduled }
                                            
                                            simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                this[0] = this[0].first to TaskState.Running()
                                            }
                                            delay(1200)
                                            simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                this[0] = this[0].first to TaskState.Success
                                                this[1] = this[1].first to TaskState.Running()
                                            }
                                            delay(1500)
                                            simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                this[1] = this[1].first to TaskState.Success
                                                this[2] = this[2].first to TaskState.Running()
                                            }
                                            delay(1200)
                                            simulatedChainSteps = simulatedChainSteps.toMutableList().apply {
                                                this[2] = this[2].first to TaskState.Success
                                            }
                                            isSimulationRunning = false
                                        }
                                    }
                                }
                            )

                            LiveQueueCard(activeTasks = activeTasks)
                            TelemetryLogsCard(history = historyRecords)
                        }
                    }
                }
            }
        }

        // Show Custom Task Enqueue Dialog
        if (showEnqueueDialog) {
            EnqueueTaskDialog(
                onDismiss = { showEnqueueDialog = false },
                onEnqueue = { id, priority, timeoutSec, requiresWifi, requiresNonRoaming, requiresCharging ->
                    showEnqueueDialog = false
                    coroutineScope.launch {
                        val constraints = Constraints(
                            requiresInternet = requiresWifi || requiresNonRoaming,
                            requiresCharging = requiresCharging,
                            requiresUnmeteredNetwork = requiresWifi,
                            requiresNonRoamingNetwork = requiresNonRoaming
                        )
                        val timeout = if (timeoutSec > 0) timeoutSec.seconds else null
                        
                        // Register a default mock handler if not already registered,
                        // so enqueuing it works and simulates work!
                        if (!TaskRegistry.isRegistered(id)) {
                            kmpWorker.register(id) {
                                delay(2000) // simulate some background work
                            }
                        }

                        kmpWorker.enqueue(
                            TaskRequest(
                                id = id,
                                type = TaskType.OneTime,
                                constraints = constraints,
                                priority = priority,
                                timeout = timeout
                            )
                        )
                    }
                }
            )
        }
    }
}

// ── HEADER VIEW ──────────────────────────────────────────────────────────────
@Composable
private fun HeaderView(
    onRefresh: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenEnqueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = ColorBorder,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
            }
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "KMPWorker Inspector",
                    color = ColorTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Connected Pulse Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ColorAccentGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ColorAccentGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            color = ColorAccentGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "Version 0.1.0-beta06 Dashboard",
                color = ColorTextSecondary,
                fontSize = 11.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenEnqueue,
                colors = ButtonDefaults.buttonColors(containerColor = ColorAccentBlue),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("➕ Enqueue Task", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorCardBg, RoundedCornerShape(6.dp))
                    .border(1.dp, ColorBorder, RoundedCornerShape(6.dp))
            ) {
                Text("🔄", fontSize = 14.sp)
            }
            IconButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorCardBg, RoundedCornerShape(6.dp))
                    .border(1.dp, ColorBorder, RoundedCornerShape(6.dp))
            ) {
                Text("🗑️", fontSize = 14.sp)
            }
        }
    }
}

// ── METRICS GRID ─────────────────────────────────────────────────────────────
@Composable
private fun MetricsGrid(
    activeCount: Int,
    history: List<ExecutionRecord>
) {
    val totalRuns = history.size
    val successfulRuns = history.count { it.state == "SUCCESS" }
    val failedRuns = history.count { it.state == "FAILED" || it.state == "TIMED_OUT" }
    val successRate = if (totalRuns > 0) {
        (successfulRuns.toFloat() / totalRuns * 100).toInt()
    } else {
        100
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            title = "ACTIVE JOBS",
            value = activeCount.toString(),
            subtitle = "Running/Scheduled",
            icon = "⚡",
            color = ColorAccentBlue,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "SUCCESS RATE",
            value = "$successRate%",
            subtitle = "$successfulRuns of $totalRuns runs",
            icon = "📈",
            color = ColorAccentGreen,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "ERRORS / TIMEOUTS",
            value = failedRuns.toString(),
            subtitle = "Needs investigation",
            icon = "⚠️",
            color = ColorAccentRed,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, ColorBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    color = ColorTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    color = color,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = ColorTextSecondary,
                    fontSize = 10.sp
                )
            }
            Text(
                text = icon,
                fontSize = 28.sp,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

// ── TASK REGISTRY CARD ────────────────────────────────────────────────────────
@Composable
private fun TaskRegistryCard(
    registeredIds: Set<String>,
    onRunTask: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "⚙️ REGISTERED HANDLERS",
                color = ColorTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (registeredIds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No handlers registered in TaskRegistry",
                        color = ColorTextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(registeredIds.toList()) { id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, ColorBorder.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = id,
                                    color = ColorTextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                // Priority display from helper
                                val timeout = TaskRegistry.timeoutFor(id)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(ColorAccentPurple.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("HANDLER", color = ColorAccentPurple, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (timeout != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(ColorAccentOrange.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("⏱️ $timeout", color = ColorAccentOrange, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = { onRunTask(id) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorAccentBlue.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, ColorAccentBlue),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Trigger", fontSize = 10.sp, color = ColorAccentBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── DAG VISUALIZER CARD ───────────────────────────────────────────────────────
@Composable
private fun DagVisualizerCard(
    steps: List<Pair<String, TaskState>>,
    isRunning: Boolean,
    onTriggerSimulation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⛓️ TASK CHAIN DAG VISUALIZER",
                    color = ColorTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onTriggerSimulation,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) ColorTextSecondary else ColorAccentPurple
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = if (isRunning) "Running..." else "Simulate Chain",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Step Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, (name, state) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Circle Indicator
                        val circleColor = when (state) {
                            is TaskState.Success -> ColorAccentGreen
                            is TaskState.Running -> ColorAccentBlue
                            is TaskState.Failed -> ColorAccentRed
                            else -> ColorTextSecondary
                        }
                        
                        val pulseScale by rememberInfiniteTransition().animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer {
                                    if (state is TaskState.Running) {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(circleColor.copy(alpha = 0.2f))
                                .border(2.dp, circleColor, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (state) {
                                    is TaskState.Success -> "✓"
                                    is TaskState.Running -> "⏳"
                                    is TaskState.Failed -> "❌"
                                    else -> index.toString()
                                },
                                color = circleColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = name,
                            color = ColorTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (state) {
                                is TaskState.Success -> "Success"
                                is TaskState.Running -> "Running"
                                is TaskState.Failed -> "Failed"
                                else -> "Pending"
                            },
                            color = circleColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (index < steps.size - 1) {
                        // Connecting Arrow/Line
                        val nextState = steps[index + 1].second
                        val lineColor = if (state is TaskState.Success) ColorAccentGreen else ColorTextSecondary
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .weight(0.5f)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(lineColor, if (nextState is TaskState.Running) ColorAccentBlue else ColorTextSecondary)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── LIVE QUEUE CARD ──────────────────────────────────────────────────────────
@Composable
private fun LiveQueueCard(
    activeTasks: Map<String, TaskState>
) {
    val pendingOrRunning = activeTasks.filter { !it.value.isTerminal }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "📡 ACTIVE EXECUTION QUEUE",
                color = ColorTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (pendingOrRunning.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active tasks in queue",
                        color = ColorTextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(pendingOrRunning.toList()) { (id, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, ColorBorder.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = id,
                                    color = ColorTextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val priorityColor = when {
                                        id.contains("high", ignoreCase = true) -> ColorAccentRed
                                        id.contains("low", ignoreCase = true) -> ColorAccentGreen
                                        else -> ColorAccentBlue
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(priorityColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (id.contains("high", ignoreCase = true)) "HIGH" else "NORMAL",
                                            color = priorityColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            // State Pill
                            val stateLabel: String
                            val stateColor: Color
                            when (state) {
                                is TaskState.Running -> {
                                    stateLabel = "RUNNING"
                                    stateColor = ColorAccentBlue
                                }
                                is TaskState.Scheduled -> {
                                    stateLabel = "SCHEDULED"
                                    stateColor = ColorAccentOrange
                                }
                                else -> {
                                    stateLabel = "PENDING"
                                    stateColor = ColorTextSecondary
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(stateColor.copy(alpha = 0.15f))
                                    .border(1.dp, stateColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stateLabel,
                                    color = stateColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── TELEMETRY LOGS CARD ──────────────────────────────────────────────────────
@Composable
private fun TelemetryLogsCard(
    history: List<ExecutionRecord>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "📊 HISTORICAL TELEMETRY LOGS",
                color = ColorTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history records found",
                        color = ColorTextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(history) { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = record.taskId,
                                        color = ColorTextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${record.durationMs}ms",
                                        color = ColorTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                                // Error / Cancel Reason Display
                                val errorMsg = record.error
                                if (!errorMsg.isNullOrBlank()) {
                                    Text(
                                        text = errorMsg,
                                        color = ColorAccentOrange,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Colored State Badges
                            val badgeColor = when (record.state) {
                                "SUCCESS" -> ColorAccentGreen
                                "FAILED" -> ColorAccentRed
                                "TIMED_OUT" -> ColorAccentOrange
                                "CANCELLED" -> ColorTextSecondary
                                else -> ColorAccentBlue
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = record.state,
                                    color = badgeColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── CUSTOM ENQUEUE DIALOG ────────────────────────────────────────────────────
@Composable
private fun EnqueueTaskDialog(
    onDismiss: () -> Unit,
    onEnqueue: (id: String, priority: TaskPriority, timeoutSec: Int, wifi: Boolean, nonRoaming: Boolean, charging: Boolean) -> Unit
) {
    var taskId by remember { mutableStateOf("custom-task-${(100..999).random()}") }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var timeoutSec by remember { mutableStateOf("0") }
    
    var requiresWifi by remember { mutableStateOf(false) }
    var requiresNonRoaming by remember { mutableStateOf(false) }
    var requiresCharging by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enqueue New Task",
                color = ColorTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Task ID Input
                OutlinedTextField(
                    value = taskId,
                    onValueChange = { taskId = it },
                    label = { Text("Task ID", color = ColorTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                        focusedBorderColor = ColorAccentBlue,
                        unfocusedBorderColor = ColorBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority Selection
                Text(
                    text = "Priority",
                    color = ColorTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskPriority.values().forEach { prio ->
                        val selected = priority == prio
                        Button(
                            onClick = { priority = prio },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) ColorAccentBlue else ColorCardBg
                            ),
                            border = BorderStroke(1.dp, if (selected) ColorAccentBlue else ColorBorder),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = prio.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else ColorTextPrimary
                            )
                        }
                    }
                }

                // Timeout Input
                OutlinedTextField(
                    value = timeoutSec,
                    onValueChange = { timeoutSec = it.filter { c -> c.isDigit() } },
                    label = { Text("Task Timeout (Seconds, 0 for none)", color = ColorTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                        focusedBorderColor = ColorAccentBlue,
                        unfocusedBorderColor = ColorBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Constraints Switches
                Text(
                    text = "Constraints",
                    color = ColorTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                ConstraintCheckbox(
                    label = "Requires Wi-Fi (Unmetered)",
                    checked = requiresWifi,
                    onCheckedChange = { requiresWifi = it }
                )
                ConstraintCheckbox(
                    label = "Requires Non-Roaming Network",
                    checked = requiresNonRoaming,
                    onCheckedChange = { requiresNonRoaming = it }
                )
                ConstraintCheckbox(
                    label = "Requires Charging",
                    checked = requiresCharging,
                    onCheckedChange = { requiresCharging = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEnqueue(
                        taskId,
                        priority,
                        timeoutSec.toIntOrNull() ?: 0,
                        requiresWifi,
                        requiresNonRoaming,
                        requiresCharging
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ColorAccentBlue),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Enqueue", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ColorTextSecondary)
            }
        },
        containerColor = ColorDarkBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
    )
}

@Composable
private fun ConstraintCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = ColorAccentBlue)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = ColorTextPrimary, fontSize = 12.sp)
    }
}
