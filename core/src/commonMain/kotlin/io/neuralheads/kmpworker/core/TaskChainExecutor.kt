package io.neuralheads.kmpworker.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Drives the execution of a [TaskChain] by observing [TaskMonitor] and
 * advancing the chain one step at a time.
 *
 * ## Step-level state restoration
 * Every time a step completes successfully, [ChainRepository.updateStep] is called
 * before the next step is enqueued. If the app is killed mid-chain and relaunched,
 * call [restorePendingChains] to resume from the last committed step.
 *
 * ## Usage
 * ```kotlin
 * val executor = TaskChainExecutor(kmpWorker, chainRepository, appScope)
 * executor.execute(myChain)
 *
 * // On app startup (after TaskMonitor.replayPendingEvents()):
 * executor.restorePendingChains()
 * ```
 */
class TaskChainExecutor(
    private val worker: KmpWorker,
    private val chainRepository: ChainRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    /**
     * Starts executing [chain] from step 0.
     * Registers an internal observer that advances steps on success.
     */
    suspend fun execute(chain: TaskChain, policy: ChainPolicy = ChainPolicy.ALLOW_DUPLICATE) {
        // Apply chain policy
        val existing = chainRepository.get(chain.id)
        if (existing != null && existing.status == "RUNNING") {
            when (policy) {
                ChainPolicy.KEEP -> {
                    KmpWorkerLogger.i("TaskChainExecutor: chain '${chain.id}' already running, KEEP policy — skipping")
                    return
                }
                ChainPolicy.REPLACE -> {
                    KmpWorkerLogger.i("TaskChainExecutor: chain '${chain.id}' already running, REPLACE policy — cancelling old")
                    cancel(chain)
                }
                ChainPolicy.ALLOW_DUPLICATE -> { /* proceed */ }
            }
        }

        KmpWorkerLogger.i("TaskChainExecutor: starting chain '${chain.id}' (${chain.totalSteps} steps)")

        // Persist initial chain state before enqueueing anything
        val stepsJson = serializeStepIds(chain)
        chainRepository.save(chain.id, stepsJson, chain.totalSteps)

        // Register all user-provided handlers under their step IDs
        registerStepHandlers(chain)

        // Start observing completions
        observeChain(chain)

        // Enqueue the first step
        enqueueStep(chain, 0)
    }

    /**
     * Restores all chains that were RUNNING when the app was last terminated.
     * Call at app startup, after [TaskMonitor.replayPendingEvents].
     *
     * @param chainProvider A function that returns a [TaskChain] given its [ChainProgress].
     *        The caller must reconstruct the chain from stored step IDs.
     */
    suspend fun restorePendingChains(
        chainProvider: suspend (ChainProgress) -> TaskChain?
    ) {
        val running = chainRepository.getAllRunning()
        KmpWorkerLogger.i("TaskChainExecutor: restoring ${running.size} pending chain(s)")
        for (progress in running) {
            val chain = chainProvider(progress) ?: continue
            KmpWorkerLogger.i("TaskChainExecutor: resuming '${chain.id}' from step ${progress.currentStep}")
            registerStepHandlers(chain)
            observeChain(chain)
            enqueueStep(chain, progress.currentStep)
        }
    }

    /**
     * Cancels a running chain and removes its progress record.
     * Any currently-executing step is cancelled via [KmpWorker.cancel].
     */
    suspend fun cancel(chain: TaskChain) {
        val progress = chainRepository.get(chain.id) ?: return
        val stepId = chain.stepId(progress.currentStep)
        worker.cancel(stepId)
        chainRepository.delete(chain.id)
        TaskMonitor.emit(chain.id, TaskState.Cancelled("Chain cancelled"))
        KmpWorkerLogger.i("TaskChainExecutor: cancelled chain '${chain.id}'")
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private fun observeChain(chain: TaskChain) {
        scope.launch {
            TaskMonitor.observeAll().collect { (taskId, state) ->
                // Only process step events for this chain
                if (!taskId.startsWith("${chain.id}:step:")) return@collect

                val stepIndex = taskId.removePrefix("${chain.id}:step:").toIntOrNull()
                    ?: return@collect

                when {
                    state is TaskState.Success -> onStepSuccess(chain, stepIndex)
                    state is TaskState.Failed && !state.willRetry ->
                        onStepFailed(chain, stepIndex, state.throwable)
                }
            }
        }
    }

    private suspend fun onStepSuccess(chain: TaskChain, completedStep: Int) {
        val nextStep = completedStep + 1
        KmpWorkerLogger.i("TaskChainExecutor: '${chain.id}' step $completedStep ✅")

        if (nextStep < chain.totalSteps) {
            // Persist progress BEFORE enqueueing next step (critical for crash safety)
            chainRepository.updateStep(chain.id, nextStep, "RUNNING")
            enqueueStep(chain, nextStep)
        } else {
            // All steps done
            chainRepository.updateStep(chain.id, completedStep, "COMPLETED")
            KmpWorkerLogger.i("TaskChainExecutor: '${chain.id}' COMPLETED all ${chain.totalSteps} steps")
            TaskMonitor.emit(chain.id, TaskState.Success)
        }
    }

    private suspend fun onStepFailed(chain: TaskChain, failedStep: Int, error: Throwable) {
        KmpWorkerLogger.e("TaskChainExecutor: '${chain.id}' step $failedStep ❌ — aborting chain", error)
        chainRepository.updateStep(chain.id, failedStep, "FAILED")
        TaskMonitor.emit(chain.id, TaskState.Failed(
            throwable  = Exception("Chain '${chain.id}' failed at step $failedStep: ${error.message}", error),
            retryCount = 0,
            willRetry  = false
        ))
    }

    private suspend fun enqueueStep(chain: TaskChain, index: Int) {
        val stepRequest = chain.stepRequest(index)
        KmpWorkerLogger.i("TaskChainExecutor: enqueueing '${chain.id}' step $index ('${chain.steps[index].id}')")
        worker.enqueue(stepRequest)
    }

    private fun registerStepHandlers(chain: TaskChain) {
        chain.steps.forEachIndexed { index, step ->
            val stepId = chain.stepId(index)
            // Only register if not already registered (idempotent for restore path)
            if (!TaskRegistry.isRegistered(stepId)) {
                // Forward to the user's handler registered under the original step ID
                TaskRegistry.register(stepId) {
                    val originalHandler = TaskRegistry.handlerFor(step.id)
                    if (originalHandler != null) {
                        // Reconstruct context with original task ID for the user handler
                        val ctx = TaskExecutionContext(
                            taskId     = step.id,
                            retryCount = retryCount,
                            payload    = payload,
                            tags       = tags
                        )
                        originalHandler.invoke(ctx)
                    } else {
                        KmpWorkerLogger.w("TaskChainExecutor: no handler for step '${step.id}'")
                    }
                }
            }
        }
    }

    private fun serializeStepIds(chain: TaskChain): String {
        val ids = chain.steps.map { it.id }
        return KmpWorkerJson.encodeToString(ListSerializer(String.serializer()), ids)
    }
}

/** Returns the step IDs decoded from a [ChainProgress.stepsJson]. */
fun ChainProgress.decodeStepIds(): List<String> = runCatching {
    KmpWorkerJson.decodeFromString(ListSerializer(String.serializer()), stepsJson)
}.getOrDefault(emptyList())
