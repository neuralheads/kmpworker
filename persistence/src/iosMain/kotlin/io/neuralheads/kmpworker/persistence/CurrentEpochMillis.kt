@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.neuralheads.kmpworker.persistence

import platform.Foundation.NSDate

actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
