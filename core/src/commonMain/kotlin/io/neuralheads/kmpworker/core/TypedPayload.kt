package io.neuralheads.kmpworker.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Default [Json] instance used by KMPWorker for typed payload encoding/decoding.
 *
 * - `ignoreUnknownKeys = true` — forwards-compatible: new fields added server-side don't crash old clients.
 * - `encodeDefaults = true` — ensures all fields are included in the serialized output.
 */
val KmpWorkerJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Encodes [data] as JSON and attaches it as the payload for this [TaskRequest].
 *
 * Usage:
 * ```kotlin
 * data class SyncData(val userId: String, val forceRefresh: Boolean = false)
 *
 * kmpWorker.enqueue(
 *     TaskRequest(id = "sync-user", type = TaskType.OneTime)
 *         .withPayload(SyncData(userId = "u-123"))
 * )
 * ```
 *
 * @param data The strongly-typed object to serialize as payload.
 * @return A copy of this [TaskRequest] with the payload set to the JSON-encoded [data].
 */
inline fun <reified T> TaskRequest.withPayload(data: T): TaskRequest =
    copy(payload = KmpWorkerJson.encodeToString(serializer<T>(), data))

/**
 * Decodes the payload of this [TaskExecutionContext] into a strongly-typed object.
 *
 * Usage (inside a registered handler):
 * ```kotlin
 * kmpWorker.registerWithContext("sync-user") {
 *     val data = decodePayload<SyncData>()
 *     repository.sync(data?.userId ?: return@registerWithContext)
 * }
 * ```
 *
 * @return The decoded object, or `null` if the payload is absent or deserialization fails.
 */
inline fun <reified T> TaskExecutionContext.decodePayload(): T? = runCatching {
    payload?.let { KmpWorkerJson.decodeFromString(serializer<T>(), it) }
}.getOrNull()

/**
 * Decodes a raw JSON string into a strongly-typed object.
 * Useful when you have the payload String directly (e.g., from logging or testing).
 *
 * @return The decoded object, or `null` if the string is blank or deserialization fails.
 */
inline fun <reified T> String?.decodeAsPayload(): T? = runCatching {
    if (isNullOrBlank()) null
    else KmpWorkerJson.decodeFromString(serializer<T>(), this!!)
}.getOrNull()
