package io.neuralheads.kmpworker.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.neuralheads.kmpworker.persistence.db.KmpWorkerDatabase

/**
 * iOS-specific factory for creating the SQLDelight [KmpWorkerDatabase].
 *
 * Usage:
 * ```kotlin
 * val database = KmpWorkerDatabaseFactory.create()
 * val repository = SqlDelightTaskRepository(database)
 * ```
 */
object KmpWorkerDatabaseFactory {

    /**
     * Creates a production SQLite-backed [KmpWorkerDatabase].
     *
     * The database file is stored in the app's default documents directory.
     * @param name Database file name. Defaults to "kmpworker.db".
     */
    fun create(name: String = "kmpworker.db"): KmpWorkerDatabase {
        val driver = NativeSqliteDriver(
            schema = KmpWorkerDatabase.Schema,
            name = name
        )
        return KmpWorkerDatabase(driver)
    }

    /**
     * Creates an in-memory [KmpWorkerDatabase] for testing.
     */
    fun createInMemory(): KmpWorkerDatabase {
        val driver = NativeSqliteDriver(
            schema = KmpWorkerDatabase.Schema,
            name = ":memory:"
        )
        return KmpWorkerDatabase(driver)
    }
}
