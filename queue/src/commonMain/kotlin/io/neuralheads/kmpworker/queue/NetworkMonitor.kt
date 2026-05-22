package io.neuralheads.kmpworker.queue

import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors network connectivity state.
 *
 * Platform implementations:
 * - Android: Uses [ConnectivityManager] with [NetworkCallback]
 * - iOS: Uses [NWPathMonitor]
 *
 * Observe [isOnline] to react to connectivity changes in real time.
 */
interface NetworkMonitor {

    /**
     * Current connectivity state. True when a network is available.
     */
    val isOnline: StateFlow<Boolean>

    /**
     * Returns the current connectivity state synchronously.
     */
    fun isCurrentlyOnline(): Boolean

    /**
     * Starts monitoring network changes.
     * Must be called before observing [isOnline].
     */
    fun start()

    /**
     * Stops monitoring and releases system resources.
     */
    fun stop()
}
