# Task Chaining

Chain multiple background tasks so they execute **sequentially**, with each step
only starting after the previous one succeeds. The entire chain is persisted to
SQLDelight, so it survives app kills and restarts.

---

## Basic Chain

```kotlin
import io.neuralheads.kmpworker.core.TaskChain
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskType

// 1. Register handlers for each step
kmpWorker.register("validate-user") { validateUserData() }
kmpWorker.register("upload-profile") { uploadProfileToServer() }
kmpWorker.register("notify-backend") { sendPushNotification() }

// 2. Build and enqueue the chain
val chain = TaskChain(
    id = "onboarding-chain",
    steps = listOf(
        TaskRequest(id = "validate-user",   type = TaskType.OneTime),
        TaskRequest(id = "upload-profile",  type = TaskType.OneTime),
        TaskRequest(id = "notify-backend",  type = TaskType.OneTime)
    )
)
kmpWorker.enqueueChain(chain)
```

---

## Observing Chain Progress

```kotlin
kmpWorker.observeChain("onboarding-chain")
    .collect { (stepId, state) ->
        when {
            state.isTerminal && state is TaskState.Success ->
                println("Step $stepId completed!")
            state is TaskState.Failed ->
                println("Step $stepId failed: ${state.throwable.message}")
        }
    }
```

---

## Chain Behaviour

| Scenario | Result |
|----------|--------|
| Step succeeds | Next step starts immediately |
| Step fails (no retry left) | Chain stops; remaining steps are skipped |
| Step fails (retry configured) | Step retries per its `RetryPolicy`; chain waits |
| App killed mid-step | Chain resumes from the failed step on next launch |
| Chain cancelled | All pending steps are cancelled |

---

## Passing Data Between Steps

Use the `payload` field on `TaskRequest` to pass data from one step to the next.
Encode using [Typed Payloads](typed-payloads.md):

```kotlin
kmpWorker.register("upload-profile") { ctx ->
    val userId = ctx.decodePayload<String>()
    uploadProfile(userId)
}

TaskRequest(
    id      = "upload-profile",
    type    = TaskType.OneTime,
    payload = withPayload("user-123")
)
```

---

## API Reference

| Function | Description |
|----------|-------------|
| `kmpWorker.enqueueChain(chain)` | Enqueues a chain for sequential execution |
| `kmpWorker.observeChain(id)` | Returns `Flow<Pair<String, TaskState>>` per step |
| `kmpWorker.cancelChain(id)` | Cancels all pending steps in the chain |
| `TaskChain(id, steps)` | Builds a chain from a list of `TaskRequest` |
