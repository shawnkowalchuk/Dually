package com.shawnkowalchuk.milo.app

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.core.review.mayBeDriving
import com.shawnkowalchuk.milo.feature.review.ReviewDue
import com.shawnkowalchuk.milo.feature.review.ReviewViewModel
import com.shawnkowalchuk.milo.platform.review.ReviewCard
import com.shawnkowalchuk.milo.platform.review.ReviewPrompts
import com.shawnkowalchuk.milo.platform.review.playReviewCards
import com.shawnkowalchuk.milo.platform.system.installedFromGooglePlay
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * The long-lived objects of Google Play's review card (2026-10-11, ADR-009): MilO asks Google
 * Play to show it at three moments, and Google Play decides whether it appears.
 *
 * Like [PartyObjects] and the others, it is a part of the [AppContainer], which creates it once
 * and through which everything reaches it (`container.review`); it is a class of its own only
 * because the container is at its size limit, and it follows the container's rules.
 *
 * @param container where the storage these objects read is made.
 */
class ReviewObjects(private val appContext: Context, private val container: AppContainer) {
    /**
     * Decides when Google Play is asked, and remembers that it was. It is handed a number of
     * trips, whether a drive may be under way, the settings and the event log, and neither the
     * trip controller nor a way to write a trip or a report: it can change none.
     */
    val prompts: ReviewPrompts by lazy {
        ReviewPrompts(
            settings = container.settingsStore,
            recordedTrips = container.tripRepository.observeRecordedTripCount(),
            mayBeDriving =
                container.tripController.activity.map { now ->
                    mayBeDriving(now.trip != null, now.truckConnected, now.parked != null)
                },
            fromGooglePlay = { installedFromGooglePlay(appContext) },
            eventLog = container.eventLogRepository,
            crashFileStore = container.crashFileStore,
            clock = container.clock,
            zone = ZoneId::systemDefault,
        )
    }

    /** Google Play's card for the screen that is in front. */
    val cardOn: (Activity) -> ReviewCard by lazy { playReviewCards(appContext) }
}

/** How [ReviewWatch] gets the review card's ViewModel. */
private fun reviewViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { ReviewViewModel(container.review.prompts) }
    }

/**
 * Tells the review card's ViewModel what the screens are doing, and carries out what it finds
 * due: the one place of the phone UI where Google Play is asked for its card.
 *
 * - **MilO on the screen** is said by the activity's own life, as it happens: MilO is paused
 *   the moment something else takes the screen, Google Play's card included.
 * - **Nothing is drawn here, and nothing can be:** the card is Google Play's own screen, shown
 *   over MilO's as it is. MilO puts no question before it and no button that promises it.
 *
 * @param homeFree whether Home is on top with nothing playing over it (`homeIsFree`).
 * @param onReport whether the Report screen is the screen on top.
 */
@Composable
internal fun ReviewWatch(container: AppContainer, homeFree: Boolean, onReport: Boolean) {
    val review: ReviewViewModel = viewModel(factory = reviewViewModelFactory(container))
    LifecycleResumeEffect(review) {
        review.onInFront(true)
        onPauseOrDispose { review.onInFront(false) }
    }
    LaunchedEffect(review, homeFree, onReport) { review.onScreens(homeFree, onReport) }

    val due by review.due.collectAsState()
    val activity = LocalActivity.current
    val now = due
    if (now != null && activity != null) {
        CelebrationThenCard(now, sentRunViewModel(container).running, onReport) {
            review.onCelebrationOver(now, container.review.cardOn(activity))
        }
    }
}

/**
 * **Where what MilO plays for a moment belongs: before Google Play's card, never with it.**
 *
 * A moment is due, and nothing has been asked of Google Play yet. [onOver] is called when
 * whatever is played for it has ended, however it ended ([afterCelebration]). Until then the
 * card is not asked for; if the screen is left meanwhile, this leaves the composition and
 * [onOver] is not called at all. What is played must ask nothing and promise nothing about a
 * review: Google's rules for the card allow no question and no call to action before it
 * (ADR-009).
 *
 * **For a report that was sent, the mascot's run across the Report screen is played**
 * (2026-10-11; `SentRunObjects.kt`). It is drawn by [SentRunOverReport], not from here, because
 * it plays after every "I sent it", and a moment is due for one of them at most. For the two
 * numbers of trips a short animation of the mascot is planned; nothing is played for them yet,
 * so that is over at once.
 *
 * @param runPlaying whether the mascot's run is asked for or playing.
 * @param onReport whether the Report screen is the screen on top.
 */
@Composable
private fun CelebrationThenCard(
    due: ReviewDue,
    runPlaying: StateFlow<Boolean>,
    onReport: Boolean,
    onOver: () -> Unit,
) {
    // Written down as this is composed, which is before a run that ends with the screen can
    // say that it has ended.
    val stillOnReport by rememberUpdatedState(onReport)
    LaunchedEffect(due) { afterCelebration(due, runPlaying, { stillOnReport }, onOver) }
}
