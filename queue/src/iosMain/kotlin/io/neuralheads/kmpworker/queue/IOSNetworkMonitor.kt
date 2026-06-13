package io.neuralheads.kmpworker.queue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest

/**
 * iOS implementation of [NetworkMonitor].
 *
 * Uses a lightweight connectivity check since NWPathMonitor from
 * Network.framework is not available via Kotlin/Native platform
 * libs in all Kotlin versions.
 */
class IOSNetworkMonitor(
    private val checkIntervalMs: Long = 10_000L
) : NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> = _isOnline

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var running = false

    override fun isCurrentlyOnline(): Boolean = _isOnline.value

    override fun start() {
        if (running) return
        running = true
        scope.launch {
            while (isActive && running) {
                checkConnectivity()
                delay(checkIntervalMs)
            }
        }
    }

    override fun stop() {
        running = false
    }

    private fun checkConnectivity() {
        try {
            val url = NSURL.URLWithString("https://captive.apple.com/hotspot-detect.html")
                ?: return
            val request = NSURLRequest.requestWithURL(url)
            var online = false
            val semaphore = platform.darwin.dispatch_semaphore_create(0)
            NSURLSession.sharedSession.dataTaskWithRequest(request) { _, response, error ->
                online = error == null && response != null
                platform.darwin.dispatch_semaphore_signal(semaphore)
            }.resume()
            platform.darwin.dispatch_semaphore_wait(semaphore, platform.darwin.DISPATCH_TIME_FOREVER)
            _isOnline.value = online
        } catch (_: Exception) {
            _isOnline.value = false
        }
    }
}
