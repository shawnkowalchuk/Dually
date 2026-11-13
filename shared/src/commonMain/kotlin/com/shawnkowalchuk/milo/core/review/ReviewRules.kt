package com.shawnkowalchuk.milo.core.review

import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.ZoneId

// When MilO asks Google Play to show its review card (2026-10-11, ADR-009). Shawn: "three google
// play prompts for a review. one after 5 trips recoreded, 100 trips recorded and after
// submitting a report". MilO only asks: Google Play decides whether its card appears, and never
// tells MilO whether it did or whether a review was written. So everything here is about when
// MilO asks, and nothing about what came of it. Pure rules: `platform/review` carries them out.

/**
 * The three moments. Each is asked for at most once, ever.
 *
 * @property recordedTrips how many recorded trips MilO has to hold for the moment to be
 * reached, or null for the one that is not a number of trips.
 */
enum class ReviewMoment(val recordedTrips: Int?) {
    /** MilO holds five trips it recorded itself. */
    FIVE_TRIPS(5),

    /** MilO holds a hundred. */
    HUNDRED_TRIPS(100),

    /** A report was recorded as sent with "I sent it", after the email app. */
    REPORT_SENT(null),
}

/** Where MilO looks at its moments. */
enum class ReviewOccasion {
    /** Home is in front and nothing is playing over it: the two numbers of trips are looked at. */
    HOME,

    /** "I sent it" has just recorded a report as sent, on the Report screen. */
    REPORT_SENT,
}

/** What is stored about one moment. */
enum class MomentStanding {
    /** Nothing has happened for it yet. */
    OPEN,

    /**
     * It was reached in a copy of MilO that Google Play did not install, and the Log has said
     * once that such a copy asks for nothing. **It is not used up:** a copy from Google Play
     * asks for it. Kept only so that the Log says it once and not at every opening of Home.
     */
    SAID_NOT_FROM_GOOGLE_PLAY,

    /** Google Play's flow was launched for it. It is never asked for again. */
    ASKED,
}

/**
 * Everything MilO remembers about its requests for the review card.
 *
 * @property standing where each moment stands. One that is not in the map is [MomentStanding.OPEN].
 * @property lastAttemptAtMs when MilO last went to Google Play for the card, whether the flow
 * was launched or Google Play gave none, or null if it never did.
 * @property lastLaunchAtMs when Google Play's flow was last launched, or null if it never was.
 * Kept apart from [lastAttemptAtMs]: a launch holds every moment back for
 * [DAYS_BETWEEN_LAUNCHES] days, a request Google Play gave no flow for only for the day.
 */
data class ReviewsStored(
    val standing: Map<ReviewMoment, MomentStanding> = emptyMap(),
    val lastAttemptAtMs: Long? = null,
    val lastLaunchAtMs: Long? = null,
) {
    fun of(moment: ReviewMoment): MomentStanding = standing[moment] ?: MomentStanding.OPEN
}

/** Why a moment that was reached is not asked for now. */
enum class NotAskedWhy {
    /**
     * This copy of MilO was not installed by Google Play (the APK from GitHub, or one put on
     * with a cable). Google Play shows its card for an app it installed for this account, so
     * here MilO asks for nothing, and uses nothing up.
     */
    NOT_FROM_GOOGLE_PLAY,

    /** A trip is being recorded, or a paired vehicle is connected: he may be driving. */
    MAY_BE_DRIVING,

    /**
     * Google Play's flow was launched less than [DAYS_BETWEEN_LAUNCHES] days ago. Google Play
     * would probably show nothing, and the moment would be spent on it.
     */
    ASKED_WITHIN_THIRTY_DAYS,

    /**
     * MilO has gone to Google Play for the card once already today, and Google Play gave no
     * flow to launch. Never twice in one day.
     */
    ONCE_TODAY_ALREADY,
}

/** What a look at the moments comes to. */
sealed interface ReviewJudgement {
    /** No moment of this occasion is reached that has not been asked for. */
    data object NothingReached : ReviewJudgement

    /**
     * Google Play is to be asked now, once, for all of [moments]: a MilO that is updated with
     * 150 trips stored has reached both numbers, and is asked once for the two.
     */
    data class Ask(val moments: Set<ReviewMoment>) : ReviewJudgement

    /** [moments] are reached, and nothing is asked now. Nothing is used up. */
    data class NotAsked(val moments: Set<ReviewMoment>, val why: NotAskedWhy) : ReviewJudgement
}

/**
 * Everything a look goes by.
 *
 * @property recordedTrips how many trips MilO holds that it recorded itself and that count:
 * finished, so not one being recorded, discarded or deleted, and not one typed in by hand.
 * @property fromGooglePlay whether Google Play installed this copy of MilO.
 * @property mayBeDriving [mayBeDriving], as of now.
 * @property nowMs the moment of the look, from MilO's own clock (ADR-006).
 * @property zone the zone the phone is in now: "today" is a day there.
 */
data class ReviewFacts(
    val occasion: ReviewOccasion,
    val recordedTrips: Int,
    val stored: ReviewsStored,
    val fromGooglePlay: Boolean,
    val mayBeDriving: Boolean,
    val nowMs: Long,
    val zone: ZoneId,
)

/**
 * Whether Shawn may be driving, by what the trip controller publishes: a trip is being
 * recorded, a paired vehicle is connected, or MilO waits beside a truck that is connected and
 * parked (Android Auto on the cable can hold that wait with Bluetooth gone). **Not known yet
 * counts as yes:** the card can wait a moment, a driver cannot be given one.
 *
 * "No trip being recorded" alone was not enough: the parked rule ends a trip while he sits in
 * the truck with the phone on its mount, and the fifth trip ending there would put the card in
 * front of a driver about to leave.
 *
 * @param vehicleConnected what the trip rules believe, or null before they have been read.
 */
fun mayBeDriving(
    tripOpen: Boolean,
    vehicleConnected: Boolean?,
    waitingBesideTruck: Boolean,
): Boolean = tripOpen || vehicleConnected != false || waitingBesideTruck

/**
 * Decides one look.
 *
 * - **Which moments are reached.** At Home, each number of trips that MilO holds and has not
 *   asked for. After "I sent it", the report's moment, unless it was asked for before. Each
 *   occasion looks at its own moments only: three moments are three requests.
 * - **Only a copy from Google Play asks.** Anywhere else nothing is asked and nothing used up.
 * - **Never while he may be driving.**
 * - **Not for [DAYS_BETWEEN_LAUNCHES] days after a launch** ([launchedLately]). A number of
 *   trips that is reached in those days stays reached, and is asked for at the first look
 *   after them; the report's moment has passed, as whenever it is held back, and comes again
 *   with the next "I sent it".
 * - **Never twice in one day,** if Google Play gave no flow to launch the first time.
 */
fun judgeReview(facts: ReviewFacts): ReviewJudgement {
    val reached = reachedNow(facts.occasion, facts.recordedTrips, facts.stored)
    return when {
        reached.isEmpty() -> ReviewJudgement.NothingReached

        !facts.fromGooglePlay -> ReviewJudgement.NotAsked(reached, NotAskedWhy.NOT_FROM_GOOGLE_PLAY)

        facts.mayBeDriving -> ReviewJudgement.NotAsked(reached, NotAskedWhy.MAY_BE_DRIVING)

        launchedLately(facts.stored, facts.nowMs) ->
            ReviewJudgement.NotAsked(reached, NotAskedWhy.ASKED_WITHIN_THIRTY_DAYS)

        attemptedToday(facts.stored, facts.nowMs, facts.zone) ->
            ReviewJudgement.NotAsked(reached, NotAskedWhy.ONCE_TODAY_ALREADY)

        else -> ReviewJudgement.Ask(reached)
    }
}

/** The moments of [occasion] that are reached and have not been asked for. */
private fun reachedNow(
    occasion: ReviewOccasion,
    recordedTrips: Int,
    stored: ReviewsStored,
): Set<ReviewMoment> {
    val ofOccasion =
        when (occasion) {
            ReviewOccasion.HOME ->
                ReviewMoment.entries.filter {
                    it.recordedTrips?.let { n -> recordedTrips >= n } ==
                        true
                }

            ReviewOccasion.REPORT_SENT -> listOf(ReviewMoment.REPORT_SENT)
        }
    return ofOccasion.filter { stored.of(it) != MomentStanding.ASKED }.toSet()
}

/**
 * How long MilO leaves between two launches of Google Play's flow. Google Play limits how
 * often it shows its card and does not say by how much; the example in its documentation is
 * an app that asks more than once within a month and is shown nothing (ADR-009). A moment
 * that fell inside that time would be launched, count as asked, and show no card.
 */
const val DAYS_BETWEEN_LAUNCHES = 30

private const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L

/**
 * Whether Google Play's flow was launched less than [DAYS_BETWEEN_LAUNCHES] days ago: thirty
 * times 24 hours by MilO's own clock (ADR-006), whatever the zone and whatever hour a change of
 * the clocks adds or takes.
 *
 * **A launch that lies ahead of now is not believed,** for [attemptedToday]'s reason.
 */
fun launchedLately(stored: ReviewsStored, nowMs: Long): Boolean = daysToWait(stored, nowMs) > 0

/**
 * How many days are left until MilO asks again after its last launch, counted up to whole
 * days: 0 if nothing holds it back, and [DAYS_BETWEEN_LAUNCHES] in the moment after a launch.
 */
fun daysToWait(stored: ReviewsStored, nowMs: Long): Int {
    val lastMs = stored.lastLaunchAtMs ?: return 0
    if (lastMs > nowMs) return 0
    val leftMs = lastMs + DAYS_BETWEEN_LAUNCHES * MILLIS_IN_DAY - nowMs
    return if (leftMs <= 0) 0 else ((leftMs + MILLIS_IN_DAY - 1) / MILLIS_IN_DAY).toInt()
}

/**
 * Whether MilO has gone to Google Play for the card today already. After a launch the
 * [DAYS_BETWEEN_LAUNCHES] days hold every moment back, so this is what holds the rest of a day
 * on which Google Play gave no flow.
 *
 * **A moment that lies ahead of now is not believed,** as everywhere in MilO (ADR-006, decision
 * 5): its own clock does not go back by itself, so such a moment was written by another
 * installation or under a date that was wrong.
 */
fun attemptedToday(stored: ReviewsStored, nowMs: Long, zone: ZoneId): Boolean {
    val lastMs = stored.lastAttemptAtMs ?: return false
    if (lastMs > nowMs) return false
    return localDateOf(lastMs, zone) == localDateOf(nowMs, zone)
}

/**
 * What is stored once Google Play's flow has been launched for [moments]. **Launched is what
 * counts as asked,** whether or not Google Play then showed its card: MilO cannot know.
 */
fun ReviewsStored.afterLaunch(moments: Set<ReviewMoment>, nowMs: Long): ReviewsStored = copy(
    standing = standing + moments.associateWith { MomentStanding.ASKED },
    lastAttemptAtMs = nowMs,
    lastLaunchAtMs = nowMs,
)

/**
 * What is stored when Google Play gave no flow to launch (no Play Store, no network): no moment
 * counts as asked, and MilO does not try again today. The last launch stays what it was.
 */
fun ReviewsStored.afterNoFlow(nowMs: Long): ReviewsStored = copy(lastAttemptAtMs = nowMs)

/**
 * What is stored once the Log has said that this copy of MilO, which Google Play did not
 * install, asks for nothing at [moments]. A moment that was asked for stays asked for.
 */
fun ReviewsStored.afterSaidNotFromGooglePlay(moments: Set<ReviewMoment>): ReviewsStored = copy(
    standing =
        standing +
            moments
                .filter { of(it) == MomentStanding.OPEN }
                .associateWith { MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY },
)
