package com.shawnkowalchuk.milo.platform.driving

import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity

// Android's reports of driving, read from Play services' Activity Recognition. The rules they go
// to are shared with the iPhone (DrivingAlertRules.kt in :shared, since 2026-11-13).

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Reads one event of Play services' Activity Recognition as a [VehicleReport].
 *
 * @param activityType the event's `DetectedActivity` type. Only vehicles are asked for.
 * @param transitionType the event's `ActivityTransition` type: entering or leaving.
 * @param eventNanos when the phone noticed it, and [nowNanos] the present, both on the clock
 * that counts from boot, which never jumps.
 * @return null for anything that is not a vehicle being entered or left. It is dropped, not
 * guessed at.
 */
fun vehicleReport(
    activityType: Int,
    transitionType: Int,
    eventNanos: Long,
    nowNanos: Long,
): VehicleReport? {
    if (activityType != DetectedActivity.IN_VEHICLE) return null
    val entered =
        when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> true
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> false
            else -> return null
        }
    return VehicleReport(entered, ageMs = (nowNanos - eventNanos) / NANOS_PER_MILLI)
}
