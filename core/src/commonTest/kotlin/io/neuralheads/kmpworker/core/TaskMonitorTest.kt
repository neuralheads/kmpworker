package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskMonitorTest {

    @Test
    fun `observe receives emitted state`() = runTest {
        TaskMonitor.reset()
        val flow = TaskMonitor.observe("monitor-task")

        TaskMonitor.emit("monitor-task", TaskState.Running)
        val state = flow.first()
        assertEquals(TaskState.Running, state)
    }

    @Test
    fun `observe filters by task id`() = runTest {
        TaskMonitor.reset()
        val flowA = TaskMonitor.observe("task-a")

        TaskMonitor.emit("task-b", TaskState.Success)
        TaskMonitor.emit("task-a", TaskState.Running)

        val state = flowA.first()
        assertEquals(TaskState.Running, state)
    }

    @Test
    fun `tryEmit returns true on success`() {
        TaskMonitor.reset()
        val result = TaskMonitor.tryEmit("task-x", TaskState.Scheduled)
        assertEquals(true, result)
    }
}
