# Testing Guide

KMPWorker ships a dedicated `:testing` module with pre-built fakes so you can
test your task logic without WorkManager, SQLDelight, or any Android system services.

---

## Setup

```kotlin
// build.gradle.kts
commonTest.dependencies {
    implementation("com.neuralheads:kmpworker-testing:0.1.0-beta01")
}
```

---

## FakeKmpWorker

Drop-in replacement for `KmpWorker` in tests. All task scheduling is in-memory and synchronous.

```kotlin
import io.neuralheads.kmpworker.testing.FakeKmpWorker
import io.neuralheads.kmpworker.core.TaskState

class SyncViewModelTest {

    private val fakeWorker = FakeKmpWorker()
    private val viewModel = SyncViewModel(worker = fakeWorker)

    @Test
    fun `shows spinner when task is running`() = runTest {
        // Register the task
        fakeWorker.register("sync") { /* no-op in test */ }

        // Enqueue and collect state
        val states = mutableListOf<TaskState>()
        val job = launch { fakeWorker.observe("sync").toList(states) }

        fakeWorker.enqueue(TaskRequest(id = "sync", type = TaskType.OneTime))

        // Simulate completion
        fakeWorker.simulateSuccess("sync")
        job.cancel()

        assert(states.any { it is TaskState.Running })
        assert(states.last() is TaskState.Success)
    }

    @AfterTest
    fun tearDown() {
        fakeWorker.reset() // clears all state between tests
    }
}
```

---

## FakeNetworkMonitor

Simulate network connectivity changes in your offline queue tests:

```kotlin
import io.neuralheads.kmpworker.testing.FakeNetworkMonitor

val fakeNetwork = FakeNetworkMonitor(initiallyConnected = false)
val queue = OfflineQueue(worker = fakeWorker, networkMonitor = fakeNetwork)

// Enqueue while offline — task should be held
queue.enqueue(request)
assertFalse(fakeWorker.wasExecuted("sync"))

// Restore connectivity — queue should drain
fakeNetwork.setConnected(true)
assertTrue(fakeWorker.wasExecuted("sync"))
```

---

## FakeTaskRepository

Inspect persisted task state directly without a real database:

```kotlin
import io.neuralheads.kmpworker.testing.FakeTaskRepository

val repo = FakeTaskRepository()

// Pre-seed tasks
repo.save(TaskRecord(id = "sync", state = TaskState.Scheduled))

// Verify state after logic runs
assertEquals(TaskState.Success, repo.getState("sync"))
```

---

## Key FakeKmpWorker API

| Method | Description |
|--------|-------------|
| `register(id) { }` | Registers a task handler |
| `enqueue(request)` | Adds task to the fake queue |
| `simulateSuccess(id)` | Moves task to `TaskState.Success` |
| `simulateFailure(id, error)` | Moves task to `TaskState.Failed` |
| `simulateTimeout(id)` | Moves task to `TaskState.TimedOut` |
| `observe(id)` | Returns `Flow<TaskState>` |
| `wasExecuted(id)` | Returns `true` if the handler ran |
| `executionCountFor(id)` | How many times the task ran |
| `wasCancelled(id)` | Returns `true` if the task was cancelled |
| `reset()` | Clears all state between tests |

---

## Best Practices

- Always call `fakeWorker.reset()` in `@AfterTest` to avoid state leaking between tests
- Use `UnconfinedTestDispatcher` for reliable Flow emission in coroutine tests
- Prefer `FakeKmpWorker` over mocking `KmpWorker` — it gives you real Flow behaviour
