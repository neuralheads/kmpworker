package io.neuralheads.kmpworker.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.NWPathMonitor
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue

/**
 * iOS implementation of [NetworkMonitor] using [NWPathMonitor].
 * Requires iOS 12+.
 */
class IOSNetworkMonitor : NetworkMonitor {

    private val monitor = NWPathMonitor()
    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline

    override fun isCurrentlyOnline(): Boolean = _isOnline.value

    override fun start() {
        monitor.setUpdateHandler { path ->
            _isOnline.value = (path.status == nw_path_status_satisfied)
        }
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        monitor.startWithQueue(queue)
    }

    override fun stop() {
        monitor.cancel()
    }
}
