package io.neuralheads.kmpworker.core

/**
 * Policy for handling duplicate chain IDs when enqueueing a [TaskChain].
 *
 * ```kotlin
 * kmpWorker.enqueueChain(chain, policy = ChainPolicy.REPLACE)
 * ```
 */
enum class ChainPolicy {
    /** If a chain with this ID is already running, skip the new one. */
    KEEP,

    /** If a chain with this ID is already running, cancel it and start the new one. */
    REPLACE,

    /** Always enqueue, even if a chain with this ID exists. */
    ALLOW_DUPLICATE
}
