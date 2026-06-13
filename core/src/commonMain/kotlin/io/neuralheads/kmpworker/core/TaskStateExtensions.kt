package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

/**
 * Convenience operators for `Flow<TaskState>`.
 *
 * Usage:
 * ```kotlin
 * kmpWorker.observe("sync")
 *     .onSuccess { showSnackbar("Sync complete!") }
 *     .onFailed { error -> showError(error.throwable.message) }
 *     .onRunning { showSpinner() }
 *     .onProgress { progress, msg -> updateProgressBar(progress) }
 *     .collect()
 * ```
 */

/** Invokes [action] whenever the task enters [TaskState.Running]. */
fun Flow<TaskState>.onRunning(action: suspend () -> Unit): Flow<TaskState> =
    onEach { if (it is TaskState.Running) action() }

/** Invokes [action] when the task reaches [TaskState.Success]. */
fun Flow<TaskState>.onSuccess(action: suspend () -> Unit): Flow<TaskState> =
    onEach { if (it is TaskState.Success) action() }

/** Invokes [action] when the task reaches [TaskState.Failed]. */
fun Flow<TaskState>.onFailed(action: suspend (TaskState.Failed) -> Unit): Flow<TaskState> =
    onEach { if (it is TaskState.Failed) action(it) }

/** Invokes [action] when the task reaches [TaskState.Cancelled]. */
fun Flow<TaskState>.onCancelled(action: suspend () -> Unit): Flow<TaskState> =
    onEach { if (it is TaskState.Cancelled) action() }

/** Invokes [action] when the task reaches any terminal state. */
fun Flow<TaskState>.onTerminal(action: suspend (TaskState) -> Unit): Flow<TaskState> =
    onEach { if (it.isTerminal) action(it) }

/** Invokes [action] when the task is stopped due to timeout. */
fun Flow<TaskState>.onTimedOut(action: suspend (TaskState.TimedOut) -> Unit): Flow<TaskState> =
    onEach { if (it is TaskState.TimedOut) action(it) }

/** Invokes [action] whenever progress is reported via [TaskState.Running] with non-null progress. */
fun Flow<TaskState>.onProgress(action: suspend (progress: Float, message: String?) -> Unit): Flow<TaskState> =
    onEach { if (it is TaskState.Running && it.progress != null) action(it.progress, it.message) }

/** Filters to only terminal states. */
fun Flow<TaskState>.terminalStates(): Flow<TaskState> =
    filter { it.isTerminal }

/** Filters to only [TaskState.Failed] states. */
fun Flow<TaskState>.failures(): Flow<TaskState.Failed> =
    filterIsInstance()

/** Filters to only [TaskState.Success] states. */
fun Flow<TaskState>.successes(): Flow<TaskState.Success> =
    filterIsInstance()

/** Filters to only [TaskState.Running] states that carry progress info. */
fun Flow<TaskState>.progressUpdates(): Flow<TaskState.Running> =
    filterIsInstance<TaskState.Running>().filter { it.progress != null }
