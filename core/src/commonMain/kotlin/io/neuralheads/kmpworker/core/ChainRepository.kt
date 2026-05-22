package io.neuralheads.kmpworker.core

/**
 * Persistent store for [TaskChain] progress.
 *
 * Stores which step each active chain is currently on. Used by [TaskChainExecutor]
 * to resume chains from the correct step after app termination.
 */
interface ChainRepository {

    /**
     * Saves the initial progress for a newly started chain.
     * @param chainId The [TaskChain.id].
     * @param stepsJson JSON-serialized list of step task IDs (for cold-launch replay).
     * @param totalSteps Total number of steps.
     */
    suspend fun save(chainId: String, stepsJson: String, totalSteps: Int)

    /**
     * Updates the current step and status of an existing chain.
     * @param chainId The [TaskChain.id].
     * @param currentStep Zero-based index of the step now executing.
     * @param status One of: "RUNNING", "COMPLETED", "FAILED".
     */
    suspend fun updateStep(chainId: String, currentStep: Int, status: String)

    /**
     * Returns the progress record for [chainId], or null if not found.
     */
    suspend fun get(chainId: String): ChainProgress?

    /**
     * Returns all chains that are currently RUNNING (for cold-launch restoration).
     */
    suspend fun getAllRunning(): List<ChainProgress>

    /**
     * Deletes the progress record for [chainId].
     */
    suspend fun delete(chainId: String)
}

/**
 * Snapshot of a chain's current state.
 */
data class ChainProgress(
    val chainId: String,
    val stepsJson: String,
    val currentStep: Int,
    val totalSteps: Int,
    val status: String,
    val updatedAt: Long
)
