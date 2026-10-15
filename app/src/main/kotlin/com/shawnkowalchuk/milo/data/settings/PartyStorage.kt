package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalDate

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

// The last day a party was held: stored at the first sound of every trip that has the party,
// on its date or on the day that makes up for it (since 2026-10-14). A party with no trip on its
// date is held on the first day with one in the week after, unless one was held since its date
// (`partyDateToday` in `core/party/PartyDay.kt`). As a day since 1970, read in the phone's zone.
// Not a setting, and not in an export file.
private val PARTY_HELD_ON_EPOCH_DAY = longPreferencesKey("party_held_on_epoch_day")

internal fun Preferences.readPartyHeldOn(): LocalDate? = this[PARTY_HELD_ON_EPOCH_DAY]?.takeIf {
    it in
        LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()
}
    ?.let(LocalDate::ofEpochDay)

/** Stores that a party was held on [day]. */
suspend fun SettingsStore.setPartyHeldOn(day: LocalDate) {
    dataStore.edit { it[PARTY_HELD_ON_EPOCH_DAY] = day.toEpochDay() }
}

/**
 * Whether the party's pop is heard at all. It has no switch of its own: it is silent when both
 * of MilO's sounds are switched off in Settings, and heard when either is on.
 */
val MiloSettings.partyPopHeard: Boolean
    get() = TripSound.entries.any { sound(it).enabled }
