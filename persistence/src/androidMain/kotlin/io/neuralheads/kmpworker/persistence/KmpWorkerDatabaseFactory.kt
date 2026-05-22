package io.neuralheads.kmpworker.persistence

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import android.content.Context
import io.neuralheads.kmpworker.persistence.db.KmpWorkerDatabase

/**
 * Android-specific factory for creating the SQLDelight [KmpWorkerDatabase].
 *
 * Usage:
 * ```kotlin
 * val database = KmpWorkerDatabaseFactory.create(context)
 * val repository = SqlDelightTaskRepository(database)
 * ```
 */
object KmpWorkerDatabaseFactory {

    /**
     * Creates a production SQLite-backed [KmpWorkerDatabase].
     *
     * @param context Application context.
     * @param name Database file name. Defaults to "kmpworker.db".
     */
    fun create(
        context: Context,
        name: String = "kmpworker.db"
    ): KmpWorkerDatabase {
        val driver = AndroidSqliteDriver(
            schema = KmpWorkerDatabase.Schema,
            context = context,
            name = name
        )
        return KmpWorkerDatabase(driver)
    }

    /**
     * Creates an in-memory [KmpWorkerDatabase] for testing.
     * Data is lost when the database is closed.
     */
    fun createInMemory(context: Context): KmpWorkerDatabase {
        val driver = AndroidSqliteDriver(
            schema = KmpWorkerDatabase.Schema,
            context = context,
            name = null  // null = in-memory
        )
        return KmpWorkerDatabase(driver)
    }
}
