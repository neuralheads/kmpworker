package io.neuralheads.kmpworker.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.neuralheads.kmpworker.core.KmpWorkerConfig
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.RetryEngine
import io.neuralheads.kmpworker.core.RetryPolicy
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRegistry
import io.neuralheads.kmpworker.core.TaskState

/**
 * WorkManager [CoroutineWorker] that bridges the Android scheduling system
 * into the KMPWorker execution model.
 *
 * Lifecycle per execution:
 * 1. Reads taskId + retry policy from [inputData]
 * 2. Checks if max retries exhausted — returns [Result.failure] if so
 * 3. Emits [TaskState.Running]
 * 4. Builds [TaskExecutionContext] with taskId, retryCount, payload, tags
 * 5. Invokes registered handler via [TaskRegistry.execute]
 * 6. Success → emits [TaskState.Success] → returns [Result.success]
 * 7. Failure → checks retry policy → [Result.retry] or [Result.failure]
 */
class KmpTaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val KEY_RETRY_COUNT = "retryCount"
        const val KEY_RETRY_POLICY_TYPE = "retryPolicyType"
        const val KEY_RETRY_POLICY_DELAY = "retryPolicyDelay"
        const val KEY_RETRY_POLICY_MAX = "retryPolicyMax"
        const val KEY_PAYLOAD = "payload"
        const val KEY_TAGS = "tags"

        const val POLICY_NONE = "none"
        const val POLICY_LINEAR = "linear"
        const val POLICY_EXPONENTIAL = "exponential"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?: return Result.failure()

        val retryCount = runAttemptCount
        val retryPolicy = readRetryPolicy()
        val payload = inputData.getString(KEY_PAYLOAD)
        val tags = inputData.getString(KEY_TAGS)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        KmpWorkerLogger.d("KmpTaskWorker: '$taskId' attempt ${retryCount + 1}")

        // Exhausted retries — fail definitively.
        // Note: RetryPolicy.None means "no retries configured, but always run the handler".
        // We only apply the exhausted check for policies that actually schedule retries.
        if (retryPolicy !is RetryPolicy.None &&
            !RetryEngine.shouldRetry(retryCount, retryPolicy) &&
            retryCount > 0
        ) {
            KmpWorkerLogger.w("KmpTaskWorker: '$taskId' exhausted retries after $retryCount attempts")
            TaskMonitor.emit(taskId, TaskState.Failed(
                throwable = Exception("Task '$taskId' exhausted retry policy after $retryCount attempts."),
                retryCount = retryCount,
                willRetry = false
            ))
            return Result.failure()
        }

        return try {
            TaskMonitor.emit(taskId, TaskState.Running)

            val ctx = TaskExecutionContext(
                taskId = taskId,
                retryCount = retryCount,
                payload = payload,
                tags = tags
            )
            TaskRegistry.execute(taskId, ctx)

            TaskMonitor.emit(taskId, TaskState.Success)
            KmpWorkerLogger.i("KmpTaskWorker: '$taskId' succeeded")
            Result.success()

        } catch (e: Exception) {
            val willRetry = RetryEngine.shouldRetry(retryCount, retryPolicy)
            KmpWorkerLogger.e("KmpTaskWorker: '$taskId' failed (willRetry=$willRetry)", e)
            TaskMonitor.emit(taskId, TaskState.Failed(
                throwable = e,
                retryCount = retryCount,
                willRetry = willRetry
            ))
            if (willRetry) Result.retry() else Result.failure()
        }
    }

    private fun readRetryPolicy(): RetryPolicy {
        return when (inputData.getString(KEY_RETRY_POLICY_TYPE)) {
            POLICY_LINEAR -> RetryPolicy.Linear(
                delayMillis = inputData.getLong(KEY_RETRY_POLICY_DELAY, 5_000L)
            )
            POLICY_EXPONENTIAL -> RetryPolicy.Exponential(
                initialDelayMillis = inputData.getLong(KEY_RETRY_POLICY_DELAY, 5_000L),
                maxRetries = inputData.getInt(KEY_RETRY_POLICY_MAX, KmpWorkerConfig.current().maxRetries)
            )
            else -> RetryPolicy.None
        }
    }
}
