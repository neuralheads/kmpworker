package io.neuralheads.kmpworker.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TaskExecutionContextTest {

    @Test
    fun `isFirstAttempt true when retryCount is 0`() {
        val ctx = TaskExecutionContext(taskId = "t", retryCount = 0)
        assertTrue(ctx.isFirstAttempt)
    }

    @Test
    fun `isRetry true when retryCount greater than 0`() {
        val ctx = TaskExecutionContext(taskId = "t", retryCount = 2)
        assertTrue(ctx.isRetry)
    }

    @Test
    fun `payload accessible in context`() {
        val ctx = TaskExecutionContext(taskId = "t", payload = "{\"key\":\"value\"}")
        assertEquals("{\"key\":\"value\"}", ctx.payload)
    }

    @Test
    fun `tags accessible in context`() {
        val ctx = TaskExecutionContext(taskId = "t", tags = setOf("upload", "critical"))
        assertTrue(ctx.tags.contains("upload"))
        assertTrue(ctx.tags.contains("critical"))
    }

    @Test
    fun `handler receives context via TaskRegistry`() = runTest {
        TaskRegistry.clearAll()
        var receivedContext: TaskExecutionContext? = null

        TaskRegistry.register("ctx-task") {
            receivedContext = this  // `this` is the TaskExecutionContext receiver
        }

        val context = TaskExecutionContext(
            taskId = "ctx-task",
            retryCount = 2,
            payload = "test-payload",
            tags = setOf("tag1")
        )

        TaskRegistry.execute("ctx-task", context)

        assertEquals("ctx-task", receivedContext?.taskId)
        assertEquals(2, receivedContext?.retryCount)
        assertEquals("test-payload", receivedContext?.payload)
        assertTrue(receivedContext?.tags?.contains("tag1") == true)
    }
}
