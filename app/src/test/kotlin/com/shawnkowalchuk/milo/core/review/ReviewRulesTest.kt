package com.shawnkowalchuk.milo.core.review

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(day: Int, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(2026, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

private fun stored(vararg asked: ReviewMoment, lastAttemptAtMs: Long? = null) =
    ReviewsStored(asked.associateWith { MomentStanding.ASKED }, lastAttemptAtMs)

private val FIVE = setOf(ReviewMoment.FIVE_TRIPS)
private val HUNDRED = setOf(ReviewMoment.HUNDRED_TRIPS)
private val BOTH = setOf(ReviewMoment.FIVE_TRIPS, ReviewMoment.HUNDRED_TRIPS)
private val REPORT = setOf(ReviewMoment.REPORT_SENT)

/**
 * When MilO asks Google Play for its review card: which moment is due, once ever and once a
 * day, the MilO that is updated with trips already stored, the copy Google Play did not
 * install, the drive that may be under way, and what counts as asked.
 */
class ReviewRulesTest {
    /** A look at Home, on a copy from Google Play with nobody driving, at noon on the 11th. */
    private fun atHome(
        trips: Int,
        stored: ReviewsStored = ReviewsStored(),
        fromGooglePlay: Boolean = true,
        mayBeDriving: Boolean = false,
        nowMs: Long = at(11, 12),
    ) = judgeReview(
        ReviewFacts(
            ReviewOccasion.HOME,
            trips,
            stored,
            fromGooglePlay,
            mayBeDriving,
            nowMs,
            EDMONTON,
        ),
    )

    private fun afterISentIt(
        trips: Int = 0,
        stored: ReviewsStored = ReviewsStored(),
        fromGooglePlay: Boolean = true,
        mayBeDriving: Boolean = false,
        nowMs: Long = at(11, 12),
    ) = judgeReview(
        ReviewFacts(
            ReviewOccasion.REPORT_SENT,
            trips,
            stored,
            fromGooglePlay,
            mayBeDriving,
            nowMs,
            EDMONTON,
        ),
    )

    // ---- Which moment is due ------------------------------------------------------------------

    @Test
    fun `nothing is reached below five trips`() {
        for (trips in 0..4) assertEquals(ReviewJudgement.NothingReached, atHome(trips))
    }

    @Test
    fun `five trips are asked for at five, and at every number up to ninety-nine`() {
        for (trips in 5..99) assertEquals(ReviewJudgement.Ask(FIVE), atHome(trips))
    }

    @Test
    fun `once asked for, five trips are never asked for again`() {
        for (trips in 5..99) {
            val judged = atHome(trips, stored(ReviewMoment.FIVE_TRIPS))
            assertEquals(ReviewJudgement.NothingReached, judged)
        }
    }

    @Test
    fun `a hundred trips are asked for at a hundred, and once`() {
        val fiveAsked = stored(ReviewMoment.FIVE_TRIPS)
        assertEquals(ReviewJudgement.NothingReached, atHome(99, fiveAsked))
        assertEquals(ReviewJudgement.Ask(HUNDRED), atHome(100, fiveAsked))
        assertEquals(ReviewJudgement.Ask(HUNDRED), atHome(5_000, fiveAsked))

        val bothAsked = stored(ReviewMoment.FIVE_TRIPS, ReviewMoment.HUNDRED_TRIPS)
        assertEquals(ReviewJudgement.NothingReached, atHome(100, bothAsked))
        assertEquals(ReviewJudgement.NothingReached, atHome(5_000, bothAsked))
    }

    @Test
    fun `a report recorded as sent is asked for once, whatever the number of trips`() {
        assertEquals(ReviewJudgement.Ask(REPORT), afterISentIt(trips = 0))
        assertEquals(ReviewJudgement.Ask(REPORT), afterISentIt(trips = 3))
        assertEquals(
            ReviewJudgement.NothingReached,
            afterISentIt(stored = stored(ReviewMoment.REPORT_SENT)),
        )
    }

    @Test
    fun `each occasion looks at its own moments only`() {
        // After "I sent it" with 150 trips never asked about: the report alone. The numbers of
        // trips wait for Home.
        assertEquals(ReviewJudgement.Ask(REPORT), afterISentIt(trips = 150))
        // And Home never asks for the report.
        assertEquals(ReviewJudgement.NothingReached, atHome(4))
        assertEquals(
            ReviewJudgement.NothingReached,
            atHome(150, stored(ReviewMoment.FIVE_TRIPS, ReviewMoment.HUNDRED_TRIPS)),
        )
    }

    // ---- The MilO that is updated with trips already stored -------------------------------------

    @Test
    fun `a MilO updated with 20 trips stored has reached five, and is asked once`() {
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(20))
        val after = ReviewsStored().afterLaunch(FIVE, at(11, 12))
        assertEquals(ReviewJudgement.NothingReached, atHome(20, after, nowMs = at(12, 12)))
    }

    @Test
    fun `a MilO updated with 150 trips stored is asked once, and both numbers count as asked`() {
        assertEquals(ReviewJudgement.Ask(BOTH), atHome(150))

        val after = ReviewsStored().afterLaunch(BOTH, at(11, 12))

        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(MomentStanding.OPEN, after.of(ReviewMoment.REPORT_SENT))
        for (day in 12..31) {
            assertEquals(ReviewJudgement.NothingReached, atHome(150, after, nowMs = at(day, 12)))
        }
    }

    // ---- Once a day ---------------------------------------------------------------------------

    @Test
    fun `never twice in one day, from midnight to midnight`() {
        val askedThisMorning = ReviewsStored().afterLaunch(REPORT, at(11, 7, 30))

        val held = ReviewJudgement.NotAsked(FIVE, NotAskedWhy.ONCE_TODAY_ALREADY)
        assertEquals(held, atHome(5, askedThisMorning, nowMs = at(11, 7, 31)))
        assertEquals(held, atHome(5, askedThisMorning, nowMs = at(11, 23, 59)))
        // The next day it is asked for.
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(5, askedThisMorning, nowMs = at(12, 0)))
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(5, askedThisMorning, nowMs = at(12, 12)))
    }

    @Test
    fun `the report's moment is held the same way on the day the trips were asked for`() {
        val askedToday = ReviewsStored().afterLaunch(FIVE, at(11, 8))

        assertEquals(
            ReviewJudgement.NotAsked(REPORT, NotAskedWhy.ONCE_TODAY_ALREADY),
            afterISentIt(stored = askedToday, nowMs = at(11, 16)),
        )
        assertEquals(
            ReviewJudgement.Ask(REPORT),
            afterISentIt(stored = askedToday, nowMs = at(12, 9)),
        )
    }

    @Test
    fun `a request Google Play gave no flow for uses nothing up, and holds the day`() {
        val after = ReviewsStored().afterNoFlow(at(11, 8))

        assertEquals(MomentStanding.OPEN, after.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(
            ReviewJudgement.NotAsked(FIVE, NotAskedWhy.ONCE_TODAY_ALREADY),
            atHome(5, after, nowMs = at(11, 20)),
        )
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(5, after, nowMs = at(12, 6)))
    }

    @Test
    fun `the day is the day in the zone the phone is in`() {
        // 23:30 in Edmonton on the 11th is 01:30 on the 12th in Toronto.
        val late = at(11, 23, 30)
        val stored = ReviewsStored(lastAttemptAtMs = late)

        assertTrue(attemptedToday(stored, at(11, 23, 45), EDMONTON))
        assertFalse(attemptedToday(stored, at(12, 0, 15), EDMONTON))
        assertTrue(attemptedToday(stored, at(12, 0, 15), ZoneId.of("America/Toronto")))
    }

    @Test
    fun `a last time that lies ahead of now is not believed`() {
        // Written by another installation, or under a date that was wrong (ADR-006).
        val ahead = ReviewsStored(lastAttemptAtMs = at(11, 18))

        assertFalse(attemptedToday(ahead, at(11, 12), EDMONTON))
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(5, ahead, nowMs = at(11, 12)))
    }

    @Test
    fun `with no last time nothing is held`() {
        assertFalse(attemptedToday(ReviewsStored(), at(11, 12), EDMONTON))
    }

    // ---- Only a copy from Google Play asks ------------------------------------------------------

    @Test
    fun `a copy Google Play did not install asks for nothing, at any moment`() {
        val notFromPlay = NotAskedWhy.NOT_FROM_GOOGLE_PLAY
        assertEquals(ReviewJudgement.NotAsked(FIVE, notFromPlay), atHome(5, fromGooglePlay = false))
        assertEquals(
            ReviewJudgement.NotAsked(BOTH, notFromPlay),
            atHome(150, fromGooglePlay = false),
        )
        assertEquals(
            ReviewJudgement.NotAsked(REPORT, notFromPlay),
            afterISentIt(fromGooglePlay = false),
        )
        // Below five there is nothing to say.
        assertEquals(ReviewJudgement.NothingReached, atHome(4, fromGooglePlay = false))
    }

    @Test
    fun `saying so uses nothing up, and a copy from Google Play then asks`() {
        val said = ReviewsStored().afterSaidNotFromGooglePlay(BOTH)

        assertEquals(MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY, said.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY, said.of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(null, said.lastAttemptAtMs)
        // Still not from Google Play: still reached, still not asked.
        assertEquals(
            ReviewJudgement.NotAsked(BOTH, NotAskedWhy.NOT_FROM_GOOGLE_PLAY),
            atHome(150, said, fromGooglePlay = false),
        )
        // Google Play has updated the copy: it asks, the same day.
        assertEquals(ReviewJudgement.Ask(BOTH), atHome(150, said, fromGooglePlay = true))
    }

    @Test
    fun `saying so never takes back a moment that was asked for`() {
        val fiveAsked = stored(ReviewMoment.FIVE_TRIPS)

        val after = fiveAsked.afterSaidNotFromGooglePlay(BOTH)

        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY, after.of(ReviewMoment.HUNDRED_TRIPS))
    }

    @Test
    fun `the install source comes before everything else that holds a moment back`() {
        // Not from Google Play, driving, and asked once today: the line the Log gets is the one
        // that will still be true tomorrow.
        val judged =
            atHome(
                5,
                ReviewsStored(lastAttemptAtMs = at(11, 8)),
                fromGooglePlay = false,
                mayBeDriving = true,
            )

        assertEquals(ReviewJudgement.NotAsked(FIVE, NotAskedWhy.NOT_FROM_GOOGLE_PLAY), judged)
    }

    // ---- Never while he may be driving ----------------------------------------------------------

    @Test
    fun `nothing is asked while a drive may be under way, and nothing is used up`() {
        val driving = NotAskedWhy.MAY_BE_DRIVING
        assertEquals(ReviewJudgement.NotAsked(FIVE, driving), atHome(5, mayBeDriving = true))
        assertEquals(ReviewJudgement.NotAsked(REPORT, driving), afterISentIt(mayBeDriving = true))
        // The drive over, it is asked for.
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(5, mayBeDriving = false))
    }

    @Test
    fun `a drive may be under way while a trip is recorded, a vehicle connected, or MilO waits`() {
        assertFalse(mayBeDriving(tripOpen = false, vehicleConnected = false, false))

        assertTrue(mayBeDriving(tripOpen = true, vehicleConnected = false, false))
        assertTrue(mayBeDriving(tripOpen = false, vehicleConnected = true, false))
        // Beside a parked truck that Android Auto alone holds, Bluetooth gone.
        assertTrue(mayBeDriving(tripOpen = false, vehicleConnected = false, true))
        // A trip started with the button, in a vehicle that is not paired.
        assertTrue(mayBeDriving(tripOpen = true, vehicleConnected = false, false))
    }

    @Test
    fun `not knowing yet whether a vehicle is connected counts as a drive`() {
        assertTrue(mayBeDriving(tripOpen = false, vehicleConnected = null, false))
    }

    // ---- What counts as asked -------------------------------------------------------------------

    @Test
    fun `a launch counts every moment it was for as asked, and keeps the others`() {
        val before =
            ReviewsStored(
                mapOf(
                    ReviewMoment.REPORT_SENT to MomentStanding.ASKED,
                    ReviewMoment.FIVE_TRIPS to MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY,
                ),
                lastAttemptAtMs = at(3, 9),
            )

        val after = before.afterLaunch(FIVE, at(11, 12))

        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.ASKED, after.of(ReviewMoment.REPORT_SENT))
        assertEquals(MomentStanding.OPEN, after.of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(at(11, 12), after.lastAttemptAtMs)
    }

    @Test
    fun `the three moments in a row, as a new phone meets them`() {
        var stored = ReviewsStored()

        // Day 11: the fifth trip. Asked.
        assertEquals(ReviewJudgement.Ask(FIVE), atHome(5, stored, nowMs = at(11, 17)))
        stored = stored.afterLaunch(FIVE, at(11, 17))
        // The same evening the month's report is sent: held, and that moment passes.
        assertEquals(
            ReviewJudgement.NotAsked(REPORT, NotAskedWhy.ONCE_TODAY_ALREADY),
            afterISentIt(trips = 5, stored = stored, nowMs = at(11, 20)),
        )
        // Day 20: 100 trips. Asked.
        assertEquals(ReviewJudgement.NothingReached, atHome(99, stored, nowMs = at(19, 17)))
        assertEquals(ReviewJudgement.Ask(HUNDRED), atHome(100, stored, nowMs = at(20, 17)))
        stored = stored.afterLaunch(HUNDRED, at(20, 17))
        // Day 30: a report is sent again. Asked.
        assertEquals(
            ReviewJudgement.Ask(REPORT),
            afterISentIt(trips = 140, stored = stored, nowMs = at(30, 9)),
        )
        stored = stored.afterLaunch(REPORT, at(30, 9))

        // And never again, for anything.
        assertEquals(ReviewJudgement.NothingReached, atHome(10_000, stored, nowMs = at(31, 9)))
        assertEquals(
            ReviewJudgement.NothingReached,
            afterISentIt(trips = 10_000, stored = stored, nowMs = at(31, 9)),
        )
    }
}
