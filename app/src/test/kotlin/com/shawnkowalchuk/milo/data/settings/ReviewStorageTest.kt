package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewsStored
import com.shawnkowalchuk.milo.core.review.afterLaunch
import com.shawnkowalchuk.milo.core.review.afterNoFlow
import com.shawnkowalchuk.milo.core.review.afterSaidNotFromGooglePlay
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** What MilO remembers about asking Google Play for its review card, as the settings file keeps it. */
class ReviewStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    @Test
    fun `out of the box nothing was asked for, and MilO never went to Google Play`() = runTest {
        val reviews = store.current().reviews

        for (moment in ReviewMoment.entries) assertEquals(MomentStanding.OPEN, reviews.of(moment))
        assertNull(reviews.lastAttemptAtMs)
        assertNull(reviews.lastLaunchAtMs)
        assertEquals(MiloSettings(), store.current())
    }

    @Test
    fun `a launch is stored and read back, and nothing else in the file is touched`() = runTest {
        val both = setOf(ReviewMoment.FIVE_TRIPS, ReviewMoment.HUNDRED_TRIPS)

        val after = store.changeReviews { it.afterLaunch(both, 1_791_700_000_000L) }

        assertEquals(after, store.current().reviews)
        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(MomentStanding.OPEN, after.of(ReviewMoment.REPORT_SENT))
        assertEquals(1_791_700_000_000L, after.lastAttemptAtMs)
        assertEquals(1_791_700_000_000L, after.lastLaunchAtMs)
        assertEquals(MiloSettings(reviews = after), store.current())
    }

    @Test
    fun `each change is made on what the file holds at that moment`() = runTest {
        store.changeReviews { it.afterSaidNotFromGooglePlay(setOf(ReviewMoment.FIVE_TRIPS)) }
        store.changeReviews { it.afterNoFlow(1_791_700_000_000L) }
        store.changeReviews { it.afterLaunch(setOf(ReviewMoment.REPORT_SENT), 1_791_800_000_000L) }

        val reviews = store.current().reviews
        assertEquals(MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY, reviews.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.OPEN, reviews.of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(MomentStanding.ASKED, reviews.of(ReviewMoment.REPORT_SENT))
        assertEquals(1_791_800_000_000L, reviews.lastAttemptAtMs)
        assertEquals(1_791_800_000_000L, reviews.lastLaunchAtMs)
    }

    @Test
    fun `a request Google Play gave no flow for leaves the last launch as it was`() = runTest {
        store.changeReviews { it.afterLaunch(setOf(ReviewMoment.FIVE_TRIPS), 1_791_700_000_000L) }
        store.changeReviews { it.afterNoFlow(1_794_400_000_000L) }

        val reviews = store.current().reviews
        assertEquals(1_794_400_000_000L, reviews.lastAttemptAtMs)
        assertEquals(1_791_700_000_000L, reviews.lastLaunchAtMs)
    }

    @Test
    fun `the moments and the two times are stored under names that must never change`() = runTest {
        store.changeReviews { it.afterLaunch(ReviewMoment.entries.toSet(), 5L) }

        val stored = file.data.first()
        assertEquals("asked", stored[stringPreferencesKey("review_five_trips")])
        assertEquals("asked", stored[stringPreferencesKey("review_hundred_trips")])
        assertEquals("asked", stored[stringPreferencesKey("review_report_sent")])
        assertEquals(5L, stored[longPreferencesKey("review_last_attempt_at_ms")])
        assertEquals(5L, stored[longPreferencesKey("review_last_launch_at_ms")])
    }

    @Test
    fun `a moment that goes back to open leaves no entry behind`() = runTest {
        store.changeReviews { it.afterLaunch(setOf(ReviewMoment.FIVE_TRIPS), 5L) }

        store.changeReviews { ReviewsStored() }

        assertEquals(MiloSettings(), store.current())
    }

    @Test
    fun `a word this build does not know is read as asked`() = runTest {
        // Only a newer build can have written it. MilO then asks for nothing it cannot be sure of.
        file.edit { it[stringPreferencesKey("review_hundred_trips")] = "asked-and-celebrated" }

        val reviews = store.current().reviews

        assertEquals(MomentStanding.ASKED, reviews.of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(MomentStanding.OPEN, reviews.of(ReviewMoment.FIVE_TRIPS))
    }

    @Test
    fun `a negative time is refused, and one that got into the file is read as none`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.changeReviews { it.copy(lastAttemptAtMs = -1L) } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.changeReviews { it.copy(lastLaunchAtMs = -1L) } }
        }
        assertEquals(MiloSettings(), store.current())
        file.edit {
            it[longPreferencesKey("review_last_attempt_at_ms")] = -5L
            it[longPreferencesKey("review_last_launch_at_ms")] = -5L
        }

        assertNull(store.current().reviews.lastAttemptAtMs)
        assertNull(store.current().reviews.lastLaunchAtMs)
    }

    @Test
    fun `an export file does not carry it, and an import leaves it as this phone has it`() =
        runTest {
            val asked =
                store.changeReviews { it.afterLaunch(setOf(ReviewMoment.FIVE_TRIPS), 5L) }

            // What travels in an export has no place for it.
            val travelling = store.current().transferred()
            store.replaceTransferred(travelling.copy(gracePeriodSeconds = 60), TruckChange.Keep)

            assertEquals(60, store.current().gracePeriodSeconds)
            assertEquals(asked, store.current().reviews)
        }

    @Test
    fun `a restore from Android's backup keeps it, so nothing is asked for a second time`() =
        runTest {
            val asked =
                store.changeReviews { it.afterLaunch(setOf(ReviewMoment.REPORT_SENT), 5L) }

            store.forgetOtherInstallation(dropAssociation = true, dropOwnSound = true)

            assertEquals(asked, store.current().reviews)
        }
}
