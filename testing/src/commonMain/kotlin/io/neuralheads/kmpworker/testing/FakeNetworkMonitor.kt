package io.neuralheads.kmpworker.testing

import io.neuralheads.kmpworker.queue.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test double for [NetworkMonitor].
 *
 * Allows tests to manually control connectivity state.
 *
 * Usage:
 * ```kotlin
 * val monitor = FakeNetworkMonitor(initiallyOnline = false)
 * monitor.setOnline(true) // simulate network restore
 * ```
 */
class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {

    private val _isOnline = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline

    override fun isCurrentlyOnline(): Boolean = _isOnline.value

    override fun start() { /* no-op in tests */ }

    override fun stop() { /* no-op in tests */ }

    /** Simulates a network connectivity change. */
    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
