package io.neuralheads.kmpworker.android

import io.neuralheads.kmpworker.core.TelemetryCollector

/**
 * Static bridge for [TelemetryCollector] access from [KmpTaskWorker].
 *
 * WorkManager instantiates [KmpTaskWorker] via reflection, so it cannot
 * receive constructor-injected dependencies. This object holds the
 * collector reference set during [AndroidKmpWorker] initialization.
 */
internal object TelemetryBridge {
    var collector: TelemetryCollector? = null
}
