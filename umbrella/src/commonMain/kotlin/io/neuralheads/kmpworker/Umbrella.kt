/**
 * KMPWorker umbrella module.
 *
 * Add a single dependency to get all KMPWorker artifacts:
 * ```kotlin
 * // build.gradle.kts (shared KMP module)
 * commonMain.dependencies {
 *     implementation("com.neuralheads:kmpworker:0.1.0-alpha02")
 * }
 * commonTest.dependencies {
 *     implementation("com.neuralheads:kmpworker-testing:0.1.0-alpha02")
 * }
 * ```
 *
 * This pulls in: core, android, ios, scheduler, persistence, and offline queue.
 * Add kmpworker-testing separately in commonTest for test utilities.
 */
package io.neuralheads.kmpworker
