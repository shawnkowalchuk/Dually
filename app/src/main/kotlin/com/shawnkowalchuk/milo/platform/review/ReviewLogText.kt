package com.shawnkowalchuk.milo.platform.review

import com.shawnkowalchuk.milo.core.review.NotAskedWhy
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion

// The lines the event log gets about Google Play's review card: one for each time MilO went to
// Google Play, and one for a moment that was reached and not asked for. English and plain, like
// the rest of the log. Pure functions.

/** What every line about the card begins with, so that they are found together in the Log. */
internal const val REVIEW_LINE = "Review card"

/**
 * What was reached, in words: the number of trips MilO holds, or the report that was recorded
 * as sent.
 */
internal fun reachedInWords(moments: Set<ReviewMoment>, recordedTrips: Int): String {
    val numbers = moments.mapNotNull { it.recordedTrips }.sorted()
    return when {
        numbers.isEmpty() -> "A report was recorded as sent with \"I sent it\""

        else ->
            "MilO holds $recordedTrips recorded trips, and asks at " +
                numbers.joinToString(" and at ")
    }
}

/** Google Play's flow was launched. */
internal fun askedLine(moments: Set<ReviewMoment>, recordedTrips: Int): String {
    val forAll = if (moments.size > 1) ", once for all of them" else ""
    return "$REVIEW_LINE: ${reachedInWords(moments, recordedTrips)}. MilO asked Google Play to " +
        "show its review card$forAll. Google Play decides whether the card appears, and MilO " +
        "is not told. MilO does not ask for this again"
}

/** Google Play gave no flow to launch. */
internal fun noFlowLine(moments: Set<ReviewMoment>, recordedTrips: Int, why: String): String =
    "$REVIEW_LINE: ${reachedInWords(moments, recordedTrips)}. MilO asked Google Play for its " +
        "review card, and Google Play gave none to show: $why. Nothing counts as asked. MilO " +
        "tries again at the next such moment, not before tomorrow"

/** Google Play gave a flow, and the moment for it had passed before it could be launched. */
internal fun passedLine(moments: Set<ReviewMoment>, recordedTrips: Int): String =
    "$REVIEW_LINE: ${reachedInWords(moments, recordedTrips)}. Google Play had a review card " +
        "ready, but by then MilO had left that screen or a drive may have begun, so MilO did " +
        "not have it shown. Nothing counts as asked, and MilO asks at the next such moment"

/** A moment was reached and MilO did not go to Google Play at all. */
internal fun notAskedLine(
    occasion: ReviewOccasion,
    moments: Set<ReviewMoment>,
    recordedTrips: Int,
    why: NotAskedWhy,
): String {
    val reason =
        when (why) {
            NotAskedWhy.NOT_FROM_GOOGLE_PLAY ->
                "this copy of MilO was not installed by Google Play, and Google Play shows its " +
                    "review card only in one that was. Nothing is used up: a copy from Google " +
                    "Play asks. This is written once"

            NotAskedWhy.MAY_BE_DRIVING ->
                "a trip is being recorded or a vehicle is connected, and MilO never asks " +
                    "while a drive may be under way. Nothing is used up. ${nextTime(occasion)}"

            NotAskedWhy.ONCE_TODAY_ALREADY ->
                "it went to Google Play for the card once already today, and never does twice " +
                    "in one day. Nothing is used up. ${nextTime(occasion)}"
        }
    return "$REVIEW_LINE: ${reachedInWords(moments, recordedTrips)}. MilO asked Google Play " +
        "for nothing: $reason"
}

private fun nextTime(occasion: ReviewOccasion): String = when (occasion) {
    ReviewOccasion.HOME ->
        "MilO asks the next time Home is open on another day, with no trip being recorded " +
            "and no vehicle connected"

    ReviewOccasion.REPORT_SENT ->
        "MilO asks after the next report that is recorded as sent with \"I sent it\""
}
