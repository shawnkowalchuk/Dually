package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.YearMonth
import java.time.format.DateTimeParseException

// The sent months whose GPS points Shawn chose to keep (2026-10-14): "Keep" on the Report
// screen's tile that offers to delete them. The tile is not offered again for such a month;
// Settings can still delete the points of every sent month, kept ones included. One entry of a
// string set per month, as "2026-09". An entry that is not a month is skipped, never guessed at.
// Not a setting Shawn sets on the Settings screen, and not in an export file. Android's backup
// carries it with the rest of the settings file.

private val POINTS_KEPT_MONTHS = stringSetPreferencesKey("points_kept_months")

internal fun Preferences.readPointsKeptMonths(): Set<YearMonth> =
    this[POINTS_KEPT_MONTHS].orEmpty().mapNotNull { entry ->
        try {
            YearMonth.parse(entry)
        } catch (notAMonth: DateTimeParseException) {
            null
        }
    }.toSet()

/** Stores that the GPS points of [month] are kept: its tile is not offered again. */
suspend fun SettingsStore.keepPointsOf(month: YearMonth) {
    dataStore.edit { stored ->
        stored[POINTS_KEPT_MONTHS] = stored[POINTS_KEPT_MONTHS].orEmpty() + month.toString()
    }
}
