package io.neuralheads.kmpworker.transfer

import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android implementation of [TransferManager] using [HttpURLConnection].
 *
 * No Ktor, no OkHttp — pure JDK HTTP for zero extra dependencies.
 * Supports resume (HTTP Range header), SHA-256 checksum verification, and progress reporting.
 */
class AndroidTransferManager : TransferManager {

    private val progressFlows = mutableMapOf<String, MutableSharedFlow<TransferProgress>>()

    override suspend fun download(request: DownloadRequest) = withContext(Dispatchers.IO) {
        val progressFlow = MutableSharedFlow<TransferProgress>(replay = 1, extraBufferCapacity = 16)
        progressFlows[request.id] = progressFlow

        TaskMonitor.tryEmit(request.id, TaskState.Scheduled)

        try {
            val file = File(request.savePath)
            file.parentFile?.mkdirs()

            var existingBytes = 0L
            if (request.resumable && file.exists()) {
                existingBytes = file.length()
            }

            val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }

            val responseCode = conn.responseCode
            if (responseCode !in listOf(200, 206)) {
                throw Exception("HTTP $responseCode: ${conn.responseMessage}")
            }

            val totalBytes = if (responseCode == 206) {
                conn.getHeaderField("Content-Range")
                    ?.substringAfter("/")?.toLongOrNull()
                    ?: (conn.contentLengthLongCompat + existingBytes)
            } else {
                existingBytes = 0 // full download, reset
                conn.contentLengthLongCompat
            }

            TaskMonitor.tryEmit(request.id, TaskState.Running())

            val append = responseCode == 206
            conn.inputStream.use { input ->
                FileOutputStream(file, append).use { output ->
                    copyWithProgress(input, output, request.id, existingBytes, totalBytes, progressFlow)
                }
            }
            conn.disconnect()

            // Checksum verification
            if (request.expectedChecksum != null) {
                if (!ChecksumVerifier.verify(request.savePath, request.expectedChecksum)) {
                    file.delete()
                    throw Exception("Checksum verification failed for '${request.id}'")
                }
                KmpWorkerLogger.i("AndroidTransferManager: '${request.id}' checksum verified")
            }

            TaskMonitor.emit(request.id, TaskState.Success)
            KmpWorkerLogger.i("AndroidTransferManager: '${request.id}' download complete → ${request.savePath}")

        } catch (e: Exception) {
            KmpWorkerLogger.e("AndroidTransferManager: '${request.id}' failed", e)
            TaskMonitor.emit(request.id, TaskState.Failed(throwable = e, willRetry = false))
        } finally {
            progressFlows.remove(request.id)
        }
    }

    override suspend fun upload(request: UploadRequest) = withContext(Dispatchers.IO) {
        val progressFlow = MutableSharedFlow<TransferProgress>(replay = 1, extraBufferCapacity = 16)
        progressFlows[request.id] = progressFlow

        TaskMonitor.tryEmit(request.id, TaskState.Scheduled)

        try {
            val file = File(request.filePath)
            if (!file.exists()) throw Exception("File not found: ${request.filePath}")

            val totalBytes = file.length()
            TaskMonitor.tryEmit(request.id, TaskState.Running())

            val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Content-Length", totalBytes.toString())
                request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            file.inputStream().use { input ->
                conn.outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    var written = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        written += bytesRead
                        val percent = if (totalBytes > 0) ((written * 100) / totalBytes).toInt() else -1
                        progressFlow.tryEmit(TransferProgress(request.id, written, totalBytes, percent))
                    }
                    output.flush()
                }
            }

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299) {
                TaskMonitor.emit(request.id, TaskState.Success)
                KmpWorkerLogger.i("AndroidTransferManager: '${request.id}' upload complete (HTTP $responseCode)")
            } else {
                throw Exception("Upload HTTP $responseCode: ${conn.responseMessage}")
            }

        } catch (e: Exception) {
            KmpWorkerLogger.e("AndroidTransferManager: '${request.id}' upload failed", e)
            TaskMonitor.emit(request.id, TaskState.Failed(throwable = e, willRetry = false))
        } finally {
            progressFlows.remove(request.id)
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

    private fun copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        id: String,
        startBytes: Long,
        totalBytes: Long,
        progressFlow: MutableSharedFlow<TransferProgress>
    ) {
        val buffer = ByteArray(8192)
        var written = startBytes
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            written += bytesRead
            val percent = if (totalBytes > 0) ((written * 100) / totalBytes).toInt() else -1
            progressFlow.tryEmit(TransferProgress(id, written, totalBytes, percent))
        }
    }
}

private val HttpURLConnection.contentLengthLongCompat: Long
    get() = getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
