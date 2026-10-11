package com.shawnkowalchuk.milo.platform.review

/**
 * How long Google Play is given to answer a request for its review flow. The request goes to
 * the Play Store app on the phone; one that has not answered by then is not waited for, because
 * the moment the card was for has passed.
 */
internal const val REQUEST_LIMIT_MS = 10_000L

/**
 * Google Play's review card, as much of it as MilO uses (ADR-009): MilO asks Google Play for a
 * review flow, and launches the one it is given. Whether the card then appears is Google
 * Play's decision, and nothing comes back that says whether it did or whether a review was
 * written. So this has no answer about the card itself, and must never grow one.
 *
 * The one implementation is [PlayReviewCard]; the rules around it run in unit tests on a
 * stand-in.
 */
fun interface ReviewCard {
    /** Asks Google Play for a review flow. Never throws for a flow that was not given. */
    suspend fun request(): ReviewFlow
}

/** What Google Play answered. */
sealed interface ReviewFlow {
    /**
     * Google Play gave a flow.
     *
     * @property launch starts it on the screen it was asked for, and answers whether it did:
     * false if that screen has gone meanwhile (the phone was turned, MilO was closed), in which
     * case nothing was started. Google Play then decides whether its card appears. The flow is
     * good for a short while only, so this is called at once or not at all.
     */
    class Given(val launch: () -> Boolean) : ReviewFlow

    /**
     * Google Play gave none: the phone has no Play Store, it could not be reached, or it
     * answered with an error.
     *
     * @property why the reason in words for the event log.
     * @property detail anything longer for the event log, such as a stack trace, or null.
     */
    data class NotGiven(val why: String, val detail: String? = null) : ReviewFlow
}
