package com.shawnkowalchuk.milo.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.platform.review.ReviewCard
import com.shawnkowalchuk.milo.platform.review.ReviewPrompts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long a screen has to have been in front, with nothing else wanting the moment, before
 * MilO acts on it. Long enough for what opens by itself a moment after MilO does (What's new,
 * the mascot's party at a trip's start) and for the question "Did you send it?" to have closed.
 */
internal const val SETTLE_MS = 1_000L

/**
 * A moment that is to be acted on now.
 *
 * @property occasion where: over Home, or on the Report screen after "I sent it".
 * @property moments what Google Play is asked for, once for all of them.
 */
data class ReviewDue(val occasion: ReviewOccasion, val moments: Set<ReviewMoment>)

/**
 * When the screens let MilO ask Google Play for its review card (2026-10-11, ADR-009). Which
 * moments are reached and what counts as asked is `platform/review`'s business; this says when
 * a screen is fit for it:
 *
 * - **The two numbers of trips,** the next time Home is in front with nothing playing over it
 *   ([homeIsFree]), and again when the number of trips or the truck's state changes under it.
 * - **A report recorded as sent,** on the Report screen, once "I sent it" has been stored.
 *
 * Each has to have held for a moment first ([SETTLE_MS]). **A moment that is due is not acted
 * on here:** [due] says so, the app plays whatever belongs to the moment first, and calls
 * [onCelebrationOver] when that is over. Only then is Google Play asked.
 */
class ReviewViewModel(private val prompts: ReviewPrompts) : ViewModel() {
    /** What the app says of the screens. */
    private data class Screens(
        val inFront: Boolean = false,
        val homeFree: Boolean = false,
        val onReport: Boolean = false,
    )

    private val screens = MutableStateFlow(Screens())
    private val dueNow = MutableStateFlow<ReviewDue?>(null)

    /** The moment the app is to act on now, or null. */
    val due: StateFlow<ReviewDue?> = dueNow.asStateFlow()

    init {
        viewModelScope.launch {
            combine(screens, prompts.reportJustSent, ::occasionOf)
                .distinctUntilChanged()
                // collectLatest, twice: a look that is waiting or under way is dropped the
                // moment anything it went by changes, and what was due is due no longer.
                .collectLatest { occasion ->
                    dueNow.value = null
                    // No screen is fit for the card: nothing is watched and nothing is read.
                    if (occasion == null) return@collectLatest
                    // One look now, and another whenever the number of trips or the truck's
                    // state changes under the screen.
                    prompts.changes.collectLatest { lookAt(occasion) }
                }
        }
    }

    private suspend fun lookAt(occasion: ReviewOccasion) {
        dueNow.value = null
        delay(SETTLE_MS)
        val moments = prompts.look(occasion)
        when {
            moments.isNotEmpty() -> dueNow.value = ReviewDue(occasion, moments)
            occasion == ReviewOccasion.REPORT_SENT -> prompts.reportDealtWith()
        }
    }

    /** MilO came to the front, or left it. */
    fun onInFront(inFront: Boolean) = screens.update { it.copy(inFront = inFront) }

    /**
     * @param homeFree whether Home is the screen on top with nothing playing over it.
     * @param onReport whether the Report screen is the screen on top.
     */
    fun onScreens(homeFree: Boolean, onReport: Boolean) {
        // A report's moment belongs to the Report screen it was answered on: left, it has passed.
        if (!onReport) prompts.reportDealtWith()
        screens.update { it.copy(homeFree = homeFree, onReport = onReport) }
    }

    /**
     * Whatever the app plays for [due] is over, or there was nothing to play: Google Play is
     * asked now, if the screen is still the one the moment was due on.
     *
     * @param card Google Play's card for the screen in front.
     */
    fun onCelebrationOver(due: ReviewDue, card: ReviewCard) {
        if (dueNow.value != due) return
        dueNow.value = null
        viewModelScope.launch {
            prompts.ask(due.occasion, card) {
                occasionOf(screens.value, prompts.reportJustSent.value) == due.occasion
            }
            if (due.occasion == ReviewOccasion.REPORT_SENT) prompts.reportDealtWith()
        }
    }

    private fun occasionOf(screens: Screens, reportSent: Boolean): ReviewOccasion? = when {
        !screens.inFront -> null
        screens.onReport && reportSent -> ReviewOccasion.REPORT_SENT
        screens.homeFree -> ReviewOccasion.HOME
        else -> null
    }
}

/**
 * Whether Home is free for the review card: it is drawn and on top, and nothing else has the
 * moment or is about to. The card comes after all of these, never over or before them:
 *
 * - **the first start's page** (Home is not drawn under it), and Setup or any other screen on
 *   top of Home;
 * - **the opening greeting,** from the fresh start until the mascot has gone or was dropped;
 * - **the one-year party,** while it plays or is owed, and while that is not known yet;
 * - **something that is about to open by itself:** What's new after an update, or the screen a
 *   tapped notification asked for.
 *
 * @param homeDrawn Home is the screen on top and the first start's page has been left.
 * @param greetingOver no greeting is asked for any more.
 * @param partyHasTheScreen whether the party has the screen or is about to, or null while that
 * is being read.
 * @param aboutToOpen something is waiting to open over Home.
 */
fun homeIsFree(
    homeDrawn: Boolean,
    greetingOver: Boolean,
    partyHasTheScreen: Boolean?,
    aboutToOpen: Boolean,
): Boolean = homeDrawn && greetingOver && partyHasTheScreen == false && !aboutToOpen
