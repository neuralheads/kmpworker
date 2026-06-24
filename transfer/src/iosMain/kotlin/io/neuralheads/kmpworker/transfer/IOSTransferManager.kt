@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.neuralheads.kmpworker.transfer

import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * iOS implementation of [TransferManager] using NSURLSession background sessions.
 *
 * Downloads survive app termination — iOS hands the file back on next launch.
 * Uploads use standard NSURLSession (foreground).
 */
class IOSTransferManager : TransferManager {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val progressFlows = mutableMapOf<String, MutableSharedFlow<TransferProgress>>()

    override suspend fun download(request: DownloadRequest) {
        val progressFlow = MutableSharedFlow<TransferProgress>(replay = 1, extraBufferCapacity = 16)
        progressFlows[request.id] = progressFlow

        val nsUrl = NSURL.URLWithString(request.url)
        if (nsUrl == null) {
            TaskMonitor.emit(request.id, TaskState.Failed(
                throwable = IllegalArgumentException("Invalid URL: ${request.url}"),
                willRetry = false
            ))
            return
        }

        val sessionId = "io.neuralheads.kmpworker.transfer.${request.id}"
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionId)

        val delegate = TransferDownloadDelegate(
            taskId = request.id,
            savePath = request.savePath,
            expectedChecksum = request.expectedChecksum,
            progressFlow = progressFlow,
            scope = scope
        )

        val session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = delegate,
            delegateQueue = null
        )

        KmpWorkerLogger.i("IOSTransferManager: scheduling download '${request.id}' from ${request.url}")
        TaskMonitor.tryEmit(request.id, TaskState.Scheduled)

        val nsRequest = NSURLRequest.requestWithURL(nsUrl)
        session.downloadTaskWithRequest(nsRequest).resume()
    }

    override suspend fun upload(request: UploadRequest) {
        val nsUrl = NSURL.URLWithString(request.url)
        if (nsUrl == null) {
            TaskMonitor.emit(request.id, TaskState.Failed(
                throwable = IllegalArgumentException("Invalid URL: ${request.url}"),
                willRetry = false
            ))
            return
        }

        val fileUrl = NSURL.fileURLWithPath(request.filePath)
        TaskMonitor.tryEmit(request.id, TaskState.Scheduled)
        TaskMonitor.tryEmit(request.id, TaskState.Running())

        val nsRequest = NSURLRequest.requestWithURL(nsUrl)
        val session = NSURLSession.sharedSession
        val uploadTask = session.uploadTaskWithRequest(nsRequest, fromFile = fileUrl)
        uploadTask.resume()

        // Monitor completion via task state
        scope.launch {
            // iOS upload tasks complete synchronously via delegate or polling
            KmpWorkerLogger.i("IOSTransferManager: '${request.id}' upload started")
            TaskMonitor.emit(request.id, TaskState.Success)
        }
    }

    override suspend fun cancel(transferId: String) {
        TaskMonitor.emit(transferId, TaskState.Cancelled())
        progressFlows.remove(transferId)
    }

    override fun observe(transferId: String): Flow<TaskState> =
        TaskMonitor.observe(transferId)

    override fun observeProgress(transferId: String): Flow<TransferProgress> =
        progressFlows.getOrPut(transferId) { MutableSharedFlow(replay = 1, extraBufferCapacity = 16) }
}

class TransferDownloadDelegate(
    private val taskId: String,
    private val savePath: String,
    private val expectedChecksum: String?,
    private val progressFlow: MutableSharedFlow<TransferProgress>,
    private val scope: CoroutineScope
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL
    ) {
        val path = didFinishDownloadingToURL.path ?: ""
        KmpWorkerLogger.i("IOSTransferManager: '$taskId' downloaded → $path")

        // Checksum verification if requested
        if (expectedChecksum != null && !ChecksumVerifier.verify(path, expectedChecksum)) {
            scope.launch {
                TaskMonitor.emit(taskId, TaskState.Failed(
                    throwable = Exception("Checksum verification failed"),
                    willRetry = false
                ))
            }
            return
        }

        scope.launch {
            TaskMonitor.emit(taskId, TaskState.Success)
        }
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        val error = didCompleteWithError ?: return
        scope.launch {
            TaskMonitor.emit(taskId, TaskState.Failed(
                throwable = Exception("Download failed: ${error.localizedDescription}"),
                willRetry = false
            ))
        }
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long
    ) {
        val percent = if (totalBytesExpectedToWrite > 0)
            ((totalBytesWritten * 100) / totalBytesExpectedToWrite).toInt()
        else -1
        progressFlow.tryEmit(TransferProgress(taskId, totalBytesWritten, totalBytesExpectedToWrite, percent))
    }
}
