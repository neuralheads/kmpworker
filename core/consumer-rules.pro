# KMPWorker Core — Consumer ProGuard/R8 Rules
#
# These rules are automatically applied to any app that depends on kmpworker-core.
# They prevent R8 from stripping or renaming @Serializable classes and key singletons.

# ── kotlinx.serialization ──────────────────────────────────────────────────────

# Keep @Serializable data classes and their generated $serializer companions
-keep class io.neuralheads.kmpworker.core.TaskRequest { *; }
-keep class io.neuralheads.kmpworker.core.TaskRequest$$serializer { *; }
-keep class io.neuralheads.kmpworker.core.Constraints { *; }
-keep class io.neuralheads.kmpworker.core.Constraints$$serializer { *; }
-keep class io.neuralheads.kmpworker.core.TaskType { *; }
-keep class io.neuralheads.kmpworker.core.TaskType$* { *; }
-keep class io.neuralheads.kmpworker.core.TaskType$OneTime { *; }
-keep class io.neuralheads.kmpworker.core.TaskType$Periodic { *; }
-keep class io.neuralheads.kmpworker.core.TaskType$ExactTime { *; }
-keep class io.neuralheads.kmpworker.core.RetryPolicy { *; }
-keep class io.neuralheads.kmpworker.core.RetryPolicy$* { *; }
-keep class io.neuralheads.kmpworker.core.RetryPolicy$None { *; }
-keep class io.neuralheads.kmpworker.core.RetryPolicy$Linear { *; }
-keep class io.neuralheads.kmpworker.core.RetryPolicy$Exponential { *; }
-keep class io.neuralheads.kmpworker.core.TaskChain { *; }
-keep class io.neuralheads.kmpworker.core.ChainProgress { *; }

# Keep generated serializer lookup (used by kotlinx.serialization.serializer<T>())
-keepclassmembers class io.neuralheads.kmpworker.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Singletons (object declarations) ──────────────────────────────────────────

-keep class io.neuralheads.kmpworker.core.TaskRegistry { *; }
-keep class io.neuralheads.kmpworker.core.TaskMonitor { *; }
-keep class io.neuralheads.kmpworker.core.RetryEngine { *; }
-keep class io.neuralheads.kmpworker.core.KmpWorkerLogger { *; }
-keep class io.neuralheads.kmpworker.core.KmpWorkerConfig { *; }

# ── Enums ──────────────────────────────────────────────────────────────────────

-keep enum io.neuralheads.kmpworker.core.TaskPriority { *; }
-keep enum io.neuralheads.kmpworker.core.KmpWorkerLogger$Level { *; }

# ── Interface implementations (for dependency injection / reflection) ──────────

-keep interface io.neuralheads.kmpworker.core.KmpWorker { *; }
-keep interface io.neuralheads.kmpworker.core.EventStore { *; }
-keep interface io.neuralheads.kmpworker.core.ChainRepository { *; }
