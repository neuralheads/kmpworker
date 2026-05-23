# KMPWorker Android — Consumer ProGuard/R8 Rules
#
# Applied automatically to any app that depends on kmpworker-android.

# ── Public API — keep all KMPWorker classes ────────────────────────────────────

-keep class io.neuralheads.kmpworker.** { *; }
-keepnames class io.neuralheads.kmpworker.** { *; }

# ── WorkManager — instantiated by reflection using class name ──────────────────

-keep class io.neuralheads.kmpworker.android.KmpTaskWorker { *; }
-keep class io.neuralheads.kmpworker.android.KmpTaskWorker$** { *; }

# ── Jetpack Startup Initializer ───────────────────────────────────────────────

-keep class io.neuralheads.kmpworker.android.KmpWorkerInitializer { *; }
-keep interface androidx.startup.Initializer { *; }

# ── kotlinx.serialization ──────────────────────────────────────────────────────

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep @Serializable companion objects and their serializer() method
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable classes by type (belt-and-suspenders for R8 full mode)
-keepclassmembers class io.neuralheads.kmpworker.core.TaskRequest { *; }
-keepclassmembers class io.neuralheads.kmpworker.core.Constraints { *; }
-keepclassmembers class io.neuralheads.kmpworker.core.RetryPolicy$* { *; }
-keepclassmembers class io.neuralheads.kmpworker.core.TaskType$* { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── ConnectivityManager callback (AndroidNetworkMonitor, registered dynamically) ─

-keep class android.net.ConnectivityManager$NetworkCallback { *; }
