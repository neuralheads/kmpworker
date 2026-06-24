# KMPWorker

> A reliability-first Kotlin Multiplatform background task library for Android & iOS.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.21-purple)](https://kotlinlang.org)

---

## Features

- One-time, periodic, exact-time & windowed background tasks
- Exponential / linear retry policies with public `RetryEngine`
- Task state monitoring via `Flow` with 10 extension operators
- Progress reporting from within task handlers
- Task chaining with crash-safe resume + builder DSL
- Task dependency graph (DAG) with parallel execution
- Chain policies (KEEP / REPLACE / ALLOW_DUPLICATE)
- Batch operations (enqueueBatch / cancelBatch)
- Rate limiting with coroutine Semaphore
- SQLDelight-backed persistence (survives app restarts)
- Execution history & telemetry
- Offline queue with automatic replay on network restore
- HTTP download & upload (resume, SHA-256 checksum, progress)
- Group cancellation via tags
- Content URI triggers (Android)
- Full constraint support (network, charging, battery, device idle)
- Foreground service configuration (Android)
- Swift interop helpers (FlowWrapper, TaskStateObserver)
- Testing utilities — `FakeKmpWorker`, `FakeNetworkMonitor`, `FakeTaskRepository`
- Android test rule — `KmpWorkerTestRule` for JUnit4
- No Ktor dependency — pure platform APIs
- ProGuard/R8 consumer rules included

---

## Installation

```kotlin
// build.gradle.kts (KMP shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker:0.1.0-beta06")
        }
        // Android platform worker (required for androidMain)
        androidMain.dependencies {
            implementation("com.neuralheads:kmpworker-android:0.1.0-beta06")
        }
    }
}
```

### Optional modules

```kotlin
// HTTP transfers (download/upload with resume & checksum)
implementation("com.neuralheads:kmpworker-transfer:0.1.0-beta06")

// Compose Live Inspector (v0.1.0-beta06+)
implementation("com.neuralheads:kmpworker-inspector:0.1.0-beta06")

// Testing (FakeKmpWorker + KmpWorkerTestRule)
testImplementation("com.neuralheads:kmpworker-testing:0.1.0-beta06")
```

---

## Package Names

All classes live under `io.neuralheads.kmpworker.*`:

| Class | Package |
|-------|---------|
| `KmpWorker`, `TaskRequest`, `TaskState`, `TaskType` | `io.neuralheads.kmpworker.core` |
| `RetryPolicy`, `RetryEngine`, `Constraints` | `io.neuralheads.kmpworker.core` |
| `TaskChain`, `TaskChainBuilder`, `ChainPolicy` | `io.neuralheads.kmpworker.core` |
| `TaskGraph`, `TaskGraphBuilder`, `TaskGraphExecutor` | `io.neuralheads.kmpworker.core` |
| `RateLimiter`, `TelemetryCollector`, `ExecutionRecord` | `io.neuralheads.kmpworker.core` |
| `AndroidKmpWorker`, `ForegroundConfig` | `io.neuralheads.kmpworker.android` |
| `IOSKmpWorker`, `FlowWrapper`, `TaskStateObserver` | `io.neuralheads.kmpworker.ios` |
| `OfflineQueue`, `NetworkMonitor` | `io.neuralheads.kmpworker.queue` |
| `TransferManager`, `DownloadRequest`, `UploadRequest` | `io.neuralheads.kmpworker.transfer` |
| `FakeKmpWorker`, `KmpWorkerTestRule` | `io.neuralheads.kmpworker.testing` |

---

## Quick Start

```kotlin
// 1. Register handler
kmpWorker.register("sync-users") {
    repository.syncUsers()
}

// 2. Schedule
kmpWorker.enqueue(
    TaskRequest(
        id = "sync-users",
        type = TaskType.OneTime,
        constraints = Constraints(requiresInternet = true),
        retryPolicy = RetryPolicy.Exponential(initialDelayMillis = 5_000, maxRetries = 3)
    )
)

// 3. Observe
kmpWorker.observe("sync-users")
    .onRunning  { showSpinner() }
    .onProgress { progress, msg -> updateBar(progress) }
    .onSuccess  { hideSpinner() }
    .onFailed   { error -> showError(error.throwable.message) }
    .collect()
```

### DSL shortcuts

```kotlin
kmpWorker.oneTime(id = "upload", retryPolicy = exponentialRetry(5.seconds, maxRetries = 3)) {
    uploader.upload()
}

kmpWorker.periodic(id = "sync", repeatInterval = 6.hours) {
    repository.sync()
}
```

---

## Android Setup

```kotlin
class MyApp : Application() {
    val kmpWorker: KmpWorker by lazy {
        KmpWorkerBuilder(
            AndroidKmpWorker(
                context = this,
                telemetry = SqlDelightTelemetryCollector(database),  // optional
                foregroundConfig = ForegroundConfig(                  // optional
                    notificationTitle = "Syncing..."
                )
            )
        )
        .configure {
            logLevel = KmpWorkerLogger.Level.DEBUG
            logger = KmpWorkerAndroidLogger
        }
        .task("sync") { repository.sync() }
        .build()
    }
}
```

WorkManager is initialized automatically via Jetpack App Startup.

---

## iOS Setup

```swift
let kmpWorker = IOSKmpWorker()

func application(_ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    kmpWorker.register(taskId: "sync") { /* ... */ }
    kmpWorker.initialize()
    return true
}
```

**Info.plist:**
```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>sync</string>
</array>
```

### Swift Flow observation

```swift
let observer = TaskStateObserver(flow: kmpWorker.observe(taskId: "sync"))
observer.onStateChange { state in
    print("State: \(state)")
}
observer.stop()
```

---

## Task Types

```kotlin
TaskType.OneTime                                        // run once ASAP
TaskType.Periodic(repeatIntervalMillis = 900_000)       // every 15 min
TaskType.ExactTime(runAtMillis = epochMs)                // at specific time
TaskType.Windowed(earliestMillis = t1, latestMillis = t2) // within a window
```

## Constraints

```kotlin
Constraints(
    requiresInternet = true,
    requiresCharging = false,
    batteryNotLow = true,
    requiresUnmeteredNetwork = true,                      // Wi-Fi only (v0.1.0-beta06+)
    requiresNonRoamingNetwork = true,                     // Non-roaming only (v0.1.0-beta06+)
    requiresDeviceIdle = false,                           // Android only
    contentUris = listOf("content://media/external/images") // Android only
)
```

---

## Task Chaining

### Constructor API

```kotlin
val chain = TaskChain(
    id = "onboarding",
    steps = listOf(
        TaskRequest(id = "fetch-profile", type = TaskType.OneTime),
        TaskRequest(id = "upload-avatar", type = TaskType.OneTime),
        TaskRequest(id = "notify-server", type = TaskType.OneTime)
    )
)
kmpWorker.enqueueChain(chain, ChainPolicy.REPLACE)
```

### Builder DSL

```kotlin
kmpWorker.chain("onboarding", policy = ChainPolicy.REPLACE) {
    beginWith("fetch-profile")
    then("upload-avatar") {
        constraints = Constraints(requiresInternet = true)
    }
    then("notify-server") {
        retryPolicy = RetryPolicy.Exponential(5_000, 3)
    }
}
```

### Chain Policies

| Policy | Behavior |
|--------|----------|
| `KEEP` | Skip if chain ID already running |
| `REPLACE` | Cancel existing, start new |
| `ALLOW_DUPLICATE` | Always enqueue (default) |

---

## Task Dependency Graph (DAG)

Execute tasks with complex dependencies — independent nodes run in parallel:

```kotlin
@OptIn(ExperimentalKmpWorkerApi::class)
kmpWorker.graph("pipeline") {
    val fetch = task("fetch-data")
    val process = task("process")
    val validate = task("validate")
    val upload = task("upload")

    fetch then process      // process depends on fetch
    fetch then validate     // validate runs PARALLEL with process
    process then upload     // upload waits for BOTH
    validate then upload
}
```

---

## Progress Reporting

```kotlin
kmpWorker.registerWithContext("upload") {
    for (i in 0..100 step 10) {
        reportProgress(i / 100f, "Uploading chunk $i")
        delay(500)
    }
}

kmpWorker.observe("upload")
    .onProgress { progress, message -> updateBar(progress) }
    .collect()
```

---

## Batch Operations

```kotlin
kmpWorker.enqueueBatch(listOf(
    TaskRequest("task-1", TaskType.OneTime),
    TaskRequest("task-2", TaskType.OneTime),
    TaskRequest("task-3", TaskType.OneTime)
))

kmpWorker.cancelBatch(listOf("task-1", "task-2", "task-3"))
```

---

## Rate Limiting

```kotlin
@OptIn(ExperimentalKmpWorkerApi::class)
val limiter = RateLimiter(maxConcurrent = 3)

limiter.withPermit {
    // At most 3 tasks execute concurrently
    doHeavyWork()
}
```

---

## Execution History & Telemetry

```kotlin
val worker = AndroidKmpWorker(
    context = this,
    telemetry = SqlDelightTelemetryCollector(database)
)

// Query history
val records = worker.getExecutionHistory(limit = 50)
records.forEach { println("${it.taskId}: ${it.state} (${it.durationMs}ms)") }

// Clear
worker.clearExecutionHistory()
```

---

## Compose Live Inspector

KMPWorker includes a gorgeous, reactive live inspector dashboard built with Compose Multiplatform to monitor and simulate task execution in real-time.

```kotlin
// Host the inspector screen inside a ComposeView or Composable tree
KmpWorkerInspectorScreen(kmpWorker = kmpWorker)
```

The inspector provides:
- **Real-Time Metrics Grid**: View active task counts, success rate, and error/timeout counters.
- **Registered Handlers**: Displays all registered handlers in `TaskRegistry` with individual buttons to trigger them.
- **Live Queue**: Real-time list of pending and running tasks.
- **DAG Visualizer**: Live step-indicator visualizing sequential task chain progress. Includes a "Simulate Chain" button.
- **Historical Telemetry Logs**: A scrollable list displaying past runs with durations, states, and error/cancellation reasons.
- **Custom Task Enqueuer**: Dialog to quickly schedule new tasks with custom priorities, timeouts, and hardware/network constraints.

---

## HTTP Transfers

No Ktor required — uses `HttpURLConnection` (Android) and `NSURLSession` (iOS):

```kotlin
val manager = AndroidTransferManager() // or IOSTransferManager()

manager.download(DownloadRequest(
    id = "large-file",
    url = "https://example.com/file.zip",
    savePath = "/downloads/file.zip",
    expectedChecksum = "sha256:abc123...",
    resumable = true
))

manager.observeProgress("large-file").collect { progress ->
    println("${progress.percentComplete}%")
}

manager.upload(UploadRequest(
    id = "backup",
    url = "https://api.example.com/upload",
    filePath = "/data/backup.zip"
))
```

---

## Offline Queue

```kotlin
val queue = OfflineQueue(
    worker = kmpWorker,
    repository = SqlDelightTaskRepository(database),
    networkMonitor = AndroidNetworkMonitor(context) // or IOSNetworkMonitor()
)
queue.start()

queue.enqueue(request)
// Online: executes immediately
// Offline: persists, replays on reconnect
```

---

## Testing

```kotlin
// Unit tests with FakeKmpWorker
val fake = FakeKmpWorker()
fake.register("sync") { repository.sync() }
fake.enqueue(TaskRequest("sync", TaskType.OneTime))
assertEquals(TaskState.Success, fake.lastStateFor("sync"))

// Simulate failures
fake.failureCount["upload"] = 2  // fails 2x, succeeds on 3rd

// Android instrumented tests
class MyWorkerTest {
    @get:Rule
    val rule = KmpWorkerTestRule()

    @Test
    fun testSync() = runTest {
        rule.worker.register("sync") { /* ... */ }
        rule.worker.enqueue(TaskRequest("sync", TaskType.OneTime))
        assertEquals(TaskState.Success, rule.worker.lastStateFor("sync"))
    }
}
```

---

## Flow Extensions

| Extension | Description |
|-----------|-------------|
| `.onRunning { }` | Task is executing |
| `.onSuccess { }` | Task completed |
| `.onFailed { error -> }` | Task failed |
| `.onCancelled { }` | Task was cancelled |
| `.onTimedOut { timeout -> }` | Task exceeded timeout |
| `.onTerminal { state -> }` | Any terminal state |
| `.onProgress { progress, msg -> }` | Progress reported |
| `.terminalStates()` | Filter to terminal only |
| `.failures()` | Filter to `Failed` only |
| `.successes()` | Filter to `Success` only |
| `.progressUpdates()` | Filter to `Running` with progress |

---

## Published Modules

| Module | Description |
|--------|-------------|
| `kmpworker` | Umbrella — includes everything |
| `kmpworker-core` | Core API, models, retry, chains, DAG |
| `kmpworker-android` | WorkManager integration |
| `kmpworker-persistence` | SQLDelight storage + telemetry |
| `kmpworker-queue` | Offline queue + network monitors |
| `kmpworker-transfer` | HTTP download/upload (no Ktor) |
| `kmpworker-inspector` | Compose Multiplatform Live Inspector UI |
| `kmpworker-testing` | FakeKmpWorker + test rule |
| `kmpworker-scheduler` | TaskScheduler interface |

---

## Requirements

| Tool | Version |
|------|---------|
| Kotlin | 2.1.21+ |
| Android minSdk | 23 |
| Android compileSdk | 35 |
| iOS | iosX64, iosArm64, iosSimulatorArm64 |
| Gradle | 9.x |

---

## License

```
Copyright 2026 NeuralHeads
Licensed under the Apache License, Version 2.0
```
