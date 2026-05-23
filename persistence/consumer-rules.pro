# KMPWorker Persistence — Consumer ProGuard/R8 Rules
#
# Applied automatically to apps depending on kmpworker-persistence.

# ── SQLDelight generated code ──────────────────────────────────────────────────

-keep class io.neuralheads.kmpworker.persistence.** { *; }

# Keep SQLDelight Database interface and its implementation
-keep interface io.neuralheads.kmpworker.persistence.KmpWorkerDatabase { *; }
-keep class io.neuralheads.kmpworker.persistence.KmpWorkerDatabaseImpl { *; }

# Keep TaskRepository and its implementations (used via interface)
-keep interface io.neuralheads.kmpworker.persistence.TaskRepository { *; }
-keep class io.neuralheads.kmpworker.persistence.SqlDelightTaskRepository { *; }
-keep class io.neuralheads.kmpworker.persistence.SqlDelightEventStore { *; }
-keep class io.neuralheads.kmpworker.persistence.SqlDelightChainRepository { *; }

# Keep database factory (used reflectively by SQLDelight drivers)
-keep class io.neuralheads.kmpworker.persistence.KmpWorkerDatabaseFactory { *; }

# ── SQLDelight runtime ─────────────────────────────────────────────────────────

-keep class app.cash.sqldelight.** { *; }
-keep interface app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
