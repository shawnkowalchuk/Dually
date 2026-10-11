package com.shawnkowalchuk.milo.feature.sentrun

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.sentrun.RunSound
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the mascot runs across the Report screen, and what is heard of it: once for each "I sent
 * it", over the Report screen only, with its two sounds started by the animation and by nothing
 * else, silent when both of MilO's sounds are switched off, and ended with its sounds when the
 * screen is left or the drawing says it was cut short.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class SentRunViewModelTest {
    private val log = FakeEventLogDao()
    private val sent = MutableStateFlow(0)
    private var soundsOn = true
    private var settingsUnreadable = false

    /** What the player was asked for, in order: "prepare", a sound started, or "stop". */
    private val heard = mutableListOf<String>()

    /** MilO with the Report screen on top, as it is when "Did you send it?" is answered. */
    private fun TestScope.onTheReportScreen(): SentRunViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            SentRunViewModel(
                sent = sent,
                soundsHeard = {
                    if (settingsUnreadable) throw IOException("the settings cannot be read")
                    soundsOn
                },
                prepare = { heard += "prepare" },
                play = { heard += it.name },
                stop = { heard += "stop" },
                eventLog = EventLogRepository(log),
                clock = { 42L },
            )
        viewModel.onScreens(onReport = true)
        runCurrent()
        return viewModel
    }

    /** "I sent it" has recorded a report as sent. */
    private fun TestScope.iSentIt() {
        sent.update { it + 1 }
        runCurrent()
    }

    /** The animation, played from its first frame to its last. */
    private fun SentRunViewModel.playedThrough() {
        onArrived()
        onBraked()
        onDone(whole = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- Once for each "I sent it" ---------------------------------------------------------------

    @Test
    fun `nothing runs until a report is recorded as sent`() = runTest {
        val viewModel = onTheReportScreen()

        assertFalse(viewModel.running.value)
        assertTrue(heard.isEmpty())
    }

    @Test
    fun `I sent it asks for one run, and it is over when the drawing says so`() = runTest {
        val viewModel = onTheReportScreen()

        iSentIt()
        assertTrue(viewModel.running.value)

        viewModel.playedThrough()
        assertFalse(viewModel.running.value)
        // It does not come back by itself.
        runCurrent()
        viewModel.onScreens(onReport = true)
        assertFalse(viewModel.running.value)
    }

    @Test
    fun `each report that is answered I sent it has its own run`() = runTest {
        val viewModel = onTheReportScreen()

        repeat(3) {
            iSentIt()
            assertTrue(viewModel.running.value)
            viewModel.playedThrough()
            assertFalse(viewModel.running.value)
        }

        assertEquals(List(3) { listOf("prepare", "WHIRR", "SQUEAL") }.flatten(), heard)
    }

    @Test
    fun `reports sent before this screen was built ask for nothing`() = runTest {
        // The activity was built again while MilO ran: the count stands at what was answered
        // before, and that is no new answer.
        sent.value = 2

        val viewModel = onTheReportScreen()

        assertFalse(viewModel.running.value)
        iSentIt()
        assertTrue(viewModel.running.value)
    }

    @Test
    fun `an answer that comes while he runs does not start him again under himself`() = runTest {
        val viewModel = onTheReportScreen()
        iSentIt()
        viewModel.onArrived()

        iSentIt()
        viewModel.onBraked()
        viewModel.onDone(whole = true)

        assertFalse(viewModel.running.value)
        assertEquals(listOf("prepare", "WHIRR", "SQUEAL"), heard)
    }

    // ---- The sounds -----------------------------------------------------------------------------

    @Test
    fun `the whirr is started as he gets to the spot and the squeal as he brakes`() = runTest {
        val viewModel = onTheReportScreen()
        iSentIt()
        // Asking for a run makes its sounds ready, and plays nothing: no sound is started
        // before the animation says so.
        assertEquals(listOf("prepare"), heard)

        viewModel.onArrived()
        assertEquals(listOf("prepare", "WHIRR"), heard)

        viewModel.onBraked()
        assertEquals(listOf("prepare", "WHIRR", "SQUEAL"), heard)
    }

    @Test
    fun `a run that plays to its end lets the squeal ring out`() = runTest {
        val viewModel = onTheReportScreen()
        iSentIt()

        viewModel.playedThrough()

        assertEquals(listOf("prepare", "WHIRR", "SQUEAL"), heard)
    }

    @Test
    fun `with both of MilO's sounds switched off he runs in silence`() = runTest {
        soundsOn = false
        val viewModel = onTheReportScreen()
        iSentIt()
        assertTrue(viewModel.running.value)

        viewModel.playedThrough()

        assertTrue(heard.isEmpty())
    }

    @Test
    fun `the switches are read anew for every run`() = runTest {
        val viewModel = onTheReportScreen()
        iSentIt()
        viewModel.playedThrough()

        soundsOn = false
        iSentIt()
        viewModel.playedThrough()
        soundsOn = true
        iSentIt()
        viewModel.playedThrough()

        val once = listOf("prepare", "WHIRR", "SQUEAL")
        assertEquals(once + once, heard)
    }

    @Test
    fun `settings that cannot be read give a run without its sounds`() = runTest {
        settingsUnreadable = true
        val viewModel = onTheReportScreen()
        iSentIt()

        viewModel.playedThrough()

        assertTrue(heard.isEmpty())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a sound whose moment comes before the switches are read is not played`() = runTest {
        val read = CompletableDeferred<Boolean>()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            SentRunViewModel(
                sent = sent,
                soundsHeard = { read.await() },
                prepare = { heard += "prepare" },
                play = { heard += it.name },
                stop = { heard += "stop" },
                eventLog = EventLogRepository(log),
                clock = { 42L },
            )
        viewModel.onScreens(onReport = true)
        runCurrent()
        iSentIt()

        viewModel.onArrived()
        read.complete(true)
        runCurrent()
        viewModel.onBraked()

        assertEquals(listOf("prepare", "SQUEAL"), heard)
    }

    @Test
    fun `no sound is started for a run that is over, or for none`() = runTest {
        val viewModel = onTheReportScreen()

        viewModel.onArrived()
        viewModel.onBraked()
        iSentIt()
        viewModel.onDone(whole = true)
        viewModel.onArrived()
        viewModel.onBraked()

        assertEquals(listOf("prepare"), heard)
    }

    // ---- Left, it is over -----------------------------------------------------------------------

    @Test
    fun `leaving the Report screen ends the run and its sound, in each part of it`() = runTest {
        // While he runs in, while he runs on the spot, and while he slides.
        for (part in 0..2) {
            heard.clear()
            val viewModel = onTheReportScreen()
            iSentIt()
            if (part >= 1) viewModel.onArrived()
            if (part >= 2) viewModel.onBraked()

            viewModel.onScreens(onReport = false)

            assertFalse("part $part", viewModel.running.value)
            assertEquals("part $part", "stop", heard.last())
            // The drawing leaves the screen with it, and says so: nothing more happens.
            viewModel.onDone(whole = false)
            assertEquals("part $part", 1, heard.count { it == "stop" })
            // Back on the Report screen: he does not come back.
            viewModel.onScreens(onReport = true)
            runCurrent()
            assertFalse("part $part", viewModel.running.value)
        }
    }

    @Test
    fun `a run the drawing cut short is over, and its sound is stopped`() = runTest {
        // MilO was put away in the middle, or the phone's animations are switched off.
        val viewModel = onTheReportScreen()
        iSentIt()
        viewModel.onArrived()

        viewModel.onDone(whole = false)

        assertFalse(viewModel.running.value)
        assertEquals(listOf("prepare", "WHIRR", "stop"), heard)
        // Told twice, it is still over once.
        viewModel.onDone(whole = false)
        assertEquals(listOf("prepare", "WHIRR", "stop"), heard)
    }

    @Test
    fun `with the phone's animations off it is over at once, and nothing is heard`() = runTest {
        val viewModel = onTheReportScreen()
        iSentIt()

        // What the drawing does then: it ends before anything is read or drawn. The sounds
        // that were made ready are let go of, and none was started.
        viewModel.onDone(whole = false)

        assertFalse(viewModel.running.value)
        assertEquals(listOf("prepare", "stop"), heard)
    }

    @Test
    fun `an answer on a screen that has been left since asks for nothing`() = runTest {
        val viewModel = onTheReportScreen()
        viewModel.onScreens(onReport = false)

        iSentIt()

        assertFalse(viewModel.running.value)
        viewModel.onScreens(onReport = true)
        runCurrent()
        assertFalse(viewModel.running.value)
        assertTrue(heard.isEmpty())
    }

    // ---- Pictures that cannot be read -----------------------------------------------------------

    @Test
    fun `pictures that cannot be read are one line in the Log, and the run is over`() = runTest {
        val viewModel = onTheReportScreen()
        iSentIt()

        viewModel.onUnreadable(IOException("mascot_run_in is not a picture"))
        viewModel.onDone(whole = false)
        runCurrent()

        assertFalse(viewModel.running.value)
        val line = log.entries.single()
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals(
            "MilO's mascot did not run across the Report screen: a picture of him could not " +
                "be read",
            line.message,
        )
        assertTrue(line.detail.orEmpty().contains("mascot_run_in is not a picture"))
        assertEquals(listOf("prepare", "stop"), heard)
    }
}
