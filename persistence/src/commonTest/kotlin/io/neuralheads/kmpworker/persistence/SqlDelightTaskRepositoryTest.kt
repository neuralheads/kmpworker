package io.neuralheads.kmpworker.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.neuralheads.kmpworker.core.*
import io.neuralheads.kmpworker.persistence.db.KmpWorkerDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class SqlDelightTaskRepositoryTest {

    private lateinit var database: KmpWorkerDatabase
    private lateinit var repository: SqlDelightTaskRepository

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KmpWorkerDatabase.Schema.create(driver)
        database = KmpWorkerDatabase(driver)
        repository = SqlDelightTaskRepository(database)
    }

    @Test
    fun testInsertAndGetById() = runTest {
        val request = TaskRequest(
            id = "task-1",
            type = TaskType.OneTime,
            priority = TaskPriority.HIGH,
            timeout = 10.seconds
        )
        repository.insert(request)

        val retrieved = repository.getById("task-1")
        assertNotNull(retrieved)
        assertEquals("task-1", retrieved.id)
        assertEquals(TaskPriority.HIGH, retrieved.priority)
        assertEquals(10.seconds, retrieved.timeout)
    }

    @Test
    fun testPrioritySorting() = runTest {
        val normalTask = TaskRequest(
            id = "normal-task",
            type = TaskType.OneTime,
            priority = TaskPriority.NORMAL
        )
        val lowTask = TaskRequest(
            id = "low-task",
            type = TaskType.OneTime,
            priority = TaskPriority.LOW
        )
        val highTask = TaskRequest(
            id = "high-task",
            type = TaskType.OneTime,
            priority = TaskPriority.HIGH
        )

        repository.insert(normalTask)
        repository.insert(lowTask)
        repository.insert(highTask)

        val pending = repository.getPending()
        assertEquals(3, pending.size)
        // Order should be HIGH -> NORMAL -> LOW
        assertEquals("high-task", pending[0].id)
        assertEquals("normal-task", pending[1].id)
        assertEquals("low-task", pending[2].id)
    }
}
