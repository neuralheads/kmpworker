# KMPWorker Queue — Consumer ProGuard/R8 Rules
#
# Applied automatically to apps depending on kmpworker-queue.

# ── NetworkMonitor implementations ─────────────────────────────────────────────

-keep interface io.neuralheads.kmpworker.queue.NetworkMonitor { *; }
-keep class io.neuralheads.kmpworker.queue.AndroidNetworkMonitor { *; }
-keep class io.neuralheads.kmpworker.queue.OfflineQueue { *; }

# ── ConnectivityManager callback (registered dynamically) ─────────────────────

-keep class android.net.ConnectivityManager$NetworkCallback { *; }
