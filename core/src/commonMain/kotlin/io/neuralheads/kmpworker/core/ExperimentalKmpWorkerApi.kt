package io.neuralheads.kmpworker.core

/**
 * Marks an API as experimental in KMPWorker.
 *
 * Experimental APIs may change without notice in minor or patch releases.
 * Opt in at the call site:
 * ```kotlin
 * @OptIn(ExperimentalKmpWorkerApi::class)
 * fun myFunction() { ... }
 * ```
 * Or at the file level:
 * ```kotlin
 * @file:OptIn(ExperimentalKmpWorkerApi::class)
 * ```
 */
@RequiresOptIn(
    message = "This KMPWorker API is experimental and may change without notice.",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS
)
annotation class ExperimentalKmpWorkerApi
