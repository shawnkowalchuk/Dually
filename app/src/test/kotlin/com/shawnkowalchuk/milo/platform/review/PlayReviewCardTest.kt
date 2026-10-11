package com.shawnkowalchuk.milo.platform.review

import com.google.android.play.core.review.model.ReviewErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the event log is told when Google Play gives no review flow. */
class PlayReviewCardTest {
    @Test
    fun `each of Google Play's errors is said in words`() {
        assertEquals(
            "the Play Store app was not found on this phone",
            refusalInWords(ReviewErrorCode.PLAY_STORE_NOT_FOUND),
        )
        assertEquals(
            "Google Play called the request invalid",
            refusalInWords(ReviewErrorCode.INVALID_REQUEST),
        )
        assertEquals(
            "Google Play reported an error of its own",
            refusalInWords(ReviewErrorCode.INTERNAL_ERROR),
        )
    }

    @Test
    fun `an error this build has no word for is named by its number`() {
        assertEquals("Google Play answered with error -7", refusalInWords(-7))
    }
}
