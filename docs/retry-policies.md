---
title: Retry Policies
sidebar_position: 3
---

# Retry Policies

KMPWorker has built-in retry support for all three policy types. Retries are handled by the platform layer (WorkManager on Android, custom handler on iOS) using the common `RetryEngine`.

---

## Policy Types

### `RetryPolicy.None` (default)

No retry on failure. The task fails immediately.

```kotlin
retryPolicy = RetryPolicy.None  // default if omitted
```

---

### `RetryPolicy.Linear`

Fixed delay between every retry attempt.

```kotlin
retryPolicy = RetryPolicy.Linear(delayMillis = 3_000) // 3s between each retry
```

| Attempt | Delay |
|---------|-------|
| 1       | 3s    |
| 2       | 3s    |
| 3       | 3s    |

---

### `RetryPolicy.Exponential`

Delay doubles on each retry. Best for network operations.

```kotlin
retryPolicy = RetryPolicy.Exponential(
    initialDelayMillis = 5_000,  // 5s base
    maxRetries = 5               // stop after 5 retries
)
```

Formula: `delay(n) = initialDelayMillis × 2^n`

| Attempt | Delay |
|---------|-------|
| 1       | 5s    |
| 2       | 10s   |
| 3       | 20s   |
| 4       | 40s   |
| 5       | 80s   |

---

## DSL Helpers

```kotlin
// Exponential retry using Duration
retryPolicy = exponentialRetry(
    initialDelay = 5.seconds,
    maxRetries = 3
)

// Linear retry using Duration
retryPolicy = linearRetry(delay = 2.seconds)
```

---

## Global Max Retries

Set a global ceiling via `KmpWorkerConfig`:

```kotlin
KmpWorkerConfig.configure {
    maxRetries = 10  // applies to Exponential if no maxRetries set
}
```

Individual `RetryPolicy.Exponential.maxRetries` overrides the global setting.

---

## iOS Behaviour

On iOS, retries are managed by KMPWorker's coroutine handler — not by BGTaskScheduler. Each retry re-submits a new BGAppRefreshTask request. Apple may delay execution based on system conditions.

---

## Android Behaviour

On Android, retry delay is mapped to WorkManager's `BackoffPolicy`:

| KMPWorker Policy | WorkManager BackoffPolicy |
|-----------------|--------------------------|
| Linear           | LINEAR                   |
| Exponential      | EXPONENTIAL              |

WorkManager enforces a **minimum backoff of 10 seconds** regardless of the value you set.
