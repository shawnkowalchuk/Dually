package com.shawnkowalchuk.milo.app

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.FakeSentReportDao
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.feature.report.ReportRecords
import com.shawnkowalchuk.milo.feature.review.ReviewDue
import com.shawnkowalchuk.milo.feature.review.ReviewViewModel
import com.shawnkowalchuk.milo.feature.review.SETTLE_MS
import com.shawnkowalchuk.milo.feature.sentrun.SentRunViewModel
import com.shawnkowalchuk.milo.platform.review.ReviewPrompts
import com.shawnkowalchuk.milo.platform.review.StandInCard
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Report feature's answer "I sent it", the mascot's run and Google Play's review card, put
 * together as the app puts them together (`MiloReportEntry.kt`, `SentRunObjects.kt`,
 * `ReviewObjects.kt`): which answers start the run, and that the review card's turn comes after
 * the run, once. The real list of sent reports, the real ViewModels and the real rules of the
 * review card; the drawing and Google Play's card are stood in for.
 */
// See GreetingViewModelTest for why the main dispatcher is set and runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class SentRunAtISentItTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeSentReportDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_300_000_000L
    private val settings = SettingsStore(FakeSettingsFile())
    private val driving = MutableStateFlow(false)
    private val card = StandInCard()

    /** What `SentRunObjects` keeps: how many reports "I sent it" has recorded as sent. */
    private val sent = MutableStateFlow(0)

    // Made on first use: the folder of the rule above is not there until a test runs.
    private val prompts by lazy {
        ReviewPrompts(
            settings = settings,
            recordedTrips = MutableStateFlow(2),
            mayBeDriving = driving,
            fromGooglePlay = { true },
            eventLog = EventLogRepository(log),
            crashFileStore = CrashFileStore(folder.root),
            clock = { nowMs },
            zone = { ZoneId.of("America/Edmonton") },
        )
    }

    /** The list of sent reports, telling the app what `MiloReportEntry` has it tell. */
    private val records by lazy {
        ReportRecords(
            sent = SentReportRepository(dao),
            settings = settings,
            eventLog = EventLogRepository(log),
            onSentByAnswer = {
                sent.update { it + 1 }
                prompts.onReportSentByAnswer()
            },
            clock = { nowMs },
        )
    }
    private val month = ReportPeriod.Month(YearMonth.of(2026, 9))
    private val handedOver =
        ReportHandOver(month, 21, 2314, nowMs - 60_000, DistanceUnit.KILOMETRES)

    /** How often the review card's turn was said to have come. */
    private var turns = 0

    /** Whether the Report screen is the screen on top, as the app's own level knows it. */
    private var onReport = true

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Screens(val run: SentRunViewModel, val review: ReviewViewModel)

    /** MilO in front with the Report screen on top, as when "Did you send it?" is asked. */
    private fun TestScope.onTheReportScreen(): Screens {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val run =
            SentRunViewModel(sent, { true }, {}, {}, {}, EventLogRepository(log)) { nowMs }
        val review = ReviewViewModel(prompts)
        run.onScreens(onReport = true)
        review.onScreens(homeFree = false, onReport = true)
        review.onInFront(true)
        runCurrent()
        return Screens(run, review)
    }

    /** What `CelebrationThenCard` does with a moment that is due, for as long as it is due. */
    private fun TestScope.celebrationThenCard(screens: Screens, due: ReviewDue): Job = launch {
        afterCelebration(due, screens.run.running, { onReport }) {
            turns++
            screens.review.onCelebrationOver(due, card)
        }
    }

    // ---- Which answers start the run ------------------------------------------------------------

    @Test
    fun `I sent it starts the run, once the report is in the list`() = runTest {
        val screens = onTheReportScreen()
        settings.setReportHandOver(handedOver)

        assertTrue(records.answered(handedOver, sent = true))
        runCurrent()

        assertEquals(1, dao.rows.size)
        assertTrue(screens.run.running.value)
    }

    @Test
    fun `Not sent starts nothing`() = runTest {
        val screens = onTheReportScreen()
        settings.setReportHandOver(handedOver)

        assertTrue(records.answered(handedOver, sent = false))
        runCurrent()

        assertFalse(screens.run.running.value)
    }

    @Test
    fun `Mark as sent starts nothing, though it writes the same row`() = runTest {
        val screens = onTheReportScreen()

        assertNotNull(records.markedSent(month, 21, 2314, DistanceUnit.KILOMETRES))
        runCurrent()

        assertEquals(1, dao.rows.size)
        assertFalse(screens.run.running.value)
    }

    @Test
    fun `removing a sent report, and an answer that could not be stored, start nothing`() =
        runTest {
            val screens = onTheReportScreen()
            val stored = records.markedSent(month, 21, 2314, DistanceUnit.KILOMETRES)

            assertTrue(records.removed(stored!!.id))
            dao.failNextInsert = IllegalStateException("the disk is full")
            assertFalse(records.answered(handedOver, sent = true))
            runCurrent()

            assertFalse(screens.run.running.value)
        }

    // ---- The review card's turn comes after the run, once ---------------------------------------

    @Test
    fun `Google Play is asked when the run is over, and not while he runs`() = runTest {
        val screens = onTheReportScreen()
        records.answered(handedOver, sent = true)
        runCurrent()
        assertTrue(screens.run.running.value)

        // A second on, the review card's moment is due. He is still running.
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        val due = screens.review.due.value
        assertEquals(ReviewDue(ReviewOccasion.REPORT_SENT, setOf(ReviewMoment.REPORT_SENT)), due)
        celebrationThenCard(screens, due!!)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(0, turns)
        assertEquals(0, card.requests)

        // The last of him and of his smoke has left the screen.
        screens.run.onDone(whole = true)
        runCurrent()

        assertEquals(1, turns)
        assertEquals(1, card.launches)
        assertNull(screens.review.due.value)
        val stored = settings.current().reviews
        assertEquals(MomentStanding.ASKED, stored.of(ReviewMoment.REPORT_SENT))
    }

    @Test
    fun `its turn comes once, whatever the run does afterwards`() = runTest {
        val screens = onTheReportScreen()
        records.answered(handedOver, sent = true)
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        celebrationThenCard(screens, screens.review.due.value!!)
        screens.run.onDone(whole = true)
        runCurrent()

        // Told again that it is over, and another report answered at once: no second turn.
        screens.run.onDone(whole = true)
        screens.run.onDone(whole = false)
        records.answered(handedOver.copy(atMs = nowMs), sent = true)
        runCurrent()
        screens.run.onDone(whole = true)
        advanceTimeBy(10 * SETTLE_MS)
        runCurrent()

        assertEquals(1, turns)
        assertEquals(1, card.requests)
        assertEquals(1, card.launches)
    }

    @Test
    fun `with no run to play, its turn comes at once, as it did before there was one`() = runTest {
        val screens = onTheReportScreen()
        records.answered(handedOver, sent = true)
        runCurrent()
        // The phone's animations are off, or his pictures cannot be read: the drawing ends
        // itself before anything is drawn.
        screens.run.onDone(whole = false)

        advanceTimeBy(SETTLE_MS)
        runCurrent()
        celebrationThenCard(screens, screens.review.due.value!!)
        runCurrent()

        assertEquals(1, turns)
        assertEquals(1, card.launches)
    }

    @Test
    fun `a run that is cut short by leaving the screen takes the review card's turn with it`() =
        runTest {
            val screens = onTheReportScreen()
            records.answered(handedOver, sent = true)
            advanceTimeBy(SETTLE_MS)
            runCurrent()
            val waiting = celebrationThenCard(screens, screens.review.due.value!!)

            // Back is pressed while he runs: the Report screen is left. The run hears of it
            // first here, which is the order in which the card could have been asked for.
            onReport = false
            screens.run.onScreens(onReport = false)
            runCurrent()
            screens.review.onScreens(homeFree = false, onReport = false)
            runCurrent()
            // The moment is due no longer, and the place that waited for it has left the screen.
            assertNull(screens.review.due.value)
            waiting.cancel()
            runCurrent()

            assertFalse(screens.run.running.value)
            assertEquals(0, turns)
            assertEquals(0, card.requests)
            assertTrue(log.entries.none { it.message.startsWith("Review card") })
            assertEquals(
                MomentStanding.OPEN,
                settings.current().reviews.of(ReviewMoment.REPORT_SENT),
            )
        }

    @Test
    fun `the two numbers of trips wait for no run`() = runTest {
        val screens = onTheReportScreen()
        records.answered(handedOver, sent = true)
        runCurrent()
        assertTrue(screens.run.running.value)

        // A moment over Home has nothing played for it: it is over at once, whatever runs.
        val home = ReviewDue(ReviewOccasion.HOME, setOf(ReviewMoment.FIVE_TRIPS))
        var over = 0
        afterCelebration(home, screens.run.running, { false }) { over++ }

        assertEquals(1, over)
    }
}
