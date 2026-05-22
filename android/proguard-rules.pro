# Add project specific ProGuard rules here.
# See: http://developer.android.com/guide/developing/tools/proguard.html

# Keep KMPWorker task worker (WorkManager needs to find it by class name)
-keep class io.neuralheads.kmpworker.android.KmpTaskWorker
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# Keep KMPWorker public API
-keep public class io.neuralheads.kmpworker.** {
    public *;
}
