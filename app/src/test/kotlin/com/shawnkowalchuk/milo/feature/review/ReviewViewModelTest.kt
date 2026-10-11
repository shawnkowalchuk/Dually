package com.shawnkowalchuk.milo.feature.review

import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.review.ReviewPrompts
import com.shawnkowalchuk.milo.platform.review.StandInCard
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(day: Int, hour: Int): Long =
    LocalDateTime.of(2026, 10, day, hour, 0).atZone(EDMONTON).toInstant().toEpochMilli()

private val FIVE = setOf(ReviewMoment.FIVE_TRIPS)
private val REPORT = setOf(ReviewMoment.REPORT_SENT)

/**
 * When the screens let MilO ask Google Play for its review card, through the ViewModel and the
 * real [ReviewPrompts], with the card replaced by a stand-in: Home in front and free, a trip
 * that holds the moment back, the Report screen after "I sent it", and the place where the
 * app plays something first.
 */
// See GreetingViewModelTest for why the main dispatcher is set and runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log = FakeEventLogDao()
    private val settings = SettingsStore(FakeSettingsFile())
    private val trips = MutableStateFlow(0)
    private val driving = MutableStateFlow(false)
    private var nowMs = at(11, 12)
    private val card = StandInCard()

    // Made on first use: the folder of the rule above is not there until a test runs.
    private val prompts by lazy {
        ReviewPrompts(
            settings = settings,
            recordedTrips = trips,
            mayBeDriving = driving,
            fromGooglePlay = { true },
            eventLog = EventLogRepository(log),
            crashFileStore = CrashFileStore(folder.root),
            clock = { nowMs },
            zone = { EDMONTON },
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** MilO's screens as a start builds them. Nothing is in front yet. */
    private fun TestScope.screens(): ReviewViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return ReviewViewModel(prompts).also { runCurrent() }
    }

    /** Home on top, with nothing playing over it, and MilO on the screen. */
    private fun ReviewViewModel.homeInFront() {
        onScreens(homeFree = true, onReport = false)
        onInFront(true)
    }

    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    /** What the app does with a moment that is due: nothing to play, so it is over at once. */
    private fun TestScope.act(viewModel: ReviewViewModel) {
        val due = viewModel.due.value ?: return
        viewModel.onCelebrationOver(due, card)
        runCurrent()
    }

    // ---- Home -----------------------------------------------------------------------------------

    @Test
    fun `five trips reached and Home opened - Google Play is asked once Home has settled`() =
        runTest {
            trips.value = 5
            val viewModel = screens()

            viewModel.homeInFront()
            runCurrent()
            // Not in the instant Home appears.
            assertNull(viewModel.due.value)
            advanceTimeBy(SETTLE_MS - 1)
            assertNull(viewModel.due.value)

            settle()
            assertEquals(ReviewDue(ReviewOccasion.HOME, FIVE), viewModel.due.value)
            // Due is not asked: that waits for whatever the app plays first.
            assertEquals(0, card.requests)

            act(viewModel)
            assertEquals(1, card.launches)
            assertNull(viewModel.due.value)
            assertEquals(
                MomentStanding.ASKED,
                settings.current().reviews.of(ReviewMoment.FIVE_TRIPS),
            )
        }

    @Test
    fun `with four trips Home is opened and nothing happens at all`() = runTest {
        trips.value = 4
        val viewModel = screens()

        viewModel.homeInFront()
        settle()
        act(viewModel)

        assertNull(viewModel.due.value)
        assertEquals(0, card.requests)
        assertEquals(MiloSettings(), settings.current())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `the fifth trip ending under an open Home is asked for there and then`() = runTest {
        trips.value = 4
        val viewModel = screens()
        viewModel.homeInFront()
        settle()
        assertNull(viewModel.due.value)

        trips.value = 5
        runCurrent()
        assertNull(viewModel.due.value)
        settle()

        assertEquals(ReviewDue(ReviewOccasion.HOME, FIVE), viewModel.due.value)
    }

    @Test
    fun `a trip being recorded holds the moment back, and the end of the drive brings it`() =
        runTest {
            trips.value = 5
            driving.value = true
            val viewModel = screens()

            viewModel.homeInFront()
            settle()
            advanceTimeBy(60 * 60 * 1000L)
            assertNull(viewModel.due.value)
            assertEquals(0, card.requests)
            assertTrue(log.entries.isEmpty())

            // The trip ends and the truck is gone, with Home still open.
            driving.value = false
            settle()
            assertEquals(ReviewDue(ReviewOccasion.HOME, FIVE), viewModel.due.value)
        }

    @Test
    fun `a trip that starts while the moment is due takes it back`() = runTest {
        trips.value = 5
        val viewModel = screens()
        viewModel.homeInFront()
        settle()
        val due = viewModel.due.value

        driving.value = true
        runCurrent()

        assertNull(viewModel.due.value)
        // What the app was playing ends a moment later: nothing is asked.
        viewModel.onCelebrationOver(due!!, card)
        settle()
        assertEquals(0, card.requests)
    }

    @Test
    fun `while something plays over Home, or Home is not on top, nothing is due`() = runTest {
        trips.value = 5
        val viewModel = screens()
        viewModel.onInFront(true)

        // The greeting is on, or the party, or What's new is about to open, or Setup is on top.
        viewModel.onScreens(homeFree = false, onReport = false)
        settle()
        advanceTimeBy(30_000)
        assertNull(viewModel.due.value)

        // It is over.
        viewModel.onScreens(homeFree = true, onReport = false)
        settle()
        assertEquals(ReviewDue(ReviewOccasion.HOME, FIVE), viewModel.due.value)
    }

    @Test
    fun `a Home that is left before it has settled is not acted on`() = runTest {
        trips.value = 5
        val viewModel = screens()
        viewModel.homeInFront()
        advanceTimeBy(SETTLE_MS / 2)

        // A press of the bar's Trips button.
        viewModel.onScreens(homeFree = false, onReport = false)
        settle()
        advanceTimeBy(30_000)

        assertNull(viewModel.due.value)
        assertEquals(0, card.requests)
    }

    @Test
    fun `MilO put away takes the moment back, and it is due again when MilO is back`() = runTest {
        trips.value = 5
        val viewModel = screens()
        viewModel.homeInFront()
        settle()
        val due = viewModel.due.value!!

        viewModel.onInFront(false)
        runCurrent()
        assertNull(viewModel.due.value)
        viewModel.onCelebrationOver(due, card)
        runCurrent()
        assertEquals(0, card.requests)

        viewModel.onInFront(true)
        settle()
        assertEquals(due, viewModel.due.value)
    }

    @Test
    fun `Home left while Google Play is asked - the card is not shown, and nothing is used up`() =
        runTest {
            trips.value = 5
            val viewModel = screens()
            viewModel.homeInFront()
            settle()
            card.answer = {
                // The bar's Trips button is pressed while the Play Store answers.
                viewModel.onScreens(homeFree = false, onReport = false)
                card.given()
            }

            act(viewModel)

            assertEquals(1, card.requests)
            assertEquals(0, card.launches)
            assertEquals(MiloSettings(), settings.current())
        }

    @Test
    fun `once asked, coming back to Home asks nothing more`() = runTest {
        trips.value = 5
        val viewModel = screens()
        viewModel.homeInFront()
        settle()
        act(viewModel)

        // Google Play's card covers MilO, and closes.
        viewModel.onInFront(false)
        runCurrent()
        viewModel.onInFront(true)
        settle()
        act(viewModel)
        // And MilO is opened again the next day, with more trips.
        nowMs = at(12, 8)
        trips.value = 9
        settle()
        act(viewModel)

        assertNull(viewModel.due.value)
        assertEquals(1, card.requests)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun `nothing is watched or read while no screen is fit for the card`() = runTest {
        trips.value = 5
        val viewModel = screens()
        // MilO is not in front: a trip may be recorded behind it, and this reads nothing.
        assertEquals(0, trips.subscriptionCount.value)
        assertEquals(0, driving.subscriptionCount.value)

        // Trips is on top, or the greeting plays.
        viewModel.onInFront(true)
        viewModel.onScreens(homeFree = false, onReport = false)
        runCurrent()
        assertEquals(0, trips.subscriptionCount.value)

        // Home is free: now the number of trips is watched.
        viewModel.onScreens(homeFree = true, onReport = false)
        runCurrent()
        assertEquals(1, trips.subscriptionCount.value)
        assertEquals(1, driving.subscriptionCount.value)

        // MilO is put away: the watch ends.
        viewModel.onInFront(false)
        runCurrent()
        assertEquals(0, trips.subscriptionCount.value)
        assertEquals(0, driving.subscriptionCount.value)
        assertTrue(log.entries.isEmpty())
    }

    // ---- The Report screen ----------------------------------------------------------------------

    @Test
    fun `I sent it on the Report screen - Google Play is asked there, once`() = runTest {
        val viewModel = screens()
        viewModel.onInFront(true)
        viewModel.onScreens(homeFree = false, onReport = true)
        settle()
        assertNull(viewModel.due.value)

        prompts.onReportSentByAnswer()
        settle()
        assertEquals(ReviewDue(ReviewOccasion.REPORT_SENT, REPORT), viewModel.due.value)

        act(viewModel)
        assertEquals(1, card.launches)
        assertFalse(prompts.reportJustSent.value)
        assertEquals(MomentStanding.ASKED, settings.current().reviews.of(ReviewMoment.REPORT_SENT))

        // The next report, a month on: nothing, and the moment does not stay in memory.
        nowMs = at(30, 9)
        prompts.onReportSentByAnswer()
        settle()
        assertNull(viewModel.due.value)
        assertFalse(prompts.reportJustSent.value)
        assertEquals(1, card.requests)
    }

    @Test
    fun `the Report screen without I sent it asks nothing, with any number of trips`() = runTest {
        trips.value = 150
        val viewModel = screens()
        viewModel.onInFront(true)

        // Opened, a PDF looked at, "Mark as sent" pressed: none of it is "I sent it".
        viewModel.onScreens(homeFree = false, onReport = true)
        settle()
        advanceTimeBy(60_000)

        assertNull(viewModel.due.value)
        assertEquals(0, card.requests)
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `the Report screen left before it settled - the report's moment has passed`() = runTest {
        val viewModel = screens()
        viewModel.onInFront(true)
        viewModel.onScreens(homeFree = false, onReport = true)
        prompts.onReportSentByAnswer()
        advanceTimeBy(SETTLE_MS / 2)

        // Back is pressed: Trips is on top, then Home.
        viewModel.onScreens(homeFree = false, onReport = false)
        viewModel.onScreens(homeFree = true, onReport = false)
        settle()

        assertFalse(prompts.reportJustSent.value)
        assertNull(viewModel.due.value)
        assertEquals(0, card.requests)
        assertEquals(MiloSettings(), settings.current())
    }

    @Test
    fun `the same day's second moment is held on the Report screen, and passes`() = runTest {
        trips.value = 5
        val viewModel = screens()
        viewModel.homeInFront()
        settle()
        act(viewModel)

        // That afternoon: the report is sent.
        nowMs = at(11, 16)
        viewModel.onScreens(homeFree = false, onReport = true)
        prompts.onReportSentByAnswer()
        settle()
        act(viewModel)

        assertNull(viewModel.due.value)
        assertFalse(prompts.reportJustSent.value)
        assertEquals(1, card.requests)
        assertEquals(MomentStanding.OPEN, settings.current().reviews.of(ReviewMoment.REPORT_SENT))
        assertTrue(log.entries.last().message.contains("once already today"))
    }

    // ---- When Home is free ----------------------------------------------------------------------

    @Test
    fun `Home is free only when it is drawn and nothing else has the moment`() {
        assertTrue(homeIsFree(homeDrawn = true, true, partyHasTheScreen = false, false))

        // The first start's page, Setup, or any screen on top of Home.
        assertFalse(homeIsFree(homeDrawn = false, true, partyHasTheScreen = false, false))
        // The opening greeting has not gone yet.
        assertFalse(homeIsFree(homeDrawn = true, false, partyHasTheScreen = false, false))
        // The one-year party plays or is owed, or that is not known yet.
        assertFalse(homeIsFree(homeDrawn = true, true, partyHasTheScreen = true, false))
        assertFalse(homeIsFree(homeDrawn = true, true, partyHasTheScreen = null, false))
        // What's new is about to open, or a tapped notification waits.
        assertFalse(homeIsFree(homeDrawn = true, true, partyHasTheScreen = false, true))
    }
}
