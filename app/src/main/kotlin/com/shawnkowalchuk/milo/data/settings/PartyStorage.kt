package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey

// What Home still owes of MilO's one-year party (2026-10-10): the moment the newest trip started
// on the day of the party that the mascot's party has not been shown for. The trip service
// stores it at the trip's start; Home takes it out when the mascot is drawn. It is kept here,
// not in memory, because the party is also owed to a MilO that is opened hours after the trip,
// in another process. Whether it still counts is decided where it is read
// (`core/party/PartyPop.kt`, `partyOwedNow`): only on the day itself.
// Not a setting Shawn chooses, and not in an export file. Android's backup carries it with the
// rest of the settings file, and a day later it means nothing.

private val PARTY_OWED_SINCE_MS = longPreferencesKey("party_owed_since_ms")

/**
 * The moment stored, or null if nothing is owed.
 * A negative one, which no setter writes, is none.
 */
internal fun Preferences.readPartyOwedSinceMs(): Long? {
    val stored = this[PARTY_OWED_SINCE_MS] ?: return null
    return stored.takeIf { it >= 0 }
}

/** Stores that the party is owed for a trip that started at [sinceMs]; null stores that none is. */
suspend fun SettingsStore.setPartyOwedSinceMs(sinceMs: Long?) {
    require(sinceMs == null || sinceMs >= 0) { "A moment cannot be negative: $sinceMs ms" }
    dataStore.edit { it.setOrRemove(PARTY_OWED_SINCE_MS, sinceMs) }
}

/**
 * Takes out what is owed, if it is still [shownForMs]: the party was shown for that trip. A
 * trip that started since has stored a later moment, and that one stays owed.
 */
suspend fun SettingsStore.clearPartyOwed(shownForMs: Long) {
    dataStore.edit { stored ->
        if (stored[PARTY_OWED_SINCE_MS] == shownForMs) stored.remove(PARTY_OWED_SINCE_MS)
    }
}

/**
 * Whether the party's pop is heard at all. It has no switch of its own: it is silent when both
 * of MilO's sounds are switched off in Settings, and heard when either is on.
 */
val MiloSettings.partyPopHeard: Boolean
    get() = TripSound.entries.any { sound(it).enabled }
