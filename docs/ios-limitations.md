---
title: iOS Limitations
sidebar_position: 5
---

# iOS Background Execution Limitations

> ⚠️ **Read this before shipping to production on iOS.**

KMPWorker uses Apple's **BGTaskScheduler** framework on iOS. Understanding its constraints is essential for setting correct user expectations and designing reliable task flows.

---

## What Apple Controls (not KMPWorker)

| Aspect | Who Controls It |
|--------|-----------------|
| When your task actually runs | **Apple** |
| How long your task gets to run | **Apple** |
| Whether your task runs at all | **Apple** |
| Task frequency | **Apple** (informed by your hint) |

---

## Task Types and Their Limits

### BGAppRefreshTask (OneTime)

Used for `TaskType.OneTime`. Characteristics:
- **Time limit**: ~30 seconds. The OS will call your expiration handler if you exceed this.
- **No guaranteed timing**: Apple schedules based on battery, app usage patterns, signal strength.
- **Minimum interval**: Apple enforces a minimum gap between refreshes, even if you request more frequently.

### BGProcessingTask (Periodic)

Used for `TaskType.Periodic`. Characteristics:
- **Longer window**: Can run for minutes rather than seconds.
- **Conditions**: Usually only runs when device is **plugged in and idle**.
- **Not guaranteed**: Apple may not schedule it if conditions aren't right.

---

## What KMPWorker Does

KMPWorker handles the following on your behalf:

✅ **Registers** all task identifiers with BGTaskScheduler at app startup via `BackgroundInitializer.initialize()`

✅ **Expiration handlers**: When iOS terminates a task early, KMPWorker:
  - Cancels the coroutine job to prevent work leaking
  - Emits `TaskState.Failed` with a descriptive exception message
  - Calls `task.setTaskCompletedWithSuccess(false)` to inform iOS

✅ **Re-scheduling**: After each execution (success or failure), you should re-submit the task request if you want it to run again. BGAppRefreshTask is one-shot.

---

## Required Setup

### Info.plist

You **must** declare every task identifier in `Info.plist` or iOS will silently reject submissions:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>sync-users</string>
    <string>upload-logs</string>
</array>
```

### AppDelegate

Call `initialize()` **before** the app finishes launching:

```swift
func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
) -> Bool {
    BackgroundInitializerKt.initialize()
    return true
}
```

---

## Testing on iOS

The iOS Simulator does **not** honour BGTaskScheduler timing. To force a task to run during development:

1. Pause in Xcode debugger
2. In the LLDB console:
```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"sync-users"]
```

---

## Designing for iOS Reliability

Given these constraints, design your tasks defensively:

1. **Always handle `TaskState.Failed`** — assume the task may be killed.
2. **Persist progress** — use the `:persistence` module to save partial work so you can resume after expiration.
3. **Keep tasks fast** — anything that must finish within 30s for `BGAppRefreshTask`.
4. **Don't rely on periodic for critical work** — for truly critical operations, use `URLSession` background transfers instead.
5. **Test the expiration path** — simulate expiration to ensure your handler runs cleanly.
