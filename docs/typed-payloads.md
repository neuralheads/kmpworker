# Typed Payloads

Pass structured data to your task handlers without raw JSON strings.
KMPWorker serializes your payload automatically using `kotlinx.serialization`.

---

## Sending a Payload

Use `withPayload<T>()` on your `TaskRequest`:

```kotlin
import io.neuralheads.kmpworker.core.withPayload

@Serializable
data class SyncConfig(val userId: String, val forceFullSync: Boolean)

val request = TaskRequest(
    id      = "sync-user",
    type    = TaskType.OneTime,
    payload = withPayload(SyncConfig(userId = "abc123", forceFullSync = true))
)
kmpWorker.enqueue(request)
```

---

## Reading the Payload in a Handler

Use `ctx.decodePayload<T>()` inside your registered handler:

```kotlin
import io.neuralheads.kmpworker.core.decodePayload

kmpWorker.register("sync-user") { ctx ->
    val config = ctx.decodePayload<SyncConfig>()
    repository.sync(userId = config.userId, full = config.forceFullSync)
}
```

---

## Supported Types

Any `@Serializable` class works — primitives, data classes, lists, maps:

```kotlin
// Primitive
payload = withPayload(42)
val count = ctx.decodePayload<Int>()

// List
payload = withPayload(listOf("a", "b", "c"))
val items = ctx.decodePayload<List<String>>()

// Nested data class
@Serializable data class UploadRequest(val fileUri: String, val bucket: String)
payload = withPayload(UploadRequest(fileUri = "...", bucket = "prod"))
val req = ctx.decodePayload<UploadRequest>()
```

---

## When to Use Payloads vs Tags

| Use | For |
|-----|-----|
| `payload` | Structured data the handler needs to do its work |
| `tags` | Grouping tasks for batch cancellation or filtering |

---

## API Reference

| Function | Description |
|----------|-------------|
| `withPayload<T>(value)` | Serializes `value` to a JSON string for `TaskRequest.payload` |
| `TaskExecutionContext.decodePayload<T>()` | Deserializes `payload` back to `T` |
| `TaskRequest.payload` | The raw serialized string (you don't normally need this directly) |
