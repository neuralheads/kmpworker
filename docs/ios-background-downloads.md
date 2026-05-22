---
title: iOS Background Downloads
sidebar_position: 6
---

# iOS Background Downloads (NSURLSession Bridge)

KMPWorker includes a native **NSURLSession background download bridge** that survives
complete app termination. This is different from `BGTaskScheduler` tasks — the download
is handed to the iOS OS daemon and continues even if the user swipes the app away.

---

## How It Works

```
Your App     →  NSURLSession.download()  →  iOS OS Daemon  →  Download completes
                                                              ↓
App killed?  →  No problem. Daemon holds the file.
                                                              ↓
iOS wakes app → BackgroundDownloadDelegate.didFinishDownloadingToURL() → TaskMonitor.emit(Success)
```

---

## Setup

### 1. AppDelegate.swift — Background Session Handler

You **must** implement this or iOS silently drops the completion event:

```swift
func application(
    _ application: UIApplication,
    handleEventsForBackgroundURLSession identifier: String,
    completionHandler: @escaping () -> Void
) {
    IOSBackgroundDownloadWorkerCompanion().handleBackgroundSession(
        identifier: identifier,
        completionHandler: completionHandler
    )
}
```

### 2. AppDelegate.swift — Initialize IOSKmpWorker with EventStore

```swift
let database = // ... your KmpWorkerDatabase
let kmpWorker = IOSKmpWorker(
    eventStore: SqlDelightEventStore(database: database)
)

func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
) -> Bool {
    kmpWorker.initialize()  // Replays persisted events + registers BGTaskScheduler tasks
    return true
}
```

---

## Usage

```kotlin
// Schedule a background download
kmpWorker.backgroundDownloads.download(
    taskId     = "user-avatar",
    url        = "https://cdn.example.com/avatar.jpg",
    onComplete = { path ->
        // Move file to permanent location — called on any thread
        NSFileManager.defaultManager.moveItemAtPath(path, toPath = "/permanent/path.jpg", error = null)
    },
    onError = { error ->
        println("Download failed: $error")
    }
)

// Observe via TaskMonitor (cross-platform)
kmpWorker.observe("user-avatar").collect { state ->
    when (state) {
        is TaskState.Scheduled -> showLoadingIndicator()
        is TaskState.Success   -> showAvatar()
        is TaskState.Failed    -> showRetryButton()
        else -> {}
    }
}
```

---

## Comparison: BGTaskScheduler vs. NSURLSession

| Feature                         | BGTaskScheduler (KmpWorker default) | NSURLSession (this bridge) |
|---------------------------------|-------------------------------------|----------------------------|
| **Survives app termination**    | ❌ Task is killed with app           | ✅ OS daemon continues     |
| **Time limit**                  | ~30 seconds                         | None (limited by network)  |
| **File downloads**              | Possible but fragile                | Native, purpose-built      |
| **Progress tracking**           | Manual                              | `didWriteData` callback    |
| **Retry on failure**            | Via KMPWorker retry policies        | OS-level retry             |
| **Cold-launch event replay**    | ✅ Via EventStore                   | ✅ Via EventStore           |
| **TaskMonitor integration**     | ✅                                  | ✅                          |

**Rule of thumb**: Use BGTaskScheduler for computation tasks. Use NSURLSession bridge for file downloads.

---

## Cold-Launch Event Replay

If the download completes while the app is completely terminated, the event is:
1. Persisted to SQLite via `EventStore.record()` inside `BackgroundDownloadDelegate`
2. Re-emitted on next launch by `kmpWorker.initialize()` → `TaskMonitor.replayPendingEvents()`

Your UI observing `kmpWorker.observe("user-avatar")` will receive the `Success` state on cold launch.
