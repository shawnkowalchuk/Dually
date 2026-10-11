package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.review.ReviewPrompts
import com.shawnkowalchuk.milo.platform.review.StandInCard
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(day: Int, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(2026, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

/**
 * The review card on a phone whose date is set a day ahead by hand for a few seconds (ADR-006:
 * Shawn does it for lives in a game, several times an evening). "Never twice in one day" goes
 * by the day, so this is where the trick could let a second request through, the same evening.
 * The real [ReviewPrompts] runs on MilO's real clock (`TrustedClock`, on a [JumpingPhone]).
 */
class ClockJumpReviewTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log = FakeEventLogDao()
    private val settings = SettingsStore(FakeSettingsFile())
    private val trips = MutableStateFlow(0)
    private val card = StandInCard()

    private fun promptsOn(phone: JumpingPhone) = ReviewPrompts(
        settings = settings,
        recordedTrips = trips,
        mayBeDriving = MutableStateFlow(false),
        fromGooglePlay = { true },
        eventLog = EventLogRepository(log),
        crashFileStore = CrashFileStore(folder.root),
        clock = { phone.clock.now() },
        zone = { EDMONTON },
    )

    @Test
    fun `the date set a day ahead lets no second request through the same evening`() = runTest {
        val phone = JumpingPhone(at(11, 19, 30))
        val prompts = promptsOn(phone)
        prompts.ask(ReviewOccasion.REPORT_SENT, card) { true }
        assertEquals(1, card.launches)
        trips.value = 5

        // The trick: for these seconds the phone says it is tomorrow evening.
        phone.advance(60_000)
        phone.setAhead()
        phone.advance(4_000)
        assertEquals(at(12, 19, 31) + 4_000, phone.phoneNowMs)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
        prompts.ask(ReviewOccasion.HOME, card) { true }
        phone.advance(8_000)
        phone.setBack()
        prompts.ask(ReviewOccasion.HOME, card) { true }

        assertEquals(1, card.requests)
        assertEquals(MomentStanding.OPEN, settings.current().reviews.of(ReviewMoment.FIVE_TRIPS))
        // The launch is dated by MilO's own time.
        assertEquals(at(11, 19, 30), settings.current().reviews.lastAttemptAtMs)
    }

    @Test
    fun `a request made inside the trick is dated today, and tomorrow is free`() = runTest {
        val phone = JumpingPhone(at(11, 19, 30))
        val prompts = promptsOn(phone)
        trips.value = 5

        phone.setAhead()
        phone.advance(4_000)
        prompts.ask(ReviewOccasion.HOME, card) { true }
        phone.advance(8_000)
        phone.setBack()

        assertEquals(at(11, 19, 30) + 4_000, settings.current().reviews.lastAttemptAtMs)
        // The rest of the 11th is held, and the report sent the next morning is asked for.
        phone.advance(3_600_000)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))
        phone.advance(12 * 3_600_000L)
        assertEquals(setOf(ReviewMoment.REPORT_SENT), prompts.look(ReviewOccasion.REPORT_SENT))
    }
}
