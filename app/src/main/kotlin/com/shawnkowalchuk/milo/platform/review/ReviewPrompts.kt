package com.shawnkowalchuk.milo.platform.review

import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.NotAskedWhy
import com.shawnkowalchuk.milo.core.review.ReviewFacts
import com.shawnkowalchuk.milo.core.review.ReviewJudgement
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.core.review.ReviewsStored
import com.shawnkowalchuk.milo.core.review.afterLaunch
import com.shawnkowalchuk.milo.core.review.afterNoFlow
import com.shawnkowalchuk.milo.core.review.afterSaidNotFromGooglePlay
import com.shawnkowalchuk.milo.core.review.daysToWait
import com.shawnkowalchuk.milo.core.review.judgeReview
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.changeReviews
import java.io.IOException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks Google Play to show its review card at MilO's three moments (2026-10-11, ADR-009; Shawn:
 * "one after 5 trips recoreded, 100 trips recorded and after submitting a report"). One object
 * for the whole app. The screens say when a moment may be acted on ([look]), and hand over the
 * card of the screen in front when it is to be asked for ([ask]); what is asked, and what
 * counts as asked, is decided by the pure rules of `core/review`.
 *
 * **MilO asks; Google Play decides.** Nothing here knows whether the card appeared or a review
 * was written, and nothing may be built that depends on either.
 *
 * **It can touch no trip and no report.** It is handed a number, a yes or no, the settings
 * file, of which it writes its own values only, and the event log. A failure in it is caught
 * here and written to the Log: left alone, it would end the process, and the trip service runs
 * in it.
 *
 * @param recordedTrips how many trips MilO holds that it recorded itself and that count, and
 * again each time that changes (`TripRepository.observeRecordedTripCount`).
 * @param mayBeDriving whether a drive may be under way, as the trip controller publishes it
 * (`core/review`, `mayBeDriving`).
 * @param fromGooglePlay whether Google Play installed this copy of MilO.
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 * @param clock MilO's own clock (ADR-006): the thirty days after a launch and "once a day" are
 * counted by it.
 * @param zone the zone the phone is in now.
 */
class ReviewPrompts(
    private val settings: SettingsStore,
    private val recordedTrips: Flow<Int>,
    private val mayBeDriving: Flow<Boolean>,
    private val fromGooglePlay: () -> Boolean,
    eventLog: EventLogRepository,
    crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    /** Writes this object's lines, and catches what fails in it (`ReviewNotes.kt`). */
    private val notes = ReviewNotes(eventLog, crashFileStore, clock)

    /** A look and a request never run side by side: each sees what the one before stored. */
    private val oneAtATime = Mutex()

    /**
     * What this process did that the settings file refused to take. Believed all the same:
     * otherwise a file that cannot be written would have MilO ask at every opening of Home.
     */
    private var notStored = ReviewsStored()

    /** What this run of MilO has said, at Home, of a moment that waits (`ReviewNotes.kt`). */
    private val saidAtHome = SaidAtHome()

    private val reportSent = MutableStateFlow(false)

    /**
     * True from "I sent it" having recorded a report until the screens have dealt with it
     * ([reportDealtWith]). In memory only: a MilO that is ended in between lets the moment pass.
     */
    val reportJustSent: StateFlow<Boolean> = reportSent.asStateFlow()

    /**
     * What a look goes by, as it is when the watch begins, and again each time it changes: the
     * number of recorded trips, and whether a drive may be under way. Nothing is read until
     * this is collected, which the screens do only while one of them is fit for the card. A
     * storage that cannot be read ends the watch, with one line in the Log.
     */
    val changes: Flow<Unit> =
        combine(recordedTrips, mayBeDriving) { trips, driving -> trips to driving }
            .distinctUntilChanged()
            .map { }
            .catch { failure ->
                if (failure !is Exception) throw failure
                val what = "Dually could not watch for the moments of Google Play's review card"
                notes.report(what, failure)
            }

    /** "I sent it" has recorded a report as sent, after the email app. Not "Mark as sent". */
    fun onReportSentByAnswer() {
        reportSent.value = true
    }

    /** The report's moment has been acted on, or has passed. */
    fun reportDealtWith() {
        reportSent.value = false
    }

    /**
     * Looks at the moments of [occasion], and writes the Log's line for one that is reached and
     * not asked for now.
     *
     * @return the moments Google Play is to be asked for now, with [ask]; empty for none.
     */
    suspend fun look(occasion: ReviewOccasion): Set<ReviewMoment> = notes.keptApart(emptySet()) {
        oneAtATime.withLock {
            val (facts, judged) = judge(occasion)
            when (judged) {
                is ReviewJudgement.Ask -> judged.moments

                is ReviewJudgement.NotAsked -> {
                    sayNotAsked(facts, judged)
                    emptySet()
                }

                ReviewJudgement.NothingReached -> emptySet()
            }
        }
    }

    /**
     * Goes to Google Play for its review card, if the rules still say so at this moment.
     *
     * - Google Play gives a flow and the screen is still [stillFitting]: the flow is launched,
     *   and from that moment every moment it was for **counts as asked**, whatever Google Play
     *   then shows. MilO asks for nothing else for thirty days.
     * - Google Play gives none, or does not answer in time: nothing counts as asked, and MilO
     *   does not try again today.
     * - The screen was left meanwhile, or a drive may have begun: nothing is launched and
     *   nothing is stored.
     *
     * @param card Google Play's card for the screen in front.
     * @param stillFitting whether the screen the card was meant for is still in front, asked
     * in the moment before the flow is launched.
     */
    suspend fun ask(occasion: ReviewOccasion, card: ReviewCard, stillFitting: () -> Boolean) {
        notes.keptApart(Unit) {
            oneAtATime.withLock {
                val (facts, judged) = judge(occasion)
                if (judged is ReviewJudgement.Ask) goToGooglePlay(facts, judged, card, stillFitting)
            }
        }
    }

    private suspend fun goToGooglePlay(
        facts: ReviewFacts,
        asking: ReviewJudgement.Ask,
        card: ReviewCard,
        stillFitting: () -> Boolean,
    ) {
        val moments = asking.moments
        val trips = facts.recordedTrips
        val flow =
            try {
                withTimeoutOrNull(REQUEST_LIMIT_MS) { card.request() }
                    ?: ReviewFlow.NotGiven(
                        "Google Play did not answer in ${REQUEST_LIMIT_MS / 1000} s",
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ReviewFlow.NotGiven("the request failed", failure.stackTraceToString())
            }
        when (flow) {
            is ReviewFlow.NotGiven -> noFlow(moments, trips, flow)

            is ReviewFlow.Given ->
                if (stillFitting() && !mayBeDriving.first()) {
                    launch(moments, trips, flow)
                } else {
                    notes.write(EventCategory.PROCESS, passedLine(moments, trips))
                }
        }
    }

    private suspend fun launch(moments: Set<ReviewMoment>, trips: Int, flow: ReviewFlow.Given) {
        val launched =
            try {
                flow.launch()
            } catch (failure: Exception) {
                // Whatever starting Google Play's screen throws: it was not started.
                val why = "its screen could not be started"
                noFlow(moments, trips, ReviewFlow.NotGiven(why, failure.stackTraceToString()))
                return
            }
        if (!launched) {
            notes.write(EventCategory.PROCESS, passedLine(moments, trips))
            return
        }
        // Not to be cut short: the flow is launched, and that has to be remembered.
        withContext(NonCancellable) {
            store { it.afterLaunch(moments, clock()) }
            notes.write(EventCategory.PROCESS, askedLine(moments, trips))
        }
    }

    private suspend fun noFlow(moments: Set<ReviewMoment>, trips: Int, flow: ReviewFlow.NotGiven) {
        withContext(NonCancellable) {
            val nowMs = clock()
            store { it.afterNoFlow(nowMs) }
            // The line below says "not before tomorrow": Home need not say it again today.
            saidAtHome.notBeforeTomorrow(localDateOf(nowMs, zone()))
            notes.write(EventCategory.PROCESS, noFlowLine(moments, trips, flow.why), flow.detail)
        }
    }

    /**
     * One line for a moment that is reached and not asked for, and not one at every look:
     * - **not from Google Play:** once ever for each moment, which is remembered in the
     *   settings file without the moment being used up;
     * - **a drive may be under way:** nothing at Home, where the card only waits for the drive
     *   to be over; one line after "I sent it", where the moment passes;
     * - **asked within thirty days:** at Home once for that launch in each run of MilO's
     *   process, and each time after "I sent it", where the moment passes;
     * - **once today already:** at Home once a day in each run of MilO's process, and each
     *   time after "I sent it".
     */
    private suspend fun sayNotAsked(facts: ReviewFacts, judged: ReviewJudgement.NotAsked) {
        val atHome = facts.occasion == ReviewOccasion.HOME
        val moments =
            when (judged.why) {
                NotAskedWhy.NOT_FROM_GOOGLE_PLAY ->
                    judged.moments.filter { facts.stored.of(it) == MomentStanding.OPEN }.toSet()

                NotAskedWhy.MAY_BE_DRIVING -> judged.moments.takeUnless { atHome }.orEmpty()

                NotAskedWhy.ASKED_WITHIN_THIRTY_DAYS, NotAskedWhy.ONCE_TODAY_ALREADY ->
                    judged.moments.takeUnless { atHome && saidAtHome.already(facts, judged.why) }
                        .orEmpty()
            }
        if (moments.isEmpty()) return
        // Not to be cut short: what is remembered and what the Log says belong together.
        withContext(NonCancellable) {
            if (judged.why == NotAskedWhy.NOT_FROM_GOOGLE_PLAY) {
                store { it.afterSaidNotFromGooglePlay(moments) }
            }
            val reached = reachedInWords(moments, facts.recordedTrips)
            val wait = daysToWait(facts.stored, facts.nowMs)
            val line = notAskedLine(facts.occasion, reached, judged.why, wait)
            notes.write(EventCategory.PROCESS, line)
        }
    }

    private suspend fun judge(occasion: ReviewOccasion): Pair<ReviewFacts, ReviewJudgement> {
        val facts =
            ReviewFacts(
                occasion = occasion,
                recordedTrips = recordedTrips.first(),
                stored = settings.current().reviews.withNotStored(),
                fromGooglePlay = fromGooglePlay(),
                mayBeDriving = mayBeDriving.first(),
                nowMs = clock(),
                zone = zone(),
            )
        return facts to judgeReview(facts)
    }

    private fun ReviewsStored.withNotStored(): ReviewsStored = copy(
        standing = standing + notStored.standing.filterValues { it == MomentStanding.ASKED },
        lastAttemptAtMs = listOfNotNull(lastAttemptAtMs, notStored.lastAttemptAtMs).maxOrNull(),
        lastLaunchAtMs = listOfNotNull(lastLaunchAtMs, notStored.lastLaunchAtMs).maxOrNull(),
    )

    /** Stores a change to what MilO remembers; one the file refuses is kept in [notStored]. */
    private suspend fun store(change: (ReviewsStored) -> ReviewsStored) {
        try {
            settings.changeReviews(change)
        } catch (notWritten: IOException) {
            notStored = change(notStored)
            val what = "Dually could not store what it asked Google Play's review card for"
            notes.report(what, notWritten)
        }
    }
}
