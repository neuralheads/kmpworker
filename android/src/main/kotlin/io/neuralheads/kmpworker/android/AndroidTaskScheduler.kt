package io.neuralheads.kmpworker.android

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.neuralheads.kmpworker.core.RetryPolicy
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.scheduler.TaskScheduler
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Android implementation of [TaskScheduler] backed by WorkManager.
 *
 * Constraint mapping:
 * | KMPWorker Constraint    | WorkManager Equivalent         |
 * |-------------------------|--------------------------------|
 * | requiresInternet = true | NetworkType.CONNECTED          |
 * | requiresCharging = true | setRequiresCharging(true)      |
 * | batteryNotLow = true    | setRequiresBatteryNotLow(true) |
 *
 * RetryPolicy mapping:
 * | KMPWorker Policy        | WorkManager BackoffPolicy      |
 * |-------------------------|--------------------------------|
 * | RetryPolicy.Linear      | BackoffPolicy.LINEAR           |
 * | RetryPolicy.Exponential | BackoffPolicy.EXPONENTIAL      |
 *
 * Tasks are enqueued as unique work so duplicate IDs replace or keep existing work.
 */
class AndroidTaskScheduler(
    internal val context: Context  // internal so AndroidKmpWorker.cancelByTag can access it
) : TaskScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun enqueue(request: TaskRequest) {
        val (policyType, policyDelay, policyMax) = encodeRetryPolicy(request.retryPolicy)
        val inputData = workDataOf(
            KmpTaskWorker.KEY_TASK_ID to request.id,
            KmpTaskWorker.KEY_RETRY_COUNT to 0,
            KmpTaskWorker.KEY_RETRY_POLICY_TYPE to policyType,
            KmpTaskWorker.KEY_RETRY_POLICY_DELAY to policyDelay,
            KmpTaskWorker.KEY_RETRY_POLICY_MAX to policyMax,
            KmpTaskWorker.KEY_PAYLOAD to request.payload,
            KmpTaskWorker.KEY_TAGS to request.tags.joinToString(",")
        )

        val constraints = buildWorkConstraints(request.constraints)

        when (val type = request.type) {
            is TaskType.OneTime -> {
                val builder = OneTimeWorkRequestBuilder<KmpTaskWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                // Apply tags for WorkManager group cancellation via cancelByTag()
                request.tags.forEach { tag -> builder.addTag(tag) }
                applyRetryPolicyToOneTime(builder, request.retryPolicy)
                workManager.enqueueUniqueWork(
                    request.id,
                    ExistingWorkPolicy.REPLACE,
                    builder.build()
                )
            }

            is TaskType.Periodic -> {
                val workRequest = buildPeriodicRequest(
                    type.repeatIntervalMillis,
                    inputData,
                    constraints,
                    request.retryPolicy
                )
                workManager.enqueueUniquePeriodicWork(
                    request.id,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            }
        }

        TaskMonitor.tryEmit(request.id, TaskState.Scheduled)
    }

    override suspend fun cancel(taskId: String) {
        workManager.cancelUniqueWork(taskId)
    }

    override suspend fun cancelByTag(tag: String) {
        workManager.cancelAllWorkByTag(tag)
    }

    override fun observe(taskId: String): Flow<TaskState> =
        TaskMonitor.observe(taskId)

    override fun observeAll(): Flow<Pair<String, TaskState>> =
        TaskMonitor.observeAll()

    override fun register(taskId: String, block: suspend () -> Unit) =
        TaskRegistry.register(taskId) { block() }

    override fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit) {
        TaskRegistry.register(taskId, block)
    }

    // ── Private builders ─────────────────────────────────────────────────────

    private data class EncodedPolicy(val type: String, val delay: Long, val max: Int)

    private fun encodeRetryPolicy(policy: RetryPolicy): EncodedPolicy = when (policy) {
        is RetryPolicy.None -> EncodedPolicy(KmpTaskWorker.POLICY_NONE, 0L, 0)
        is RetryPolicy.Linear -> EncodedPolicy(KmpTaskWorker.POLICY_LINEAR, policy.delayMillis, Int.MAX_VALUE)
        is RetryPolicy.Exponential -> EncodedPolicy(KmpTaskWorker.POLICY_EXPONENTIAL, policy.initialDelayMillis, policy.maxRetries)
    }

    private fun applyRetryPolicyToOneTime(builder: OneTimeWorkRequest.Builder, retryPolicy: RetryPolicy) {
        when (retryPolicy) {
            is RetryPolicy.None -> { /* no backoff */ }
            is RetryPolicy.Linear -> builder.setBackoffCriteria(BackoffPolicy.LINEAR, retryPolicy.delayMillis, TimeUnit.MILLISECONDS)
            is RetryPolicy.Exponential -> builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, retryPolicy.initialDelayMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun buildWorkConstraints(
        kmpConstraints: io.neuralheads.kmpworker.core.Constraints
    ): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(
                if (kmpConstraints.requiresInternet) NetworkType.CONNECTED
                else NetworkType.NOT_REQUIRED
            )
            .setRequiresCharging(kmpConstraints.requiresCharging)
            .setRequiresBatteryNotLow(kmpConstraints.batteryNotLow)
            .build()
    }

    private fun buildPeriodicRequest(
        repeatIntervalMillis: Long,
        inputData: androidx.work.Data,
        constraints: Constraints,
        retryPolicy: RetryPolicy
    ): PeriodicWorkRequest {
        val builder = PeriodicWorkRequestBuilder<KmpTaskWorker>(
            repeatIntervalMillis,
            TimeUnit.MILLISECONDS
        )
            .setInputData(inputData)
            .setConstraints(constraints)

        when (retryPolicy) {
            is RetryPolicy.None -> { /* no backoff */ }
            is RetryPolicy.Linear -> builder.setBackoffCriteria(
                BackoffPolicy.LINEAR,
                retryPolicy.delayMillis,
                TimeUnit.MILLISECONDS
            )
            is RetryPolicy.Exponential -> builder.setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                retryPolicy.initialDelayMillis,
                TimeUnit.MILLISECONDS
            )
        }

        return builder.build()
    }
}
