package com.shawnkowalchuk.milo.platform.review

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [ReviewCard] through Google Play's In-App Review library (ADR-009), the only code of MilO's
 * that touches it. The library talks to the Play Store app on the phone; MilO itself still
 * opens no connection and has no INTERNET permission.
 *
 * **Google's rules for the card, which this keeps to** (developer.android.com, "Google Play
 * In-App Reviews API"): the card is Google Play's own screen over MilO's, shown as it is;
 * nothing of MilO's is drawn on it or round it, and MilO never closes it. No question of
 * MilO's comes before it, and no button of MilO's promises it, because Google Play may show
 * nothing at all: it limits how often anyone sees the card.
 *
 * @param activity the screen the card is shown over. Held only while one request runs.
 */
class PlayReviewCard(private val manager: ReviewManager, private val activity: Activity) :
    ReviewCard {
    override suspend fun request(): ReviewFlow = try {
        val info = manager.requestReviewFlow().answer()
        // What the task of the launch reports is not listened to: it completes when Google
        // Play's screen has closed, and says neither whether the card was shown nor whether a
        // review was written.
        ReviewFlow.Given {
            val screenThere = !activity.isFinishing && !activity.isDestroyed
            if (screenThere) manager.launchReviewFlow(activity, info)
            screenThere
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: ReviewException) {
        ReviewFlow.NotGiven(refusalInWords(refused.errorCode))
    } catch (failure: Exception) {
        // Whatever else the library throws. Left alone, it would end the process, and the trip
        // service runs in it.
        val what = "the request failed (${failure.javaClass.name})"
        ReviewFlow.NotGiven(what, failure.stackTraceToString())
    }
}

/**
 * Waits for what one of the library's tasks comes to, without holding a thread: its result, or
 * its failure thrown here. The library's own Kotlin extensions have a function for this; they
 * are not in the build (ADR-009), and this is all of them that MilO would have used.
 */
private suspend fun <T> Task<T>.answer(): T = suspendCancellableCoroutine { waiting ->
    addOnCompleteListener { done ->
        val failure = done.exception
        when {
            failure != null -> waiting.resumeWithException(failure)
            done.isCanceled -> waiting.cancel()
            else -> waiting.resume(done.result)
        }
    }
}

/** Google Play's error for a request for the review flow, in words for the event log. */
internal fun refusalInWords(@ReviewErrorCode errorCode: Int): String = when (errorCode) {
    ReviewErrorCode.PLAY_STORE_NOT_FOUND -> "the Play Store app was not found on this phone"
    ReviewErrorCode.INVALID_REQUEST -> "Google Play called the request invalid"
    ReviewErrorCode.INTERNAL_ERROR -> "Google Play reported an error of its own"
    else -> "Google Play answered with error $errorCode"
}

/**
 * Makes the [ReviewCard] for whichever screen is in front, on one manager for the process.
 * Called once, by the `AppContainer`'s part for the review card.
 */
fun playReviewCards(context: Context): (Activity) -> ReviewCard {
    val manager by lazy { ReviewManagerFactory.create(context.applicationContext) }
    return { activity -> PlayReviewCard(manager, activity) }
}
