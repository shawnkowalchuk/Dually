package com.shawnkowalchuk.milo.core.party

import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.LocalDate
import java.time.ZoneId

// The day of MilO's party (Shawn, 2026-10-10: "a one year use animation. After the person has
// used the app for a year this one will come up that day every time he starts a trip"; "the
// aniversery animation is only tobe used on that day for starting trips"). He chose that a year
// counts from the first recorded trip, not from the day MilO was installed, so that it travels
// with an export and an import, and that it comes every year on that date. Plain Kotlin with
// unit tests: nothing here reads a clock or storage.

/**
 * When one stored trip started, and how it came to be stored: all the party's day is worked out
 * from. Named as the trip's columns are, because the database fills it from them.
 *
 * @param startedAtMs when the trip started, as it is stored now.
 * @param recordedStartedAtMs when MilO recorded it as starting, if its times were changed by
 * hand since; null on a trip that was never edited.
 * @param addedByHand true for a trip Shawn typed in because MilO missed it.
 */
data class TripStart(
    val startedAtMs: Long,
    val recordedStartedAtMs: Long?,
    val addedByHand: Boolean,
)

/**
 * When the first trip MilO recorded started, or null while it has recorded none.
 *
 * **Which trips count:** every trip MilO recorded itself that storage still holds, whatever
 * became of it: finished, discarded as too short or as a false start, deleted by Shawn, or still
 * open. Each of them is a day MilO was in use.
 *
 * **Which do not: trips typed in by hand.** He can give such a trip any date, also one from
 * before he had MilO, and a slip of the year would move the party. For the same reason a trip
 * whose times he changed counts by the start MilO recorded, not by the one he typed.
 */
fun firstRecordedStartMs(trips: List<TripStart>): Long? = trips
    .filterNot { it.addedByHand }
    .minOfOrNull { it.recordedStartedAtMs ?: it.startedAtMs }

/**
 * Whether [nowMs] falls on the day of MilO's party: the day of the year its first recorded trip
 * started on, one or more years later.
 *
 * - **Both days are read in [zone],** the zone the phone is in now: the first trip's day is the
 *   day the Trips screen shows for it today, and "today" is the day every screen shows.
 * - **A first trip on 29 February** has its party on 28 February in a year without one, and on
 *   the 29th, not the 28th, in a year with one.
 * - **Not on the first trip's own day,** and on no day before it: at least a year lies between.
 *
 * @param firstRecordedStartMs see [firstRecordedStartMs]; null means no party at all.
 * @param nowMs the moment asked about, from MilO's own clock (ADR-006): a phone whose date is
 * set a day ahead for a few seconds has no party a day early.
 */
fun isPartyDay(firstRecordedStartMs: Long?, nowMs: Long, zone: ZoneId): Boolean {
    if (firstRecordedStartMs == null) return false
    val today = localDateOf(nowMs, zone)
    return dateOfParty(localDateOf(firstRecordedStartMs, zone), today.year) == today
}

/**
 * How many days after its date a party that had no trip on the day can still be held (Shawn,
 * 2026-10-14: "When the yearly anniversary is active if there isnt a active trip on that day the
 * next day it will pop up"; he chose "the next trip, within 7 days").
 */
const val MAKE_UP_DAYS = 7L

/**
 * The date of the party that is held today, or null on a day that holds none.
 *
 * - **On the date itself** ([isPartyDay]), as ever: every trip that day.
 * - **On a make-up day:** one of the [MAKE_UP_DAYS] days after the date, when no party was held
 *   from the date up to yesterday. The first such day with a trip holds the party, all that day,
 *   exactly as the date would have; after it, and after the last of the days, none until the
 *   next year. Shawn chose "same as the day" for it.
 *
 * Whether a party was held is what the trip service stored at a trip's first sound
 * ([lastHeldOn]), not whether a trip was stored: a trip that never had its first sound (a false
 * start) held no party, and leaves the make-up open.
 *
 * @param lastHeldOn the last day a party was held, or null if none ever was on this phone.
 */
fun partyDateToday(
    firstRecordedStartMs: Long?,
    lastHeldOn: LocalDate?,
    nowMs: Long,
    zone: ZoneId,
): LocalDate? {
    if (firstRecordedStartMs == null) return null
    val first = localDateOf(firstRecordedStartMs, zone)
    val today = localDateOf(nowMs, zone)
    if (isPartyDay(firstRecordedStartMs, nowMs, zone)) return today
    // A date late in December is made up early in January: last year's date is looked at too.
    return listOf(today.year, today.year - 1)
        .mapNotNull { year -> dateOfParty(first, year) }
        .firstOrNull { date ->
            today > date &&
                today <= date.plusDays(MAKE_UP_DAYS) &&
                (lastHeldOn == null || lastHeldOn !in date..today.minusDays(1))
        }
}

/**
 * The party's date in [year], or null before a year has passed since [first]. plusYears gives
 * 28 February for a 29 February in a year that has none.
 */
private fun dateOfParty(first: LocalDate, year: Int): LocalDate? {
    val years = year - first.year
    return if (years >= 1) first.plusYears(years.toLong()) else null
}
