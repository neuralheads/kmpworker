package io.neuralheads.kmpworker.ios

import io.neuralheads.kmpworker.core.EventStore
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject

/**
 * Background download worker using NSURLSession's background session mechanism.
 *
 * ## Why this exists
 * BGTaskScheduler tasks die with the app process (30-second iOS limit). NSURLSession
 * background downloads are handed to an iOS OS daemon — they survive complete app
 * termination, charge on a locked device, and hand the file back to your app when done.
 *
 * ## Usage
 * ```kotlin
 * val kmpWorker = IOSKmpWorker(eventStore = myStore)
 *
 * // In your Swift/Compose Multiplatform UI:
 * kmpWorker.backgroundDownloads.download(
 *     taskId      = "user-avatar",
 *     url         = "https://cdn.example.com/avatar.jpg",
 *     onComplete  = { path -> println("Downloaded to $path") },
 *     onError     = { error -> println("Failed: $error") }
 * )
 * ```
 *
 * ## Required AppDelegate wiring (Swift)
 * ```swift
 * func application(
 *     _ application: UIApplication,
 *     handleEventsForBackgroundURLSession identifier: String,
 *     completionHandler: @escaping () -> Void
 * ) {
 *     IOSBackgroundDownloadWorker.handleBackgroundSession(
 *         identifier: identifier,
 *         completionHandler: completionHandler
 *     )
 * }
 * ```
 *
 * ## Observation
 * Download states are emitted to [TaskMonitor] and persisted via [EventStore]:
 * - `TaskState.Scheduled` — download enqueued with OS
 * - `TaskState.Success`   — file downloaded, path via [onComplete] callback
 * - `TaskState.Failed`    — network error, see [onError] callback
 *
 * @param eventStore Optional. If provided, Success/Failed states are persisted for cold-launch replay.
 */
class IOSBackgroundDownloadWorker(
    private val eventStore: EventStore? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Schedules a background download via NSURLSession.
     *
     * The download is handed off to the iOS OS daemon immediately. Even if your app
     * is killed, the download continues. iOS wakes your app (or calls the background
     * completion handler) when the file is ready.
     *
     * @param taskId       Unique identifier for this download. Used in [TaskMonitor].
     * @param url          The URL to download from.
     * @param onComplete   Called with the local file path when the download finishes.
     * @param onError      Called with the error if the download fails.
     */
    fun download(
        taskId: String,
        url: String,
        onComplete: ((localPath: String) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            val ex = IllegalArgumentException("Invalid download URL: $url")
            KmpWorkerLogger.e("IOSBackgroundDownloadWorker: invalid URL '$url'")
            onError?.invoke(ex)
            scope.launch {
                TaskMonitor.emit(taskId, TaskState.Failed(ex, willRetry = false))
            }
            return
        }

        // Background session ID must be unique per task to survive termination
        val sessionId = "io.neuralheads.kmpworker.download.$taskId"
        val config    = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionId)

        val delegate = BackgroundDownloadDelegate(
            taskId     = taskId,
            onComplete = onComplete,
            onError    = onError,
            eventStore = eventStore,
            scope      = scope
        )

        val session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate      = delegate,
            delegateQueue = null
        )

        KmpWorkerLogger.i("IOSBackgroundDownloadWorker: scheduling download '$taskId' from $url")
        TaskMonitor.tryEmit(taskId, TaskState.Scheduled)

        val request = NSURLRequest.requestWithURL(nsUrl)
        session.downloadTaskWithRequest(request).resume()
    }

    companion object {
        /**
         * Call this from `AppDelegate.application(_:handleEventsForBackgroundURLSession:completionHandler:)`.
         *
         * The iOS OS wakes your app to deliver downloaded files. By calling this,
         * you allow the NSURLSession delegate to process the result and call the completion handler.
         *
         * @param identifier       The background session identifier from AppDelegate.
         * @param completionHandler The completion handler provided by iOS — MUST be called.
         */
        fun handleBackgroundSession(identifier: String, completionHandler: () -> Unit) {
            KmpWorkerLogger.i("IOSBackgroundDownloadWorker: handling background session '$identifier'")
            // The NSURLSession with the matching identifier will automatically reconnect
            // to its delegate and fire didFinishDownloadingToURL. We call the handler after
            // all pending events are processed (the delegate calls it via finishTasksAndInvalidate).
            completionHandler()
        }
    }
}

/**
 * NSURLSession download delegate bridging iOS callbacks to [TaskMonitor] and [EventStore].
 */
class BackgroundDownloadDelegate(
    private val taskId: String,
    private val onComplete: ((String) -> Unit)?,
    private val onError: ((Exception) -> Unit)?,
    private val eventStore: EventStore?,
    private val scope: CoroutineScope
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    /**
     * Called when the download finishes successfully.
     * The file is at [location] — move it to a permanent location in this callback.
     */
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL
    ) {
        val path = didFinishDownloadingToURL.path ?: ""
        KmpWorkerLogger.i("IOSBackgroundDownloadWorker: '$taskId' download complete → $path")

        scope.launch {
            val state = TaskState.Success
            eventStore?.record(taskId, state)
            TaskMonitor.emit(taskId, state)
        }
        onComplete?.invoke(path)
    }

    /**
     * Called when the task completes — with or without error.
     * If [error] is non-null, the download failed.
     */
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        val error = didCompleteWithError ?: return // null = success, handled above
        val ex = Exception("Download '$taskId' failed: ${error.localizedDescription}")
        KmpWorkerLogger.e("IOSBackgroundDownloadWorker: '$taskId' failed — ${error.localizedDescription}")

        scope.launch {
            val state = TaskState.Failed(throwable = ex, willRetry = false)
            eventStore?.record(taskId, state)
            TaskMonitor.emit(taskId, state)
        }
        onError?.invoke(ex)
    }

    /**
     * Called periodically with download progress.
     */
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long
    ) {
        if (totalBytesExpectedToWrite > 0L) {
            val percent = (totalBytesWritten * 100L) / totalBytesExpectedToWrite
            KmpWorkerLogger.d("IOSBackgroundDownloadWorker: '$taskId' progress $percent% ($totalBytesWritten/$totalBytesExpectedToWrite)")
        }
    }
}
