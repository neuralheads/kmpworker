package io.neuralheads.kmpworker.android

/**
 * Configuration for running a task as a foreground service on Android.
 *
 * When set on a [TaskRequest] with [TaskPriority.HIGH], the task will run
 * as an expedited WorkManager job with a persistent notification.
 *
 * ```kotlin
 * val foreground = ForegroundConfig(
 *     notificationTitle = "Uploading file...",
 *     notificationChannelId = "upload_channel",
 *     notificationChannelName = "Uploads",
 *     notificationId = 1001
 * )
 * ```
 *
 * @param notificationTitle Title shown in the foreground notification.
 * @param notificationChannelId Android notification channel ID (required for API 26+).
 * @param notificationChannelName Human-readable channel name.
 * @param notificationId Unique ID for the notification.
 * @param notificationIcon Resource ID for the small icon. Defaults to the app icon.
 */
data class ForegroundConfig(
    val notificationTitle: String,
    val notificationChannelId: String = "kmpworker_foreground",
    val notificationChannelName: String = "Background Tasks",
    val notificationId: Int = 42,
    val notificationIcon: Int = 0
)
