package com.shawnkowalchuk.milo.data.cloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a purchase and "Restore purchase" come to, with a stand-in for the server. */
class CloudUnlockTest {
    /** Answers by token; remembers what it was sent. */
    private class Server(private val answers: Map<String, CloudUnlockResult>) : CloudUnlockServer {
        val sent = mutableListOf<String>()

        override suspend fun unlock(token: String): CloudUnlockResult {
            sent += token
            return answers.getValue(token)
        }
    }

    @Test
    fun `a paid purchase goes to the server, and its answer is the result`() = runTest {
        val server = Server(mapOf("t1" to CloudUnlockResult.Unlocked))

        assertEquals(
            CloudUnlockResult.Unlocked,
            unlockAfter(PlayPurchase.Bought("t1"), { emptyList() }, server),
        )
        assertEquals(listOf("t1"), server.sent)
    }

    @Test
    fun `a purchase owned already is restored from what Google Play holds`() = runTest {
        val server = Server(mapOf("t1" to CloudUnlockResult.Unlocked))

        assertEquals(
            CloudUnlockResult.Unlocked,
            unlockAfter(PlayPurchase.AlreadyOwned, { listOf("t1") }, server),
        )
    }

    @Test
    fun `what Google Play's sheet said without a purchase reaches no server`() = runTest {
        val server = Server(emptyMap())
        val cases =
            mapOf(
                PlayPurchase.Pending to CloudUnlockResult.Pending,
                PlayPurchase.Cancelled to CloudUnlockResult.Cancelled,
                PlayPurchase.Unavailable to CloudUnlockResult.NotOffered,
                PlayPurchase.Failed("response 6") to
                    CloudUnlockResult.Failed("Google Play: response 6"),
            )
        for ((purchase, result) in cases) {
            assertEquals(result, unlockAfter(purchase, { emptyList() }, server))
        }
        assertEquals(emptyList<String>(), server.sent)
    }

    @Test
    fun `a restore stops at the first purchase that unlocks`() = runTest {
        val server =
            Server(
                mapOf(
                    "refunded" to CloudUnlockResult.Refunded,
                    "good" to CloudUnlockResult.Unlocked,
                    "never" to CloudUnlockResult.Offline,
                ),
            )

        assertEquals(
            CloudUnlockResult.Unlocked,
            restoreFrom(listOf("refunded", "good", "never"), server),
        )
        assertEquals(listOf("refunded", "good"), server.sent)
    }

    @Test
    fun `a restore that unlocks nothing says why with the first answer`() = runTest {
        val server =
            Server(mapOf("a" to CloudUnlockResult.OtherAccount, "b" to CloudUnlockResult.Offline))

        assertEquals(CloudUnlockResult.OtherAccount, restoreFrom(listOf("a", "b"), server))
        assertEquals(CloudUnlockResult.NothingToRestore, restoreFrom(emptyList(), server))
    }
}
