package com.shawnkowalchuk.milo.core.party

import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.ZoneId

// Who plays the party's pop, and what Home still owes. On the day of the party ([isPartyDay])
// every trip that starts has two things coming: the pop of a party popper, wherever the phone
// is, and the mascot's party over Home, once, the next time Home is in front that day (Shawn,
// 2026-10-10: "Sound always, animation in MilO"). Plain Kotlin with unit tests.

/** How the party's pop is played at a trip's start. */
enum class PopAtTripStart {
    /** It is not the day of the party: the trip's sounds are those of every other day. */
    NOT_TODAY,

    /** The trip service plays the pop at once, in place of the trip's first sound. */
    AT_ONCE,

    /**
     * Home is in front and plays the party now: the pop belongs to the popper, and is played by
     * the animation on the frame the popper goes off, so that picture and sound agree. The trip
     * service plays no pop, unless the animation never begins ([PopHandover]).
     */
    WITH_THE_ANIMATION,
}

/**
 * Who plays the pop of a trip that is starting.
 *
 * @param partyDay whether the trip starts on the day of the party.
 * @param homeWatching whether Home is in front this moment and can show the party: MilO is on
 * the screen with Home on top, and the phone's animations are not switched off.
 */
fun popAtTripStart(partyDay: Boolean, homeWatching: Boolean): PopAtTripStart = when {
    !partyDay -> PopAtTripStart.NOT_TODAY
    homeWatching -> PopAtTripStart.WITH_THE_ANIMATION
    else -> PopAtTripStart.AT_ONCE
}

/**
 * The pop of a trip's start while it is on its way from the trip service to the animation.
 *
 * The service leaves it ([leave]) when Home is in front. Whoever plays it takes it first
 * ([take]), and only one can: the animation as the mascot is first drawn, or the service a
 * moment later if no animation began (Home was left in that instant, or the pictures could not
 * be read). **So a trip's start is never without its pop, and never has two.**
 *
 * Safe from any thread: the service and the screens are on different ones.
 */
class PopHandover {
    private var left = false

    /** The trip service leaves the pop to the animation. */
    @Synchronized
    fun leave() {
        left = true
    }

    /**
     * Takes the pop that was left, if one was and nobody has taken it.
     *
     * @return true for the one caller who now has to see that it is played.
     */
    @Synchronized
    fun take(): Boolean {
        val had = left
        left = false
        return had
    }
}

/**
 * Whether Home still owes the party for a trip that started today.
 *
 * - **Only on the day itself:** what was owed when midnight came is not shown the day after.
 * - **A moment that lies ahead of now is no debt.** MilO's clock does not go back by itself; a
 *   stored moment that is later than now was written by another installation or under a date
 *   that was wrong, and is not to be believed (ADR-006, decision 5).
 * - **Several trips since the last showing are one debt:** each start moves [owedSinceMs] to
 *   itself, and one showing settles it.
 *
 * @param owedSinceMs when the newest trip started that the party has not been shown for, or
 * null if nothing is owed. It is only ever stored on the day of a party.
 * @param nowMs the moment asked about, from MilO's own clock.
 * @param zone the zone the phone is in now.
 */
fun partyOwedNow(owedSinceMs: Long?, nowMs: Long, zone: ZoneId): Boolean {
    if (owedSinceMs == null || owedSinceMs > nowMs) return false
    return localDateOf(owedSinceMs, zone) == localDateOf(nowMs, zone)
}
