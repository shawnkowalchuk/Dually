package com.shawnkowalchuk.milo.feature.sentrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.sentrun.RunSound
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * When the mascot runs across the Report screen (2026-10-11). Shawn: "i want this animation
 * included. and used when the user completes sending a report by email"; asked when exactly, he
 * chose the answer "I sent it" to "Did you send it?", after the email app, and not "Mark as
 * sent".
 *
 * - **Once for each "I sent it" that has recorded a report as sent,** over the Report screen it
 *   was answered on. [running] says so; the app draws the run while it does, and Google Play's
 *   review card, which belongs to the same moment, waits until it does not (ADR-009).
 * - **Left, it is over.** When the Report screen is left the run ends where it stands, with its
 *   sounds, and does not come back when the screen does. The drawing ends itself the same way
 *   when MilO is put away ([onDone]).
 * - **Its two sounds are started by the animation,** in the drawing of the screen their frame
 *   is first shown in ([onArrived], [onBraked]), and by nothing else: no clock here times one.
 *   They have no switch of their own: **they are silent when both of MilO's sounds are switched
 *   off in Settings,** as the party's pop is.
 *
 * Nothing here knows a trip or a report: it is handed a count, a yes or no about the sounds,
 * and two functions that play and stop one.
 *
 * @param sent how many reports "I sent it" has recorded as sent in this run of MilO's process.
 * A run is asked for each time the number changes; the number it stands at when this is built
 * asks for none.
 * @param soundsHeard whether either of MilO's two sounds is switched on in Settings.
 * @param prepare makes the run's sounds ready to start, when a run is asked for that is heard.
 * @param play starts one of them, on its frame; [stop] ends whichever is playing and lets go of
 * what was made ready.
 * @param clock wall-clock milliseconds, for the event log.
 */
class SentRunViewModel(
    sent: StateFlow<Int>,
    private val soundsHeard: suspend () -> Boolean,
    private val prepare: () -> Unit,
    private val play: (RunSound) -> Unit,
    private val stop: () -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    private val asked = MutableStateFlow(false)

    /** True from "I sent it" until the run it asked for is over, however it ended. */
    val running: StateFlow<Boolean> = asked.asStateFlow()

    /** Whether the Report screen is the screen on top. */
    private var onReport = false

    /** Whether this run's sounds are heard: null until the settings have been read. */
    private var heard: Boolean? = null

    init {
        viewModelScope.launch { sent.drop(1).collect { ask() } }
    }

    /** "I sent it" has recorded a report as sent. */
    private fun ask() {
        // Answered on a screen that has been left since: the moment has passed. And a run that
        // is playing is not started again under itself.
        if (!onReport || asked.value) return
        heard = null
        asked.value = true
        viewModelScope.launch {
            val now =
                try {
                    soundsHeard()
                } catch (_: IOException) {
                    // A settings file that cannot be read: the run plays without its sounds.
                    false
                }
            if (!asked.value) return@launch
            heard = now
            // Most of a second before the first is due: a sound made on its frame starts late.
            // TODO(debt): with the phone's animations off the run is over a moment later, and
            //  the two players made here are let go unused. Nothing is heard. Making them only
            //  once he is drawn needs one more word from the drawing. See docs/FINDINGS_LOG.md,
            //  2026-10-11.
            if (now) prepare()
        }
    }

    /** @param onReport whether the Report screen is the screen on top. */
    fun onScreens(onReport: Boolean) {
        this.onReport = onReport
        if (!onReport) end(whole = false)
    }

    /** He has got to the spot he runs on: the wheels whirr up. */
    fun onArrived() = sound(RunSound.WHIRR)

    /** His brake lights have come on: the squeal of the slide. */
    fun onBraked() = sound(RunSound.SQUEAL)

    /**
     * The run is over.
     *
     * @param whole whether it was played to its end. A run that was cut short takes its sound
     * with it; one that ended by itself lets the squeal's last moment ring out.
     */
    fun onDone(whole: Boolean) = end(whole)

    /** The run's pictures could not be read: nothing was drawn, and nothing is tried again. */
    fun onUnreadable(why: IOException) {
        viewModelScope.launch {
            val what =
                "Dually's mascot did not run across the Report screen: a picture of him could " +
                    "not be read"
            eventLog.add(clock(), EventCategory.ERROR, what, why.stackTraceToString())
        }
    }

    private fun sound(which: RunSound) {
        if (asked.value && heard == true) play(which)
    }

    private fun end(whole: Boolean) {
        if (!asked.value) return
        asked.value = false
        heard = null
        if (!whole) stop()
    }
}
