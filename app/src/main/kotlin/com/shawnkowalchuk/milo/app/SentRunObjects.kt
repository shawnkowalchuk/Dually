package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.core.designsystem.component.MascotRun
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.partyPopHeard
import com.shawnkowalchuk.milo.feature.review.ReviewDue
import com.shawnkowalchuk.milo.feature.sentrun.SentRunViewModel
import com.shawnkowalchuk.milo.platform.sentrun.SentRunSounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The long-lived objects of the mascot's run across the Report screen (2026-10-11): he runs in,
 * runs on the spot with his shoes smoking, brakes and slides out, when a report has been
 * recorded as sent with "I sent it".
 *
 * Like [PartyObjects] and the others, it is a part of the [AppContainer], which creates it once
 * and through which everything reaches it (`container.sentRun`); it is a class of its own only
 * because the container is at its size limit, and it follows the container's rules.
 *
 * @param container where the event log these objects write to is made.
 */
class SentRunObjects(private val appContext: Context, private val container: AppContainer) {
    private val sentByAnswer = MutableStateFlow(0)

    /**
     * How many reports "I sent it" has recorded as sent in this run of MilO's process. In
     * memory only: a MilO that is ended in the moment after the answer plays no run for it.
     */
    val sent: StateFlow<Int> = sentByAnswer.asStateFlow()

    /** "I sent it" has recorded a report as sent, after the email app. Not "Mark as sent". */
    fun onReportSentByAnswer() = sentByAnswer.update { it + 1 }

    /** Plays the run's two sounds, as sounds of the screen. A failure goes to the Log. */
    val sounds: SentRunSounds by lazy {
        SentRunSounds(appContext) { failure ->
            container.applicationScope.launch {
                container.eventLogRepository.add(container.clock(), EventCategory.ERROR, failure)
            }
        }
    }
}

/**
 * The run's ViewModel, one for the activity: the same one for whoever asks from [MiloApp]'s
 * own level, which is where both [SentRunOverReport] and [ReviewWatch] ask.
 */
@Composable
internal fun sentRunViewModel(container: AppContainer): SentRunViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            SentRunViewModel(
                sent = container.sentRun.sent,
                // The party's rule for its pop: heard when either of MilO's sounds is on.
                soundsHeard = { container.settingsStore.current().partyPopHeard },
                prepare = container.sentRun.sounds::prepare,
                play = container.sentRun.sounds::play,
                stop = container.sentRun.sounds::stop,
                eventLog = container.eventLogRepository,
                clock = container.clock,
            )
        }
    },
)

/**
 * The run over the Report screen, drawn after it, while one plays. It takes no press from what
 * is under it. Leaving the Report screen ends it, and so does putting MilO away.
 *
 * @param onReport whether the Report screen is the screen on top.
 * @param barHeight the room the bottom bar takes: he runs along the bar's top edge, on the
 * line the greetings walk.
 */
@Composable
internal fun SentRunOverReport(container: AppContainer, onReport: Boolean, barHeight: Dp) {
    val run = sentRunViewModel(container)
    LaunchedEffect(run, onReport) { run.onScreens(onReport) }
    val running by run.running.collectAsState()
    if (running && onReport) {
        MascotRun(
            onDone = run::onDone,
            barHeight = barHeight,
            onArrived = run::onArrived,
            onBraked = run::onBraked,
            onUnreadable = run::onUnreadable,
        )
    }
}

/**
 * Waits until what MilO plays for a moment is over, and then says so, once: Google Play's card
 * comes after it, never with it (ADR-009).
 *
 * - **A report recorded as sent:** the mascot's run across the Report screen. It is asked for
 *   in the moment the review card's moment begins, a second before that moment can be due, so
 *   [runPlaying] is true by then if a run plays at all; with the phone's animations off, or
 *   pictures that cannot be read, it is over at once.
 * - **The two numbers of trips,** over Home: nothing is played for them yet.
 *
 * **Leaving the Report screen is one of the ways a run ends, and then the moment has passed
 * with it:** [onOver] is not called. Whoever waits here is cancelled a moment later, when the
 * moment is due no longer; [stillOnReport] closes the gap between the two, so that Google Play
 * is not asked for a card for a screen that has just been left.
 *
 * @param stillOnReport whether the Report screen is still the screen on top, asked when the
 * run is over.
 */
internal suspend fun afterCelebration(
    due: ReviewDue,
    runPlaying: StateFlow<Boolean>,
    stillOnReport: () -> Boolean,
    onOver: () -> Unit,
) {
    if (due.occasion == ReviewOccasion.REPORT_SENT) {
        runPlaying.first { !it }
        if (!stillOnReport()) return
    }
    onOver()
}
