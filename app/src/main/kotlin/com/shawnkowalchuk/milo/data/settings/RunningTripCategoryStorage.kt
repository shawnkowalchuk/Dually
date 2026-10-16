package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.schedule.TripCategory

// What Shawn chose on Home for the trip being recorded (since 2026-10-16: "when a trip is
// started on the home page can we have it show if it is business or personal at the top in the
// start trip card. and if you hit the button it will toggle"). Stored as "tripId:CATEGORY" and
// taken out when that trip closes; one for another trip means nothing. Not a setting, and not
// in an export file. An entry that is not of that form is read as none.

private val RUNNING_TRIP_CATEGORY = stringPreferencesKey("running_trip_category")

private const val SEPARATOR = ':'

/** The category Shawn chose for the trip with [tripId] while it was recorded. */
data class RunningTripCategory(val tripId: Long, val category: TripCategory)

internal fun Preferences.readRunningTripCategory(): RunningTripCategory? {
    val stored = this[RUNNING_TRIP_CATEGORY] ?: return null
    val id = stored.substringBefore(SEPARATOR).toLongOrNull() ?: return null
    val category =
        TripCategory.entries.firstOrNull { it.name == stored.substringAfter(SEPARATOR) }
            ?: return null
    return RunningTripCategory(id, category)
}

/** Stores [category] as chosen for the trip being recorded, [tripId]; null takes it out. */
suspend fun SettingsStore.setRunningTripCategory(tripId: Long, category: TripCategory?) {
    dataStore.edit { stored ->
        if (category == null) {
            stored.remove(RUNNING_TRIP_CATEGORY)
        } else {
            stored[RUNNING_TRIP_CATEGORY] = "$tripId$SEPARATOR${category.name}"
        }
    }
}
