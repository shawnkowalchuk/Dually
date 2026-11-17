package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.party.PopHandover
import com.shawnkowalchuk.milo.core.party.firstRecordedStartMs
import com.shawnkowalchuk.milo.core.party.partyDateToday
import com.shawnkowalchuk.milo.core.party.partyOwedNow
import com.shawnkowalchuk.milo.core.party.popAtTripStart
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.clearPartyOwed
import com.shawnkowalchuk.milo.data.settings.partyPopHeard
import com.shawnkowalchuk.milo.data.settings.setPartyHeldOn
import com.shawnkowalchuk.milo.data.settings.setPartyOwedSinceMs
import com.shawnkowalchuk.milo.data.trip.TripRepository

/**
 * Dually's one-year party on the iPhone (2026-11-16), by Android's rules (`OneYearParty`): one
 * year after the first recorded trip, and on that date every year after (or the first day with a
 * trip in the week after it), every trip that starts begins with the pop of a party popper in
 * place of its first sound, and once for the trips of that day the mascot holds his party over
 * Home. The pop of a trip that starts while Home is watching is left to the animation
 * ([PopHandover]): whoever takes it plays it, so a trip's start is never without its pop and
 * never has two.
 *
 * @param clock wall-clock milliseconds.
 */
class IosParty internal constructor(
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    /** Whether Home is on the screen and could play the party now, told by Swift. */
    var homeWatching: Boolean = false

    private val handover = PopHandover()

    /** The party a trip's start owes and the animation has not shown, as this start knows it. */
    private var shownForMs: Long? = null

    /**
     * What a trip's first sound is today: its usual sound, the pop at once, or the pop left to
     * the animation. On a party day it stores that the party is owed, and the day it was held.
     */
    suspend fun atTripStart(): PopAtTripStart = try {
        look()
    } catch (failure: Exception) {
        eventLog.add(
            clock(),
            EventCategory.ERROR,
            "Dually could not tell whether today is the day of its party",
            failure.stackTraceToString(),
        )
        PopAtTripStart.NOT_TODAY
    }

    private suspend fun look(): PopAtTripStart {
        val nowMs = clock()
        val zoneNow = zone()
        val firstMs = firstRecordedStartMs(trips.findTripStarts())
        val lastHeldOn = runCatching { settings.current().partyHeldOn }.getOrNull()
        val date =
            partyDateToday(firstMs, lastHeldOn, nowMs, zoneNow) ?: return PopAtTripStart.NOT_TODAY
        val today = localDateOf(nowMs, zoneNow)
        val stored =
            try {
                settings.setPartyOwedSinceMs(nowMs)
                if (lastHeldOn != today) settings.setPartyHeldOn(today)
                true
            } catch (notStored: Exception) {
                eventLog.add(
                    clock(),
                    EventCategory.ERROR,
                    "Dually could not store that its mascot's party is owed",
                    notStored.stackTraceToString(),
                )
                false
            }
        // A party that could not be stored is not shown, so its popper cannot make the sound.
        val pop = popAtTripStart(partyDay = true, homeWatching = homeWatching && stored)
        if (pop == PopAtTripStart.WITH_THE_ANIMATION) handover.leave()
        val since = firstMs?.let { localDateOf(it, zoneNow) }
        val how =
            if (pop == PopAtTripStart.WITH_THE_ANIMATION) {
                "Home is in front: the mascot's party plays there, and its popper makes the pop"
            } else {
                "The pop takes the place of this trip's first sound, and the mascot's party " +
                    "plays when Home is next in front today"
            }
        val what =
            if (date == today) {
                "Dually's party: a trip starts on the day of its first recorded trip ($since)"
            } else {
                "Dually's party of $date, held today: no trip had its first sound on that day, " +
                    "and this is the first day with one since (its first recorded trip: $since)"
            }
        eventLog.add(nowMs, EventCategory.SERVICE, "$what. $how")
        return pop
    }

    /**
     * When the party is owed and has not been shown, in epoch milliseconds: Home plays it now.
     * Null when nothing is owed today.
     */
    suspend fun owedNow(): Long? {
        val owed = runCatching { settings.current().partyOwedSinceMs }.getOrNull() ?: return null
        if (owed == shownForMs) return null
        return owed.takeIf { partyOwedNow(it, clock(), zone()) }
    }

    /**
     * The party of [forMs] is on the screen: not owed again for these trips. Returns whether
     * the pop of a trip's start was left to it, which it then plays at its pop or, if it never
     * pops, when it ends.
     */
    suspend fun shown(forMs: Long): Boolean {
        shownForMs = forMs
        val owesPop = handover.take()
        try {
            settings.clearPartyOwed(forMs)
        } catch (notStored: Exception) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "Dually could not store that its mascot's party has been shown",
                notStored.stackTraceToString(),
            )
        }
        return owesPop
    }

    /** The pop left to an animation that never began: the trip's start takes it back. */
    fun takePop(): Boolean = handover.take()

    /** Whether the party's pop is heard: not when both of Dually's sounds are switched off. */
    suspend fun popHeard(): Boolean = runCatching {
        settings.current().partyPopHeard
    }.getOrDefault(true)

    /** A picture of the party could not be read: said in the Log in Android's words. */
    suspend fun unreadable() = eventLog.add(
        clock(),
        EventCategory.ERROR,
        "Dually's mascot did not hold his party: a picture of him could not be read",
    )
}
