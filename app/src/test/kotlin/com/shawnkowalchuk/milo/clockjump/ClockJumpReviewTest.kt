package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.review.ReviewFlow
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

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

/**
 * The review card on a phone whose date is set a day ahead by hand for a few seconds (ADR-006:
 * Shawn does it for lives in a game, several times an evening). The thirty days after a launch
 * are counted in days, and "never twice in one day" goes by the day: this is where the trick
 * could let a request through a day early, or a second one the same evening. The real
 * [ReviewPrompts] runs on MilO's real clock (`TrustedClock`, on a [JumpingPhone]).
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
        assertEquals(at(11, 19, 30), settings.current().reviews.lastLaunchAtMs)
    }

    @Test
    fun `the date set a day ahead on the last evening lets nothing through a day early`() =
        runTest {
            val phone = JumpingPhone(at(11, 19, 30))
            val prompts = promptsOn(phone)
            prompts.ask(ReviewOccasion.REPORT_SENT, card) { true }
            trips.value = 5

            // Twenty-nine days and two hours on. The trick: for these seconds the phone says
            // that the thirty days were over twenty-two hours ago.
            phone.advance(29 * DAY_MS + 2 * HOUR_MS)
            phone.setAhead()
            phone.advance(4_000)
            assertEquals(at(11, 19, 30) + 30 * DAY_MS + 2 * HOUR_MS + 4_000, phone.phoneNowMs)
            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
            prompts.ask(ReviewOccasion.HOME, card) { true }
            phone.advance(8_000)
            phone.setBack()
            prompts.ask(ReviewOccasion.HOME, card) { true }
            assertEquals(1, card.requests)

            // The next evening, when they really are over: asked.
            phone.advance(22 * HOUR_MS)
            assertEquals(setOf(ReviewMoment.FIVE_TRIPS), prompts.look(ReviewOccasion.HOME))
            prompts.ask(ReviewOccasion.HOME, card) { true }
            assertEquals(2, card.launches)
        }

    @Test
    fun `a launch made inside the trick is dated today, and its thirty days run from today`() =
        runTest {
            val phone = JumpingPhone(at(11, 19, 30))
            val prompts = promptsOn(phone)
            trips.value = 5

            phone.setAhead()
            phone.advance(4_000)
            prompts.ask(ReviewOccasion.HOME, card) { true }
            phone.advance(8_000)
            phone.setBack()

            val launchedAt = at(11, 19, 30) + 4_000
            assertEquals(launchedAt, settings.current().reviews.lastAttemptAtMs)
            assertEquals(launchedAt, settings.current().reviews.lastLaunchAtMs)
            // A report sent the next morning is held, and so is one sent 29 days after that.
            phone.advance(13 * HOUR_MS)
            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))
            phone.advance(29 * DAY_MS)
            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))
            // Thirty days after the launch as MilO dated it, not thirty-one: asked.
            phone.advance(11 * HOUR_MS)
            assertEquals(launchedAt + 30 * DAY_MS + 8_000, phone.clock.now())
            assertEquals(setOf(ReviewMoment.REPORT_SENT), prompts.look(ReviewOccasion.REPORT_SENT))
        }

    @Test
    fun `a failed request inside the trick is dated today, and tomorrow is free`() = runTest {
        val phone = JumpingPhone(at(11, 19, 30))
        val prompts = promptsOn(phone)
        trips.value = 5
        card.answer = { ReviewFlow.NotGiven("the Play Store app was not found on this phone") }

        phone.setAhead()
        phone.advance(4_000)
        prompts.ask(ReviewOccasion.HOME, card) { true }
        phone.advance(8_000)
        phone.setBack()

        assertEquals(at(11, 19, 30) + 4_000, settings.current().reviews.lastAttemptAtMs)
        assertEquals(null, settings.current().reviews.lastLaunchAtMs)
        // The rest of the 11th is held, and the next morning is free.
        phone.advance(HOUR_MS)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
        phone.advance(12 * HOUR_MS)
        assertEquals(setOf(ReviewMoment.FIVE_TRIPS), prompts.look(ReviewOccasion.HOME))
    }
}
