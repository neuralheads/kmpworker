package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TaskStateExtensionsTest {

    @Test
    fun `Success isTerminal is true`() {
        assertTrue(TaskState.Success.isTerminal)
    }

    @Test
    fun `Scheduled isTerminal is false`() {
        assertFalse(TaskState.Scheduled.isTerminal)
    }

    @Test
    fun `Running isTerminal is false`() {
        assertFalse(TaskState.Running.isTerminal)
    }

    @Test
    fun `Cancelled isTerminal is true`() {
        assertTrue(TaskState.Cancelled.isTerminal)
    }

    @Test
    fun `Failed with willRetry=true is not terminal`() {
        val state = TaskState.Failed(throwable = RuntimeException(), willRetry = true)
        assertFalse(state.isTerminal)
    }

    @Test
    fun `Failed with willRetry=false is terminal`() {
        val state = TaskState.Failed(throwable = RuntimeException(), willRetry = false)
        assertTrue(state.isTerminal)
    }

    @Test
    fun `Scheduled isActive is true`() {
        assertTrue(TaskState.Scheduled.isActive)
    }

    @Test
    fun `Running isActive is true`() {
        assertTrue(TaskState.Running.isActive)
    }

    @Test
    fun `Success isActive is false`() {
        assertFalse(TaskState.Success.isActive)
    }
}
