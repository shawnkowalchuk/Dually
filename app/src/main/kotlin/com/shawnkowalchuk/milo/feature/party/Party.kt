package com.shawnkowalchuk.milo.feature.party

import com.shawnkowalchuk.milo.core.party.partyOwedNow
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import java.time.ZoneId

/** Why MilO's one-year party is playing over Home. */
enum class PartyShowing {
    /** A trip started today, on the day of the party, and Home owes the mascot's party for it. */
    THE_DAY,

    /** It was asked for on the What's new screen, for a look at it on any day. */
    ON_DEMAND,
}

/**
 * Everything the party over Home goes by, at one moment.
 *
 * @property read true once the settings have been read. Until then nothing is known, and the
 * greeting waits ([hasTheScreen]).
 * @property owedSinceMs when the newest trip started that the party is owed for, as the settings
 * have it, or null.
 * @property shownForMs the [owedSinceMs] this start of MilO has already dealt with: shown, or
 * tried and given up. It is not shown a second time if it could not be taken out of the
 * settings.
 * @property asked true while the party has been asked for on demand and not been played.
 * @property watching true while Home is in front and can show the party: MilO is on the screen,
 * Home is drawn ([homeIsDrawn]) and the phone's animations are not off.
 * @property owedToday whether [owedSinceMs] still counts, as of the last look ([settled]).
 * @property showing the party that is playing, or null.
 */
data class PartyFacts(
    val read: Boolean = false,
    val owedSinceMs: Long? = null,
    val shownForMs: Long? = null,
    val asked: Boolean = false,
    val watching: Boolean = false,
    val owedToday: Boolean = false,
    val showing: PartyShowing? = null,
) {
    /**
     * Whether a party has the screen, or is about to: the opening greeting then stands back and
     * keeps its turn (Shawn's party "plays instead"). Null while the settings are being read:
     * the greeting waits for the answer, as it waits for its own turn to be read.
     */
    val hasTheScreen: Boolean?
        get() = if (read) showing != null || owedToday || asked else null
}

/**
 * Whether Home is drawn, for a party to play over: it is the screen on top, and the first
 * start's page, which is drawn in place of every screen, has been left with OK.
 *
 * **Setup's Done button need not have been pressed** (unlike for the greeting, which is for a
 * MilO that has been set up): whoever leaves Setup by another way has an ordinary Home from
 * then on, perhaps for good, and the party is for a year of trips, not for a button.
 *
 * @param firstRun how far the first start has got, or null while that is being read: nothing
 * is drawn then.
 */
fun homeIsDrawn(onTop: Boolean, firstRun: FirstRunStage?): Boolean =
    onTop && firstRun != null && firstRun != FirstRunStage.INTRO

/**
 * The facts after a look at the clock: whether the party is still owed, and which party plays.
 *
 * - **Owed** means owed today ([partyOwedNow]: on the day itself only, so nothing left over is
 *   shown after midnight) and not dealt with already.
 * - **Nothing plays while Home is not watching.** A party that is playing ends when Home stops
 *   watching: a press left Home, or MilO was put away.
 * - **A party that is playing is not changed under itself:** not when what it was owed for is
 *   taken out of the settings, which is its own doing, and not by a request on demand.
 * - **The day's party comes before one on demand.**
 *
 * @param nowMs the moment of the look, from MilO's own clock.
 */
fun PartyFacts.settled(nowMs: Long, zone: ZoneId): PartyFacts {
    val owed = owedSinceMs != shownForMs && partyOwedNow(owedSinceMs, nowMs, zone)
    val plays =
        when {
            !watching -> null
            showing != null -> showing
            owed -> PartyShowing.THE_DAY
            asked -> PartyShowing.ON_DEMAND
            else -> null
        }
    return copy(owedToday = owed, showing = plays)
}
