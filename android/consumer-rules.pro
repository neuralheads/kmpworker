# KMPWorker ProGuard consumer rules
# These rules are automatically applied to any app that uses kmpworker-android.

# Keep all KMPWorker public API classes
-keep class io.neuralheads.kmpworker.** { *; }
-keepnames class io.neuralheads.kmpworker.** { *; }

# Keep WorkManager worker class names (required for WorkManager to instantiate them)
-keep class io.neuralheads.kmpworker.android.KmpTaskWorker { *; }

# Keep coroutine internals referenced from workers
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
