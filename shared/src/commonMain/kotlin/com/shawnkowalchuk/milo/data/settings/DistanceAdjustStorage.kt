package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.shawnkowalchuk.milo.core.trip.MAX_ADJUST_STEPS

// Settings' distance adjustment (since 2026-10-14): how many half-percent steps every new trip's
// distance is raised or lowered by, from -10 to +10, 0 until Shawn moves it. What it does is in
// `core/trip/DistanceAdjustment.kt`. A stored value outside the range is read as the nearest end.

private val DISTANCE_ADJUST_STEPS = intPreferencesKey("distance_adjust_half_percent_steps")

internal fun Preferences.readDistanceAdjustSteps(): Int =
    (this[DISTANCE_ADJUST_STEPS] ?: 0).coerceIn(-MAX_ADJUST_STEPS, MAX_ADJUST_STEPS)

/** Stores Settings' distance adjustment, in half-percent [steps] from -10 to +10. */
suspend fun SettingsStore.setDistanceAdjustSteps(steps: Int) {
    require(steps in -MAX_ADJUST_STEPS..MAX_ADJUST_STEPS) { "Out of range: $steps steps" }
    dataStore.edit { it[DISTANCE_ADJUST_STEPS] = steps }
}
