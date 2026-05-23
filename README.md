# KMPWorker

> A reliability-first Kotlin Multiplatform background task library for Android & iOS — by **NeuralHeads**.

[![Maven Central](https://img.shields.io/maven-central/v/com.neuralheads/kmpworker)](https://central.sonatype.com/artifact/com.neuralheads/kmpworker)
[![CI](https://github.com/neuralheads/kmpworker/actions/workflows/ci.yml/badge.svg)](https://github.com/neuralheads/kmpworker/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)](https://kotlinlang.org)

---

## What is KMPWorker?

KMPWorker gives you a **single, platform-agnostic API** to schedule and execute background tasks across Android and iOS — backed by WorkManager on Android and BGTaskScheduler on iOS.

- ✅ One-time & periodic background tasks
- ✅ Exponential / linear retry policies
- ✅ Task state monitoring via `Flow`
- ✅ SQLDelight-backed persistence (survives app restarts)
- ✅ Offline queue with automatic replay on network restore
- ✅ Task chaining — sequential steps with crash-safe resume
- ✅ Group cancellation via tags
- ✅ Execution context (retry count, payload, tags) delivered to handlers
- ✅ Testing utilities with `FakeKmpWorker` — no WorkManager needed in tests

---

## Installation

> **Current version: `0.1.0-alpha03`**

### One import — everything included

```kotlin
// build.gradle.kts (KMP shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker:0.1.0-alpha03")
        }
    }
}
```

### Fine-grained (pick only what you need)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker-core:0.1.0-alpha03")
        }
        androidMain.dependencies {
            implementation("com.neuralheads:kmpworker-android:0.1.0-alpha03")
        }
        // Optional
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker-persistence:0.1.0-alpha03")
            implementation("com.neuralheads:kmpworker-queue:0.1.0-alpha03")
        }
        commonTest.dependencies {
            implementation("com.neuralheads:kmpworker-testing:0.1.0-alpha03")
        }
    }
}
```

> **Note:** `kmpworker-ios` is a Kotlin/Native module included via the KMP metadata in `kmpworker-core`.
> iOS targets are resolved automatically — no separate iOS dependency declaration is needed.

---

## Package Names

All classes live under `io.neuralheads.kmpworker.*`:

| Class | Package |
|-------|---------|
| `KmpWorker` | `io.neuralheads.kmpworker.core` |
| `KmpWorkerBuilder` | `io.neuralheads.kmpworker.core` |
| `TaskRequest` | `io.neuralheads.kmpworker.core` |
| `TaskType` | `io.neuralheads.kmpworker.core` |
| `TaskState` | `io.neuralheads.kmpworker.core` |
| `RetryPolicy` | `io.neuralheads.kmpworker.core` |
| `Constraints` | `io.neuralheads.kmpworker.core` |
| `TaskExecutionContext` | `io.neuralheads.kmpworker.core` |
| `TaskChain` | `io.neuralheads.kmpworker.core` |
| `AndroidKmpWorker` | `io.neuralheads.kmpworker.android` |
| `KmpWorkerAndroidLogger` | `io.neuralheads.kmpworker.android` |
| `IOSKmpWorker` | `io.neuralheads.kmpworker.ios` |
| `OfflineQueue` | `io.neuralheads.kmpworker.queue` |
| `NetworkMonitor` | `io.neuralheads.kmpworker.queue` |
| `AndroidNetworkMonitor` | `io.neuralheads.kmpworker.queue` |
| `IOSNetworkMonitor` | `io.neuralheads.kmpworker.queue` |
| `FakeKmpWorker` | `io.neuralheads.kmpworker.testing` |

---

## Quick Start

```kotlin
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.core.RetryPolicy
import io.neuralheads.kmpworker.core.Constraints

// 1. Register your task handler at startup
kmpWorker.register("sync-users") {
    repository.syncUsers()
}

// 2. Schedule it
kmpWorker.enqueue(
    TaskRequest(
        id          = "sync-users",
        type        = TaskType.OneTime,
        constraints = Constraints(requiresInternet = true),
        retryPolicy = RetryPolicy.Exponential(initialDelayMillis = 5_000, maxRetries = 3)
    )
)

// 3. Observe state via Flow
kmpWorker.observe("sync-users")
    .onSuccess  { hideSpinner() }
    .onFailed   { error -> showError(error.throwable.message) }
    .onRunning  { showSpinner() }
    .collect()
```

### DSL shortcut (register + enqueue in one call)

```kotlin
import io.neuralheads.kmpworker.core.oneTime
import io.neuralheads.kmpworker.core.periodic
import io.neuralheads.kmpworker.core.exponentialRetry
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.hours

kmpWorker.oneTime(
    id          = "upload-logs",
    retryPolicy = exponentialRetry(initialDelay = 5.seconds, maxRetries = 3)
) {
    logUploader.upload()
}

kmpWorker.periodic(id = "sync", repeatInterval = 6.hours) {
    repository.sync()
}
```

---

## Android Setup

```kotlin
import io.neuralheads.kmpworker.android.AndroidKmpWorker
import io.neuralheads.kmpworker.android.KmpWorkerAndroidLogger
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.KmpWorkerBuilder
import io.neuralheads.kmpworker.core.KmpWorkerLogger

class MyApp : Application() {
    val kmpWorker: KmpWorker by lazy {
        KmpWorkerBuilder(
            AndroidKmpWorker(context = this)
            // Optional: add persistence for cold-launch replay:
            // AndroidKmpWorker(context = this, eventStore = SqlDelightEventStore(db))
        )
        .configure {
            logLevel = KmpWorkerLogger.Level.DEBUG
            logger   = KmpWorkerAndroidLogger   // routes to android.util.Log
        }
        .task("sync-users") { repository.sync() }
        .build()
    }
}
```

WorkManager is pre-warmed automatically via Jetpack App Startup — no manual init required.

---

## iOS Setup

`IOSKmpWorker` is a Kotlin class callable from Swift via the compiled KMP framework.

```swift
import kmpworker   // your compiled KMP framework name

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    let kmpWorker = IOSKmpWorker()   // no-arg constructor; eventStore/chainRepo are optional

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Register handlers BEFORE calling initialize()
        kmpWorker.register(taskId: "sync-users") {
            // your task logic
        }
        // Registers all task IDs with BGTaskScheduler
        kmpWorker.initialize()
        return true
    }

    // Required for NSURLSession background downloads
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        IOSBackgroundDownloadWorker.companion.handleBackgroundSession(
            identifier: identifier,
            completionHandler: completionHandler
        )
    }
}
```

**Info.plist** — declare all task identifiers:
```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>sync-users</string>
</array>
```

> ⚠️ Apple controls when background tasks actually execute. See [docs/ios-limitations.md](docs/ios-limitations.md).

---

## API Reference

### `TaskType`

```kotlin
TaskType.OneTime                                    // run once, ASAP
TaskType.Periodic(repeatIntervalMillis = 900_000L)  // repeat every 15 min
TaskType.ExactTime(runAtMillis = epochMs)            // run at/after a specific time
```

### `TaskRequest`

```kotlin
TaskRequest(
    id          = "my-task",          // required — unique stable ID
    type        = TaskType.OneTime,   // required
    constraints = Constraints(
        requiresInternet = true,      // wait for network
        requiresCharging = false,     // wait for charger
        batteryNotLow    = false      // wait for battery > threshold
    ),
    retryPolicy = RetryPolicy.Exponential(initialDelayMillis = 5_000, maxRetries = 3),
    priority    = TaskPriority.HIGH,              // HIGH | NORMAL | LOW
    tags        = setOf("upload", "user-data"),  // Set<String>, not List
    payload     = """{ \"userId\": 42 }"""        // optional JSON string
)
```

### `RetryPolicy`

```kotlin
RetryPolicy.None                                         // no retry (default)
RetryPolicy.Linear(delayMillis = 3_000L)                 // fixed 3 s gap
RetryPolicy.Exponential(initialDelayMillis = 5_000L,     // 5 s → 10 s → 20 s…
                        maxRetries = 4)

// Duration DSL helpers (import io.neuralheads.kmpworker.core.*)
exponentialRetry(initialDelay = 5.seconds, maxRetries = 3)
linearRetry(delay = 2.seconds)
```

### `KmpWorker` interface

| Method | Description |
|--------|-------------|
| `enqueue(request)` | Schedule a background task |
| `cancel(taskId)` | Cancel a task by ID |
| `cancelByTag(tag)` | Cancel all tasks sharing a tag |
| `observe(taskId)` | `Flow<TaskState>` for one task |
| `observeAll()` | `Flow<Pair<String, TaskState>>` for all tasks |
| `register(taskId, block)` | Register a no-context handler |
| `registerWithContext(taskId, block)` | Register a handler receiving `TaskExecutionContext` |
| `enqueueChain(chain)` | Schedule a sequential `TaskChain` |
| `observeChain(chainId)` | `Flow<TaskState>` for a chain |

### `TaskState` — Flow extensions

```kotlin
import io.neuralheads.kmpworker.core.onSuccess
import io.neuralheads.kmpworker.core.onFailed
import io.neuralheads.kmpworker.core.onRunning
import io.neuralheads.kmpworker.core.onCancelled

kmpWorker.observe("sync-users")
    .onRunning  { showSpinner() }
    .onSuccess  { hideSpinner() }
    .onFailed   { error -> showError(error.throwable.message) }
    .onCancelled { hide() }
    .collect()

// Additional extension functions:
// .onTerminal { state -> }           — Success, Cancelled, or final Failed
// .terminalStates()                  — filters to terminal states only
// .failures()                        — Flow<TaskState.Failed>
// .successes()                       — Flow<TaskState.Success>
```

### Typed Payloads

Send and receive strongly-typed data with your tasks — no manual JSON needed:

```kotlin
import io.neuralheads.kmpworker.core.withPayload
import io.neuralheads.kmpworker.core.decodePayload

@Serializable
data class SyncData(val userId: String, val forceRefresh: Boolean = false)

// Attach payload
kmpWorker.enqueue(
    TaskRequest(id = "sync-user", type = TaskType.OneTime)
        .withPayload(SyncData(userId = "u-123"))
)

// Decode inside handler
kmpWorker.registerWithContext("sync-user") {
    val data = decodePayload<SyncData>() ?: return@registerWithContext
    repository.sync(data.userId, forceRefresh = data.forceRefresh)
}
```

### Task Chaining

Execute tasks sequentially — each step only runs after the previous one succeeds. Progress is persisted so chains resume correctly after app termination:

```kotlin
import io.neuralheads.kmpworker.core.TaskChain

val chain = TaskChain(
    id    = "onboarding-flow",
    steps = listOf(
        TaskRequest(id = "step-fetch-profile",  type = TaskType.OneTime),
        TaskRequest(id = "step-upload-avatar",  type = TaskType.OneTime),
        TaskRequest(id = "step-notify-server",  type = TaskType.OneTime)
    )
)

kmpWorker.enqueueChain(chain)

kmpWorker.observeChain("onboarding-flow").collect { state ->
    when (state) {
        is TaskState.Success -> println("All steps complete")
        is TaskState.Failed  -> println("Chain failed: ${state.throwable.message}")
        else -> {}
    }
}
```

> Chaining requires a `ChainRepository` passed to the platform worker constructor:
> `AndroidKmpWorker(context, chainRepo = SqlDelightChainRepository(db))`

---

## Offline Queue

```kotlin
import io.neuralheads.kmpworker.queue.OfflineQueue
import io.neuralheads.kmpworker.queue.AndroidNetworkMonitor  // Android
import io.neuralheads.kmpworker.queue.IOSNetworkMonitor       // iOS (iOS 12+, uses NWPathMonitor)
import io.neuralheads.kmpworker.persistence.TaskRepository

// Requires all 3 parameters
val queue = OfflineQueue(
    worker         = kmpWorker,
    repository     = SqlDelightTaskRepository(database),  // from kmpworker-persistence
    networkMonitor = AndroidNetworkMonitor(context)        // or IOSNetworkMonitor()
)
queue.start()

queue.enqueue(request)
// → Online:  executes immediately
// → Offline: persists to SQLDelight, replays automatically on reconnect

// Manual replay (e.g., on app foreground)
scope.launch { queue.replay() }

// Check pending count
val pending: Int = queue.pendingCount()
```

---

## Testing

Add to your test dependencies:

```kotlin
commonTest.dependencies {
    implementation("com.neuralheads:kmpworker-testing:0.1.0-alpha02")
}
```

```kotlin
import io.neuralheads.kmpworker.testing.FakeKmpWorker
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.core.TaskState

val fakeWorker = FakeKmpWorker()

fakeWorker.register("sync") { repository.sync() }
fakeWorker.enqueue(TaskRequest(id = "sync", type = TaskType.OneTime))

assertEquals(TaskState.Success, fakeWorker.lastStateFor("sync"))

// Simulate failures — fail 2 times, then succeed on the 3rd attempt
fakeWorker.failureCount["upload"] = 2
```

### `FakeKmpWorker` inspection properties

| Property | Type | Description |
|----------|------|-------------|
| `enqueuedTasks` | `List<TaskRequest>` | All tasks passed to `enqueue()` |
| `cancelledTasks` | `List<String>` | Task IDs passed to `cancel()` |
| `cancelledTags` | `List<String>` | Tags passed to `cancelByTag()` |
| `failureCount` | `MutableMap<String, Int>` | Simulate N failures before success |
| `lastStateFor(id)` | `TaskState?` | Last emitted state for a task |
| `allStatesFor(id)` | `List<TaskState>` | Full state history for a task |
| `wasEnqueued(id)` | `Boolean` | Whether the task was enqueued |
| `wasCancelled(id)` | `Boolean` | Whether the task was cancelled |
| `executionCountFor(id)` | `Int` | How many times the task ran |
| `reset()` | — | Clears all state between tests |

---

## Published Artifacts

| Artifact | Version | Description |
|----------|---------|-------------|
| `com.neuralheads:kmpworker` | `0.1.0-alpha03` | ⭐ Umbrella — one import, everything |
| `com.neuralheads:kmpworker-core` | `0.1.0-alpha03` | Core API, models, retry engine, task chaining |
| `com.neuralheads:kmpworker-android` | `0.1.0-alpha03` | Android platform worker |
| `com.neuralheads:kmpworker-persistence` | `0.1.0-alpha03` | SQLDelight task + event + chain storage |
| `com.neuralheads:kmpworker-queue` | `0.1.0-alpha03` | Offline queue + `AndroidNetworkMonitor` + `IOSNetworkMonitor` |
| `com.neuralheads:kmpworker-scheduler` | `0.1.0-alpha03` | `TaskScheduler` interface (advanced: build custom schedulers) |
| `com.neuralheads:kmpworker-testing` | `0.1.0-alpha03` | `FakeKmpWorker` + `FakeNetworkMonitor` + `FakeTaskRepository` |

> `kmpworker-ios` is not a separate published artifact — iOS classes (`IOSKmpWorker`,
> `IOSNetworkMonitor`, `IOSBackgroundDownloadWorker`) are compiled into the KMP `.klib`
> and resolved automatically from `kmpworker-core`.

---

## Version Roadmap

| Version | Status | What's included |
|---------|--------|-----------------|
| `0.1.0-alpha01` | ✅ Released | Core API, WorkManager (Android), BGTaskScheduler (iOS), SQLDelight persistence, offline queue, task chaining, typed payloads, NSURLSession download bridge |
| `0.1.0-alpha02` | ✅ Released | ProGuard/R8 consumer rules, `FakeNetworkMonitor`, `exponentialRetry()` / `linearRetry()` factories, retry bug fixes |
| `0.1.0-alpha03` | ✅ **Current** | `OfflineQueue.executeNow` made suspend, `FakeKmpWorker.reset()` replay fix, `AndroidNetworkMonitor` cleanup, publish infrastructure |
| `0.1.0-beta01` | 🔜 Coming next | Public API freeze, instrumented device tests, Dokka documentation |
| `v0.2.0` | 📋 Planned | Foreground service support, progress reporting |
| `v0.3.0` | 📋 Planned | Upload tasks, background downloads |
| `v1.0.0` | 📋 Planned | Stable API, production-ready iOS, full docs |

> ✅ **Released** = live on Maven Central, usable right now.
> 📋 **Planned** = not built yet — coming in a future release.

---

## Requirements

| Tool | Version |
|------|---------|
| Kotlin | 2.1.0+ |
| Android `minSdk` | 23 |
| Android `compileSdk` | 35 |
| iOS targets | `iosX64`, `iosArm64`, `iosSimulatorArm64` |
| Gradle | 8.x |

---

## License

```
Copyright 2024 NeuralHeads

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0
```
