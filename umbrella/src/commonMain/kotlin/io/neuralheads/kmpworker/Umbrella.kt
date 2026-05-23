/**
 * KMPWorker umbrella module.
 *
 * Add a single dependency to get all KMPWorker artifacts:
 * ```kotlin
 * // build.gradle.kts (shared KMP module)
 * commonMain.dependencies {
 *     implementation("com.neuralheads:kmpworker:0.1.0-alpha01")
 * }
 * ```
 *
 * This pulls in: core, scheduler, persistence, queue, and the
 * platform-specific android / ios modules automatically.
 */
package io.neuralheads.kmpworker
