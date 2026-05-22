package io.neuralheads.kmpworker.core

/**
 * Task execution priority hint.
 *
 * Supported on Android (maps to WorkManager expedited work / priority hints).
 * On iOS, priority is informational only — Apple controls execution order.
 *
 * | Priority | Android Mapping              |
 * |----------|------------------------------|
 * | HIGH     | Expedited work request       |
 * | NORMAL   | Standard work request        |
 * | LOW      | Deferred / battery-saver     |
 */
enum class TaskPriority {
    HIGH,
    NORMAL,
    LOW
}
