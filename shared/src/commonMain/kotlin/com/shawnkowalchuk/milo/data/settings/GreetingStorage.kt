package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey

// How many greetings this phone has been shown since the greetings take turns (2026-10-10): the
// wave, the moonwalk, the wave, and so on. Which one is next is worked out from the count
// (`feature/greeting/GreetingTurn.kt`), so that it is remembered from one start to the next.
// Not a setting Shawn chooses, and not in an export file: it is this phone's. Android's backup
// carries it with the rest of the settings file.

private val GREETINGS_SHOWN = intPreferencesKey("greetings_shown")

/**
 * The count stored, or null if none ever was: on a phone that had MilO before the greetings
 * took turns, and on a new one until its first start. A negative count, which no setter can
 * write, is read as none.
 */
internal fun Preferences.readGreetingsShown(): Int? = this[GREETINGS_SHOWN]?.takeIf { it >= 0 }

/** Stores how many greetings this phone has been shown. */
suspend fun SettingsStore.setGreetingsShown(count: Int) {
    require(count >= 0) { "A count of greetings cannot be negative: $count" }
    dataStore.edit { it[GREETINGS_SHOWN] = count }
}
