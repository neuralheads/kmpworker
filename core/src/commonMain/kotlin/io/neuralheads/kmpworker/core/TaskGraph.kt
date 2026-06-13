package io.neuralheads.kmpworker.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * A directed acyclic graph (DAG) of tasks with dependency edges.
 *
 * Unlike [TaskChain] (sequential), a [TaskGraph] allows parallel execution
 * of independent tasks that converge at join points.
 *
 * ```kotlin
 * kmpWorker.graph("pipeline") {
 *     val fetch = task("fetch-data")
 *     val process = task("process")
 *     val validate = task("validate")
 *     val upload = task("upload")
 *
 *     fetch then process     // process depends on fetch
 *     fetch then validate    // validate runs in parallel with process
 *     process then upload    // upload waits for BOTH
 *     validate then upload
 * }
 * ```
 */
@ExperimentalKmpWorkerApi
data class TaskGraph(
    val id: String,
    val nodes: List<TaskRequest>,
    val edges: List<Edge>
) {
    data class Edge(val from: String, val to: String)

    /** Task IDs with no incoming edges — these start first. */
    val roots: List<String> by lazy {
        val targets = edges.map { it.to }.toSet()
        nodes.map { it.id }.filter { it !in targets }
    }

    /** Returns all task IDs that [taskId] depends on. */
    fun dependenciesOf(taskId: String): Set<String> =
        edges.filter { it.to == taskId }.map { it.from }.toSet()

    /** Returns all task IDs that depend on [taskId]. */
    fun dependentsOf(taskId: String): Set<String> =
        edges.filter { it.from == taskId }.map { it.to }.toSet()
}

/**
 * DSL builder for [TaskGraph].
 */
@ExperimentalKmpWorkerApi
class TaskGraphBuilder(private val graphId: String) {

    private val nodes = mutableListOf<TaskRequest>()
    private val edges = mutableListOf<TaskGraph.Edge>()

    /** Declares a task node in the graph. */
    fun task(taskId: String, configure: TaskRequestBuilder.() -> Unit = {}): TaskGraphNode {
        val request = TaskRequestBuilder(taskId).apply(configure).build()
        nodes.add(request)
        return TaskGraphNode(taskId)
    }

    inner class TaskGraphNode(val id: String) {
        /** Declares that [other] depends on this node. */
        infix fun then(other: TaskGraphNode): TaskGraphNode {
            edges.add(TaskGraph.Edge(from = this.id, to = other.id))
            return other
        }
    }

    internal fun build(): TaskGraph = TaskGraph(id = graphId, nodes = nodes, edges = edges)
}

/**
 * Executes a [TaskGraph] respecting dependency ordering.
 *
 * Independent nodes run in parallel via `coroutineScope`. A node only
 * starts once ALL its dependencies have reached [TaskState.Success].
 * If any node fails, the entire graph fails.
 */
@ExperimentalKmpWorkerApi
class TaskGraphExecutor(
    private val worker: KmpWorker,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    suspend fun execute(graph: TaskGraph) {
        KmpWorkerLogger.i("TaskGraphExecutor: starting graph '${graph.id}' (${graph.nodes.size} nodes)")
        TaskMonitor.emit(graph.id, TaskState.Running())

        val completed = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        val remaining = graph.nodes.map { it.id }.toMutableSet()

        while (remaining.isNotEmpty() && failed.isEmpty()) {
            val ready = remaining.filter { nodeId ->
                graph.dependenciesOf(nodeId).all { it in completed }
            }

            if (ready.isEmpty() && remaining.isNotEmpty()) {
                TaskMonitor.emit(graph.id, TaskState.Failed(
                    throwable = Exception("Cycle detected in graph '${graph.id}'"),
                    willRetry = false
                ))
                return
            }

            // Execute ready nodes in parallel, collect results after all finish
            val results = coroutineScope {
                ready.map { nodeId ->
                    async {
                        val request = graph.nodes.first { it.id == nodeId }
                        try {
                            worker.enqueue(request)
                            val state = worker.observe(nodeId).first { it.isTerminal }
                            nodeId to (state is TaskState.Success)
                        } catch (e: Exception) {
                            nodeId to false
                        }
                    }
                }.map { it.await() }
            }

            // Process results on single thread — no data race
            for ((nodeId, success) in results) {
                if (success) completed.add(nodeId) else failed.add(nodeId)
            }

            remaining.removeAll(completed)
            remaining.removeAll(failed)
        }

        if (failed.isNotEmpty()) {
            TaskMonitor.emit(graph.id, TaskState.Failed(
                throwable = Exception("Graph '${graph.id}' failed at nodes: ${failed.joinToString()}"),
                willRetry = false
            ))
        } else {
            TaskMonitor.emit(graph.id, TaskState.Success)
            KmpWorkerLogger.i("TaskGraphExecutor: graph '${graph.id}' COMPLETED")
        }
    }
}
