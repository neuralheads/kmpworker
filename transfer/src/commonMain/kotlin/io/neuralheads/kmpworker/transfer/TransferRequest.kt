package io.neuralheads.kmpworker.transfer

/**
 * Describes a file download to be performed in the background.
 *
 * ```kotlin
 * val download = DownloadRequest(
 *     id = "avatar-download",
 *     url = "https://cdn.example.com/avatar.jpg",
 *     savePath = "/data/files/avatar.jpg",
 *     expectedChecksum = "sha256:abc123...",
 *     headers = mapOf("Authorization" to "Bearer token")
 * )
 * transferManager.download(download)
 * ```
 *
 * @param id Unique task identifier.
 * @param url URL to download from.
 * @param savePath Local file path to save the downloaded file.
 * @param expectedChecksum Optional SHA-256 checksum for verification (format: "sha256:hex").
 * @param headers Optional HTTP headers to include.
 * @param resumable Whether to support resume on interrupted downloads.
 */
data class DownloadRequest(
    val id: String,
    val url: String,
    val savePath: String,
    val expectedChecksum: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val resumable: Boolean = true
)

/**
 * Describes a file upload to be performed in the background.
 *
 * ```kotlin
 * val upload = UploadRequest(
 *     id = "log-upload",
 *     url = "https://api.example.com/upload",
 *     filePath = "/data/files/logs.zip",
 *     method = "POST",
 *     headers = mapOf("Content-Type" to "application/octet-stream")
 * )
 * transferManager.upload(upload)
 * ```
 *
 * @param id Unique task identifier.
 * @param url URL to upload to.
 * @param filePath Local file path to upload.
 * @param method HTTP method (POST, PUT).
 * @param headers Optional HTTP headers.
 */
data class UploadRequest(
    val id: String,
    val url: String,
    val filePath: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap()
)

/**
 * Progress update for an active transfer.
 *
 * @param id Task identifier.
 * @param bytesTransferred Bytes transferred so far.
 * @param totalBytes Total expected bytes (-1 if unknown).
 * @param percentComplete Progress percentage (0-100), -1 if unknown.
 */
data class TransferProgress(
    val id: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val percentComplete: Int
)
