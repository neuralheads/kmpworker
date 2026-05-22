---
title: Getting Started
sidebar_position: 1
---

# Getting Started with KMPWorker

## Installation

Add dependencies to your shared module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.neuralheads.kmpworker:kmpworker-core:0.1.0")
        }
        androidMain.dependencies {
            implementation("io.neuralheads.kmpworker:kmpworker-android:0.1.0")
        }
        iosMain.dependencies {
            implementation("io.neuralheads.kmpworker:kmpworker-ios:0.1.0")
        }
    }
}
```

Optional modules:
```kotlin
// Persistence (task survival across app restarts)
commonMain.dependencies {
    implementation("io.neuralheads.kmpworker:kmpworker-persistence:0.1.0")
}

// Offline queue (auto-replay on network restore)
commonMain.dependencies {
    implementation("io.neuralheads.kmpworker:kmpworker-queue:0.1.0")
}

// Testing utilities
testImplementation("io.neuralheads.kmpworker:kmpworker-testing:0.1.0")
```

---

## Android Setup

### 1. Create AndroidKmpWorker

```kotlin
class MyApp : Application() {
    val kmpWorker: KmpWorker by lazy { AndroidKmpWorker(this) }
}
```

### 2. Register tasks at startup

```kotlin
override fun onCreate() {
    super.onCreate()

    // Configure (optional)
    KmpWorkerConfig.configure {
        logLevel = KmpWorkerLogger.Level.DEBUG
        maxRetries = 5
        logger = KmpWorkerLogger.Logger { level, msg, t ->
            Log.println(when(level) {
                KmpWorkerLogger.Level.ERROR -> Log.ERROR
                KmpWorkerLogger.Level.WARN  -> Log.WARN
                else                        -> Log.DEBUG
            }, "KMPWorker", msg)
        }
    }

    kmpWorker.register("sync-users") { repository.sync() }
    kmpWorker.register("upload-logs") { logUploader.upload() }
}
```

### 3. Enqueue tasks

```kotlin
// Simple one-time task
lifecycleScope.launch {
    kmpWorker.enqueue(TaskRequest(
        id = "sync-users",
        type = TaskType.OneTime,
        constraints = Constraints(requiresInternet = true),
        retryPolicy = RetryPolicy.Exponential(initialDelayMillis = 5_000)
    ))
}

// Using DSL
lifecycleScope.launch {
    kmpWorker.oneTime(
        id = "upload-logs",
        retryPolicy = exponentialRetry(initialDelay = 5.seconds, maxRetries = 3)
    ) {
        logUploader.upload()
    }
}
```

---

## iOS Setup

See [iOS Setup](ios-setup.md) and [iOS Limitations](ios-limitations.md) before proceeding.

---

## Observing Task State

```kotlin
kmpWorker.observe("sync-users")
    .collect { state ->
        when (state) {
            is TaskState.Scheduled -> showProgress("Queued...")
            is TaskState.Running   -> showProgress("Running...")
            is TaskState.Success   -> hideProgress()
            is TaskState.Failed    -> showError(state.throwable.message)
        }
    }
```

---

## Next Steps

- [Scheduling Tasks](scheduling-tasks.md)
- [Retry Policies](retry-policies.md)
- [Offline Queue](offline-queue.md)
- [iOS Limitations](ios-limitations.md)
