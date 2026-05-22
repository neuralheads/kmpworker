---
title: Offline Queue
sidebar_position: 4
---

# Offline Queue

The `OfflineQueue` is KMPWorker's standout feature. It provides seamless offline-first task execution — tasks submitted while the device is offline are persisted and automatically replayed when connectivity is restored.

---

## How It Works

```
enqueue(request)
     ↓
isOnline?
     ↓ YES → KmpWorker.enqueue() — executes now
     ↓ NO  → TaskRepository.insert() — stored in SQLDelight
              ↓
         network restored (NWPathMonitor / ConnectivityManager)
              ↓
         OfflineQueue.replay() → re-enqueue all PENDING tasks
```

---

## Setup

```kotlin
// 1. Provide dependencies
val worker: KmpWorker = AndroidKmpWorker(context)
val repository: TaskRepository = SqlDelightTaskRepository(database)
val networkMonitor: NetworkMonitor = AndroidNetworkMonitor(context)

// 2. Build OfflineQueue
val offlineQueue = OfflineQueue(worker, repository, networkMonitor)

// 3. Start monitoring (call once at app startup)
networkMonitor.start()
offlineQueue.start()

// 4. Register handlers as usual
worker.register("sync-todos") { todosRepo.sync() }
```

---

## Enqueue

```kotlin
offlineQueue.enqueue(
    TaskRequest(
        id = "sync-todos",
        type = TaskType.OneTime,
        constraints = Constraints(requiresInternet = true)
    )
)
```

- **Online**: Immediately sent to `KmpWorker.enqueue()`
- **Offline**: Saved to SQLDelight with status `PENDING`

---

## App Restart Recovery

Tasks survive app termination. On next app startup:

```kotlin
// Option A: let OfflineQueue handle it automatically via start() + network callback

// Option B: manually trigger on app foreground
lifecycleScope.launch {
    offlineQueue.replay()
}
```

---

## Observing Queue State

Each task in the offline queue, once replayed, emits state through the normal `KmpWorker.observe()` channel:

```kotlin
worker.observe("sync-todos").collect { state ->
    when (state) {
        is TaskState.Running -> showSyncing()
        is TaskState.Success -> showSynced()
        is TaskState.Failed  -> showError()
        else -> { }
    }
}
```

---

## Platform Implementations

### Android — `AndroidNetworkMonitor`

Uses `ConnectivityManager.NetworkCallback` to reactively update a `StateFlow<Boolean>`.

```kotlin
val monitor = AndroidNetworkMonitor(context)
monitor.start()
```

### iOS — `IOSNetworkMonitor`

Uses `NWPathMonitor` (iOS 12+) to reactively update a `StateFlow<Boolean>`.

```kotlin
val monitor = IOSNetworkMonitor()
monitor.start()
```

---

## Testing

Use `FakeNetworkMonitor` to control connectivity state in tests:

```kotlin
val monitor = FakeNetworkMonitor(initiallyOnline = false)
val queue = OfflineQueue(fakeWorker, fakeRepo, monitor)
queue.start()

queue.enqueue(myTask)
// task is persisted (offline)

monitor.setOnline(true)
// triggers replay automatically
```
