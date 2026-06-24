package io.neuralheads.kmpworker.core

import kotlinx.serialization.Serializable

/**
 * Platform conditions that must be satisfied before a task can execute.
 *
 * Platform support matrix:
 * | Constraint         | Android | iOS     |
 * |--------------------|---------|---------|
 * | requiresInternet   | ✅       | Partial |
 * | requiresCharging   | ✅       | ❌       |
 * | batteryNotLow      | ✅       | ❌       |
 *
 * On iOS, only internet connectivity is partially respected via BGProcessingTaskRequest.
 */
@Serializable
data class Constraints(
    /** Task should only run when a network connection is available. */
    val requiresInternet: Boolean = false,

    /** Task should only run when the device is charging. Android only. */
    val requiresCharging: Boolean = false,

    /** Task should only run when battery level is not critically low. Android only. */
    val batteryNotLow: Boolean = false,

    /** Task should only run when the device is idle. Android only (API 23+). */
    val requiresDeviceIdle: Boolean = false,

    /**
     * Content URIs to observe for changes. Android only.
     * When any of these URIs change, the task will be triggered.
     *
     * Example: `contentUris = listOf("content://media/external/images/media")`
     */
    val contentUris: List<String> = emptyList(),

    /** Task should only run when on an unmetered network (e.g. Wi-Fi). */
    val requiresUnmeteredNetwork: Boolean = false,

    /** Task should only run when on a non-roaming network. */
    val requiresNonRoamingNetwork: Boolean = false
)
