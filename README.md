# KMPWorker

> A reliability-first Kotlin Multiplatform background task library for Android & iOS â€” by **NeuralHeads**.

[![Maven Central](https://img.shields.io/maven-central/v/com.neuralheads/kmpworker)](https://search.maven.org/search?q=com.neuralheads)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)](https://kotlinlang.org)

---

## What is KMPWorker?

KMPWorker gives you a **single, platform-agnostic API** to schedule and execute background tasks across Android and iOS â€” backed by WorkManager on Android and BGTaskScheduler on iOS.

- âœ… One-time & periodic background tasks
- âœ… Exponential / linear retry policies
- âœ… Task state monitoring via `Flow`
- âœ… SQLDelight-backed persistence (survives app restarts)
- âœ… Offline queue with automatic replay on network restore
- âœ… Group cancellation via tags
- âœ… Execution context (retry count, payload, tags) delivered to handlers
- âœ… Testing utilities with `FakeKmpWorker` â€” no WorkManager needed in tests

---

## Installation

### One import â€” everything included

```kotlin
// build.gradle.kts (KMP shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker:0.1.0")
        }
    }
}
```

That's it. One line gives you: core + android + ios + persistence + offline queue.

### Fine-grained (pick only what you need)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker-core:0.1.0")
        }
        androidMain.dependencies {
            implementation("com.neuralheads:kmpworker-android:0.1.0")
        }
        iosMain.dependencies {
            implementation("com.neuralheads:kmpworker-ios:0.1.0")
        }
        // Optional
        commonMain.dependencies {
            implementation("com.neuralheads:kmpworker-persistence:0.1.0")
            implementation("com.neuralheads:kmpworker-queue:0.1.0")
        }
        commonTest.dependencies {
            implementation("com.neuralheads:kmpworker-testing:0.1.0")
        }
    }
}
```

---

## Quick Start

```kotlin
// 1. Register your task handler at startup
kmpWorker.register("sync-users") {
    repository.syncUsers()
}

// 2. Schedule it
kmpWorker.enqueue(
    TaskRequest(
        id = "sync-users",
        type = TaskType.OneTime,
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
kmpWorker.oneTime(
    id = "upload-logs",
    retryPolicy = exponentialRetry(5.seconds, maxRetries = 3)
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
class MyApp : Application() {
    val kmpWorker: KmpWorker by lazy {
        KmpWorkerBuilder(AndroidKmpWorker(this))
            .configure {
                logLevel = KmpWorkerLogger.Level.DEBUG
                logger   = KmpWorkerAndroidLogger  // routes to android.util.Log
            }
            .task("sync-users") { repository.sync() }
            .build()
    }
}
```

WorkManager is pre-warmed automatically via Jetpack App Startup â€” no manual init required.

---

## iOS Setup

```swift
// AppDelegate.swift
let kmpWorker = IOSKmpWorker()

func application(_ application: UIApplication,
    didFinishLaunchingWithOptions: ...) -> Bool {
    kmpWorker.register(taskId: "sync-users") { ... }
    kmpWorker.initialize()  // registers with BGTaskScheduler
    return true
}
```

**Info.plist** (required):
```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>sync-users</string>
</array>
```

> âš ï¸ Apple controls when background tasks actually execute. See [iOS Limitations](docs/ios-limitations.md).

---

## Retry Policies

```kotlin
RetryPolicy.None                                       // no retry (default)
RetryPolicy.Linear(delayMillis = 3_000)                // fixed 3s gap
RetryPolicy.Exponential(initialDelayMillis = 5_000,    // 5s â†’ 10s â†’ 20s...
                        maxRetries = 4)

// Duration DSL
exponentialRetry(initialDelay = 5.seconds, maxRetries = 3)
linearRetry(delay = 2.seconds)
```

---

## Offline Queue

```kotlin
val queue = OfflineQueue(kmpWorker, repository, networkMonitor)
queue.start()

queue.enqueue(request)
// â†’ Online:  executes immediately
// â†’ Offline: persists to SQLDelight, replays automatically on reconnect
```

---

## Testing

```kotlin
val fakeWorker = FakeKmpWorker()

fakeWorker.register("sync") { repository.sync() }
fakeWorker.enqueue(TaskRequest("sync", TaskType.OneTime))

assertEquals(TaskState.Success, fakeWorker.lastStateFor("sync"))

// Simulate failures
fakeWorker.failureCount["upload"] = 2  // fail 2 times, then succeed
```

---

## Published Artifacts

| Artifact | Description |
|---|---|
| `com.neuralheads:kmpworker` | â­ Umbrella â€” one import, everything |
| `com.neuralheads:kmpworker-core` | Core API, models, retry engine |
| `com.neuralheads:kmpworker-android` | WorkManager integration |
| `com.neuralheads:kmpworker-ios` | BGTaskScheduler integration |
| `com.neuralheads:kmpworker-persistence` | SQLDelight task storage |
| `com.neuralheads:kmpworker-queue` | Offline queue engine |
| `com.neuralheads:kmpworker-testing` | FakeKmpWorker + test doubles |

---

## Version Roadmap

| Version | Features |
|---|---|
| v0.1.0 | One-time tasks, periodic, retry, Flow monitoring, offline queue, persistence |
| v0.2.0 | Task chaining, priorities, foreground service support |
| v0.3.0 | Upload tasks, progress reporting |
| v1.0.0 | Stable API, full docs, production-ready iOS |

---

## License

```
Copyright 2024 NeuralHeads

Licensed under the Apache License, Version 2.0
```
