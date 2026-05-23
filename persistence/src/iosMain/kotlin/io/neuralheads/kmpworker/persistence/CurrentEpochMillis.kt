@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.neuralheads.kmpworker.persistence

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

/**
 * iOS/macOS: uses POSIX clock_gettime(CLOCK_REALTIME) to get epoch milliseconds.
 * Avoids NSDate.timeIntervalSince1970 which has Kotlin/Native 2.x binding quirks.
 */
actual fun currentEpochMillis(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME.toUInt(), ts.ptr)
    ts.tv_sec * 1_000L + ts.tv_nsec / 1_000_000L
}
