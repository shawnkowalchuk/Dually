package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewsStored

// What MilO remembers about asking Google Play for its review card (2026-10-11, ADR-009): where
// each of the three moments stands, when MilO last went to Google Play for the card, and when
// it last launched Google Play's flow. Each moment is asked for once, ever, and thirty days lie
// between two launches: both have to outlive the process and every update.
// Not settings Shawn chooses, and not in an export file: they are this phone's, like the count
// of greetings. Android's backup carries them with the rest of the settings file, so a phone
// that is restored from a backup does not ask again for what it asked for before.

// The key names are what is written to the file; renaming one silently resets that value.
private val ReviewMoment.key: Preferences.Key<String>
    get() =
        stringPreferencesKey(
            when (this) {
                ReviewMoment.FIVE_TRIPS -> "review_five_trips"
                ReviewMoment.HUNDRED_TRIPS -> "review_hundred_trips"
                ReviewMoment.REPORT_SENT -> "review_report_sent"
            },
        )

private val REVIEW_LAST_ATTEMPT_AT_MS = longPreferencesKey("review_last_attempt_at_ms")
private val REVIEW_LAST_LAUNCH_AT_MS = longPreferencesKey("review_last_launch_at_ms")

/** What a standing is stored as. A moment nothing has happened for has no entry. */
private val MomentStanding.stored: String?
    get() =
        when (this) {
            MomentStanding.OPEN -> null
            MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY -> "said"
            MomentStanding.ASKED -> "asked"
        }

/**
 * What is stored about the review card. A word this build does not know, which only a newer
 * build can have written, is read as asked: MilO then asks for nothing it cannot be sure of.
 * A time that is negative, which no setter writes, is read as none.
 */
internal fun Preferences.readReviews(): ReviewsStored {
    val standing =
        buildMap {
            for (moment in ReviewMoment.entries) {
                val word = this@readReviews[moment.key] ?: continue
                val known = MomentStanding.entries.firstOrNull { it.stored == word }
                put(moment, known ?: MomentStanding.ASKED)
            }
        }
    return ReviewsStored(
        standing = standing,
        lastAttemptAtMs = this[REVIEW_LAST_ATTEMPT_AT_MS]?.takeIf { it >= 0 },
        lastLaunchAtMs = this[REVIEW_LAST_LAUNCH_AT_MS]?.takeIf { it >= 0 },
    )
}

private fun MutablePreferences.writeReviews(reviews: ReviewsStored) {
    for (moment in ReviewMoment.entries) setOrRemove(moment.key, reviews.of(moment).stored)
    setOrRemove(REVIEW_LAST_ATTEMPT_AT_MS, reviews.lastAttemptAtMs)
    setOrRemove(REVIEW_LAST_LAUNCH_AT_MS, reviews.lastLaunchAtMs)
}

/**
 * Changes what is stored about the review card, in one write: [change] is handed what the file
 * holds at that moment, so two changes cannot undo each other.
 *
 * @return what is stored now.
 */
suspend fun SettingsStore.changeReviews(change: (ReviewsStored) -> ReviewsStored): ReviewsStored {
    var after = ReviewsStored()
    dataStore.edit { stored ->
        after = change(stored.readReviews())
        for (lastMs in listOfNotNull(after.lastAttemptAtMs, after.lastLaunchAtMs)) {
            require(lastMs >= 0) { "A moment cannot be negative: $lastMs ms" }
        }
        stored.writeReviews(after)
    }
    return after
}
