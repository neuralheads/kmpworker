package io.neuralheads.kmpworker.sample

import android.app.Application
import io.neuralheads.kmpworker.android.AndroidKmpWorker
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.persistence.KmpWorkerDatabaseFactory
import io.neuralheads.kmpworker.persistence.SqlDelightChainRepository
import io.neuralheads.kmpworker.persistence.SqlDelightEventStore
import io.neuralheads.kmpworker.persistence.SqlDelightTelemetryCollector

/**
 * Sample Application demonstrating KMPWorker setup.
 *
 * In a real app, inject [kmpWorker] via dependency injection (Hilt, Koin, etc.)
 */
class SampleApp : Application() {

    lateinit var kmpWorker: KmpWorker
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize SQLite database and services
        val database = KmpWorkerDatabaseFactory.create(this)
        val eventStore = SqlDelightEventStore(database)
        val chainRepo = SqlDelightChainRepository(database)
        val telemetry = SqlDelightTelemetryCollector(database)
        
        kmpWorker = AndroidKmpWorker(
            context = this,
            eventStore = eventStore,
            chainRepo = chainRepo,
            telemetry = telemetry
        )
        registerTasks()
    }

    /**
     * Register all background task handlers.
     * Must be done before any enqueue() calls.
     */
    private fun registerTasks() {
        // Offline todo sync
        kmpWorker.register("sync-todos") {
            // In a real app: todosRepository.syncWithServer()
            println("KMPWorker: Syncing todos...")
        }

        // Image upload
        kmpWorker.register("upload-image") {
            // In a real app: imageUploader.uploadPending()
            println("KMPWorker: Uploading images...")
        }

        // Periodic data refresh
        kmpWorker.register("refresh-data") {
            // In a real app: dataRepository.refresh()
            println("KMPWorker: Refreshing data...")
        }
    }
}
