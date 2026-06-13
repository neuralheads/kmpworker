package io.neuralheads.kmpworker.transfer

import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.flow.Flow

/**
 * Cross-platform file transfer manager for background downloads and uploads.
 *
 * No Ktor dependency — uses platform-native HTTP APIs:
 * - Android: `HttpURLConnection`
 * - iOS: `NSURLSession` background sessions
 *
 * ```kotlin
 * val manager = TransferManager.create(context) // platform factory
 *
 * // Download with progress
 * manager.download(DownloadRequest(
 *     id = "large-file",
 *     url = "https://example.com/file.zip",
 *     savePath = "/downloads/file.zip",
 *     expectedChecksum = "sha256:abc..."
 * ))
 *
 * // Observe progress
 * manager.observeProgress("large-file").collect { progress ->
 *     println("${progress.percentComplete}%")
 * }
 *
 * // Upload
 * manager.upload(UploadRequest(
 *     id = "backup",
 *     url = "https://api.example.com/upload",
 *     filePath = "/data/backup.zip"
 * ))
 * ```
 */
interface TransferManager {

    /** Starts a background download. */
    suspend fun download(request: DownloadRequest)

    /** Starts a background upload. */
    suspend fun upload(request: UploadRequest)

    /** Cancels an active transfer by ID. */
    suspend fun cancel(transferId: String)

    /** Observes task state for a transfer. */
    fun observe(transferId: String): Flow<TaskState>

    /** Observes transfer progress. */
    fun observeProgress(transferId: String): Flow<TransferProgress>
}
