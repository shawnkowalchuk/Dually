package com.shawnkowalchuk.milo.platform.party

import com.shawnkowalchuk.milo.core.party.PopAtTripStart
import com.shawnkowalchuk.milo.core.party.PopHandover
import com.shawnkowalchuk.milo.core.party.TripStart
import com.shawnkowalchuk.milo.core.party.firstRecordedStartMs
import com.shawnkowalchuk.milo.core.party.partyDateToday
import com.shawnkowalchuk.milo.core.party.popAtTripStart
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.clearPartyOwed
import com.shawnkowalchuk.milo.data.settings.setPartyHeldOn
import com.shawnkowalchuk.milo.data.settings.setPartyOwedSinceMs
import java.io.IOException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long the trip service waits to learn whether today is the day of the party before it
 * plays the trip's sounds as on any day. The answer is one read of the stored trips, a few
 * milliseconds; the limit is for a database that something else holds.
 */
private const val LOOK_LIMIT_MS = 1_000L

/**
 * MilO's one-year party, apart from its drawing (Shawn, 2026-10-10: "After the person has used
 * the app for a year this one will come up that day every time he starts a trip"; "Sound
 * always, animation in MilO"). One object for the whole app, which stands between the trip
 * service and Home:
 *
 * - **The trip service asks it at every trip's first sound** ([atTripStart]) whether today is
 *   the day, and is told how the pop is played. On the day it also stores that Home owes the
 *   mascot's party ([owedSinceMs]).
 * - **Home says whether it is in front** ([homeWatching]), plays the party when one is owed,
 *   and says when it has ([shown]).
 * - **The pop is handed from the one to the other** ([takePop]) when Home is in front as the
 *   trip starts: the popper makes the sound, and nobody plays it twice.
 * - **A press on Settings' version asks for the party on demand** ([askFor]), for a look at it
 *   on any day. That stores nothing.
 *
 * **It can touch no trip.** It reads when the stored trips started, reads and writes two values
 * of its own in the settings file (what Home owes, and the last day a party was held), and
 * writes lines to the event log. A failure in it is caught here: the trip's sounds are then the
 * ones of every other day, and the Log says why. Left alone, a failure would end the process,
 * and the trip service runs in it.
 *
 * @param tripStarts when every stored trip started (`TripRepository.findTripStarts`).
 * @param clock MilO's own clock (ADR-006): the day is today by it, not by the phone's date.
 * @param zone the zone the phone is in now.
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 */
class OneYearParty(
    private val tripStarts: suspend () -> List<TripStart>,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    /**
     * Whether Home is in front this moment and can show the party: said by the screens, read by
     * the trip service as a trip starts. False until a screen says otherwise.
     */
    @Volatile
    var homeWatching: Boolean = false

    private val handover = PopHandover()

    private val asked = MutableStateFlow(false)

    /** True from a request to see the party on demand until Home has dealt with it. */
    val askedFor: StateFlow<Boolean> = asked.asStateFlow()

    /**
     * When the newest trip started that Home has not shown the party for, or null, and again
     * each time that changes. Whether it still counts is for whoever reads it to say
     * (`partyOwedNow`). A settings file that cannot be read owes nothing.
     */
    val owedSinceMs: Flow<Long?> =
        settings.settings
            .map { it.partyOwedSinceMs }
            .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }
            .distinctUntilChanged()

    /** Asks for the party on demand: Home plays it once, with its pop, and stores nothing. */
    fun askFor() {
        asked.value = true
    }

    /** The request of [askFor] has been dealt with: played, or dropped. */
    fun askedDealtWith() {
        asked.value = false
    }

    /**
     * A trip is starting: its first sound is about to be played. Says how the party's pop is
     * played, and on the day of the party stores that Home owes the mascot's party.
     *
     * **It never throws and never takes long:** whatever goes wrong, the answer is
     * [PopAtTripStart.NOT_TODAY], which leaves the trip its sounds of every other day.
     */
    suspend fun atTripStart(): PopAtTripStart = try {
        withTimeoutOrNull(LOOK_LIMIT_MS) { look() } ?: tooSlow()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Every kind of failure, on purpose: see the class comment.
        report("Dually could not tell whether today is the day of its party", failure)
        PopAtTripStart.NOT_TODAY
    }

    private suspend fun look(): PopAtTripStart {
        // TODO(debt): a MilO that starts with nothing to check the phone's date against, in the
        //  very seconds the date is set a day ahead, goes by that date until it comes back
        //  (ADR-006's open gap). A trip that started in those seconds on the eve of the party
        //  would have the pop a day early. See docs/FINDINGS_LOG.md, 2026-10-10.
        val nowMs = clock()
        val firstMs = firstRecordedStartMs(tripStarts())
        // A settings file that cannot be read knows of no party held: the date still has its
        // party, as it had before a make-up day existed.
        val lastHeldOn =
            try {
                settings.current().partyHeldOn
            } catch (unreadable: IOException) {
                null
            }
        val date =
            partyDateToday(firstMs, lastHeldOn, nowMs, zone()) ?: return PopAtTripStart.NOT_TODAY
        val today = localDateOf(nowMs, zone())
        val stored =
            try {
                settings.setPartyOwedSinceMs(nowMs)
                // What makes a later day of the week no make-up day: the party was held.
                if (lastHeldOn != today) settings.setPartyHeldOn(today)
                true
            } catch (notStored: IOException) {
                report("Dually could not store that its mascot's party is owed", notStored)
                false
            }
        // A party that could not be stored is not shown, so its popper cannot make the sound.
        val pop = popAtTripStart(partyDay = true, homeWatching = homeWatching && stored)
        if (pop == PopAtTripStart.WITH_THE_ANIMATION) handover.leave()
        val since = firstMs?.let { localDateOf(it, zone()) }
        val how =
            when (pop) {
                PopAtTripStart.WITH_THE_ANIMATION ->
                    "Home is in front: the mascot's party plays there, and its popper makes the pop"

                else ->
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
        write(nowMs, EventCategory.SERVICE, "$what. $how", detail = null, unlogged = null)
        return pop
    }

    private suspend fun tooSlow(): PopAtTripStart {
        val what =
            "Dually could not tell in time whether today is the day of its party: the stored " +
                "trips took more than $LOOK_LIMIT_MS ms to read. This trip has its usual sounds"
        report(what, failure = null)
        return PopAtTripStart.NOT_TODAY
    }

    /**
     * Takes the pop that the trip service left to the animation ([PopHandover.take]).
     *
     * @return true for the one caller who now has to see that it is played.
     */
    fun takePop(): Boolean = handover.take()

    /**
     * The mascot's party has been shown for the trip that started at [forMs]: it is owed no
     * longer. A trip that started since stays owed.
     */
    suspend fun shown(forMs: Long) {
        try {
            settings.clearPartyOwed(forMs)
        } catch (notStored: IOException) {
            report("Dually could not store that its mascot's party has been shown", notStored)
        }
    }

    private suspend fun report(message: String, failure: Exception?) {
        write(clock(), EventCategory.ERROR, message, failure?.stackTraceToString(), failure)
    }

    /**
     * One line for the event log. If the log is what fails, the line goes to a crash file, as
     * the clock watch's does, and whoever asked carries on.
     *
     * @param unlogged the failure the line is about, if it is about one, for the crash file.
     */
    private suspend fun write(
        atMs: Long,
        category: EventCategory,
        message: String,
        detail: String?,
        unlogged: Exception?,
    ) {
        try {
            eventLog.add(atMs, category, message, detail)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val lost = IllegalStateException(message, unlogged)
            lost.addSuppressed(logFailure)
            crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, lost))
        }
    }
}
