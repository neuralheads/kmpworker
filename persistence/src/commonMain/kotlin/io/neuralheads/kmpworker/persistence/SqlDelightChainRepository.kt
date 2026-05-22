package io.neuralheads.kmpworker.persistence

import io.neuralheads.kmpworker.core.ChainProgress
import io.neuralheads.kmpworker.core.ChainRepository
import io.neuralheads.kmpworker.persistence.db.KmpWorkerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [ChainRepository].
 *
 * Persists chain step progress to SQLite so chains can resume after app termination.
 * All operations are dispatched to [Dispatchers.IO].
 */
class SqlDelightChainRepository(
    private val database: KmpWorkerDatabase
) : ChainRepository {

    private val queries get() = database.chain_progressQueries

    override suspend fun save(
        chainId: String,
        stepsJson: String,
        totalSteps: Int
    ): Unit = withContext(Dispatchers.IO) {
        queries.upsertChain(
            chain_id     = chainId,
            steps_json   = stepsJson,
            current_step = 0L,
            total_steps  = totalSteps.toLong(),
            status       = "RUNNING",
            updated_at   = currentEpochMillis()
        )
    }

    override suspend fun updateStep(
        chainId: String,
        currentStep: Int,
        status: String
    ): Unit = withContext(Dispatchers.IO) {
        queries.updateStep(
            current_step = currentStep.toLong(),
            status       = status,
            updated_at   = currentEpochMillis(),
            chain_id     = chainId
        )
    }

    override suspend fun get(chainId: String): ChainProgress? =
        withContext(Dispatchers.IO) {
            queries.getChain(chainId).executeAsOneOrNull()?.toProgress()
        }

    override suspend fun getAllRunning(): List<ChainProgress> =
        withContext(Dispatchers.IO) {
            queries.getAllRunning().executeAsList().map { it.toProgress() }
        }

    override suspend fun delete(chainId: String): Unit = withContext(Dispatchers.IO) {
        queries.deleteChain(chainId)
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun Chain_progress.toProgress() =
        ChainProgress(
            chainId     = chain_id,
            stepsJson   = steps_json,
            currentStep = current_step.toInt(),
            totalSteps  = total_steps.toInt(),
            status      = status,
            updatedAt   = updated_at
        )
}
