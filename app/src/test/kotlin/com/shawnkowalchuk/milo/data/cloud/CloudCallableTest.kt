package com.shawnkowalchuk.milo.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/** The call to the purchase check, by Firebase's callable protocol, and how its answers read. */
class CloudCallableTest {
    @Test
    fun `the request is the token under data, as a callable function takes it`() {
        assertEquals("{\"data\":{\"purchaseToken\":\"abc\"}}", unlockRequestBody("abc"))
    }

    @Test
    fun `a success unlocks`() {
        assertEquals(
            CloudUnlockResult.Unlocked,
            unlockAnswer(200, "{\"result\":{\"unlocked\":true}}"),
        )
    }

    @Test
    fun `the server's reasons are read from the error's message`() {
        fun refusal(status: String, message: String) =
            "{\"error\":{\"status\":\"$status\",\"message\":\"$message\"}}"

        assertEquals(
            CloudUnlockResult.OtherAccount,
            unlockAnswer(403, refusal("PERMISSION_DENIED", "other-account")),
        )
        assertEquals(
            CloudUnlockResult.Pending,
            unlockAnswer(400, refusal("FAILED_PRECONDITION", "pending")),
        )
        assertEquals(
            CloudUnlockResult.Refunded,
            unlockAnswer(400, refusal("FAILED_PRECONDITION", "refunded")),
        )
        assertEquals(
            CloudUnlockResult.Offline,
            unlockAnswer(503, refusal("UNAVAILABLE", "play-unreachable")),
        )
        assertEquals(
            CloudUnlockResult.Failed("Purchase check: FAILED_PRECONDITION, not-purchased"),
            unlockAnswer(400, refusal("FAILED_PRECONDITION", "not-purchased")),
        )
    }

    @Test
    fun `an answer outside the protocol is offline for a server error, else a failure`() {
        assertEquals(CloudUnlockResult.Offline, unlockAnswer(502, "<html>Bad gateway</html>"))
        assertEquals(CloudUnlockResult.Failed("Purchase check: HTTP 404"), unlockAnswer(404, ""))
    }
}
