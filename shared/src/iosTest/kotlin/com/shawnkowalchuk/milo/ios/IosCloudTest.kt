package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.testZone
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.cloud.CLOUD_OWNER
import com.shawnkowalchuk.milo.data.cloud.CloudDocument
import com.shawnkowalchuk.milo.data.cloud.cloudOwnerOf
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The online backup on the iPhone (phase 4h), against a stand-in for Firebase: the tile, the
 * sign-in that finds an unlock bought elsewhere, the upload, one phone per account, the restore
 * on a second iPhone, and the purchase check's answers.
 */
class IosCloudTest {
    private val firstPhone = RecorderWorld()
    private val secondPhone = RecorderWorld()
    private val cloud = FakeFirebase()

    @AfterTest
    fun cleanUp() {
        firstPhone.close()
        secondPhone.close()
    }

    @Test
    fun `before anyone signs in the tile offers the purchase at the App Store's price`() = runTest {
        val tile = backupOf(firstPhone).tile(available = true, price = "$19.99")
        assertEquals("Purchase online backup · $19.99", tile.purchase)
        assertEquals("Restore purchase", tile.restorePurchase)
        assertFalse(tile.signedIn)
        assertNotNull(tile.signIn)
        assertEquals(0, cloud.calls, "no Firebase before a sign-in")
    }

    @Test
    fun `a build without Firebase's settings offers nothing`() = runTest {
        val tile = backupOf(firstPhone).tile(available = false, price = null)
        assertNull(tile.purchase)
        assertNull(tile.restorePurchase)
    }

    @Test
    fun `an account unlocked on Android is not asked to pay and its trips upload`() = runTest {
        driveOneTrip(firstPhone)
        cloud.unlocked += ACCOUNT
        val backup = backupOf(firstPhone)

        val note = backup.signedIn(ACCOUNT, "shawn@example.com")

        assertEquals("Backup unlocked. Your trips start uploading now.", note?.text)
        assertTrue(backup.upload("the test"))
        val held = cloud.documents.getValue(ACCOUNT)
        assertEquals(1, held.keys.count { it.startsWith("trips/") })
        assertTrue(held.containsKey("backup/settings"))
        assertNotNull(held[CLOUD_OWNER]?.let(::cloudOwnerOf))
        val tile = backup.tile(available = true, price = null)
        assertEquals("On", tile.on)
        assertNull(tile.purchase)

        // Nothing changed since: the next upload sends nothing.
        val before = cloud.writes
        assertTrue(backup.upload("again"))
        assertEquals(before, cloud.writes)
    }

    @Test
    fun `a second iPhone is asked and restores the first one's trips`() = runTest {
        driveOneTrip(firstPhone)
        cloud.unlocked += ACCOUNT
        val first = backupOf(firstPhone)
        first.signedIn(ACCOUNT, "shawn@example.com")
        assertTrue(first.upload("the test"))

        val second = backupOf(secondPhone)
        second.signedIn(ACCOUNT, "shawn@example.com")
        second.upload("the test")
        val tile = second.tile(available = true, price = null)
        assertNotNull(tile.restoreHere)
        assertNotNull(tile.backUpHere)
        assertTrue(tile.text.contains("1 trip from another phone"), tile.text)
        assertEquals(
            0,
            secondPhone.trips.observeFinishedTrips().first().size,
            "nothing replaced before the yes",
        )

        val note = second.restoreHere()
        assertTrue(note.good, note.text)
        assertEquals(1, secondPhone.trips.observeFinishedTrips().first().size)
        assertEquals("On", second.tile(available = true, price = null).on)
    }

    @Test
    fun `a purchase goes to the check with Apple's signed transaction`() = runTest {
        val backup = backupOf(firstPhone)
        backup.signedIn(ACCOUNT, "")
        cloud.answer = 200 to """{"result":{"unlocked":true}}"""

        val answer = backup.purchased("eyJ.signed.transaction")

        assertEquals("""{"data":{"signedTransaction":"eyJ.signed.transaction"}}""", cloud.called)
        assertTrue(answer.finish)
        assertEquals(true, backup.account()?.unlocked)
    }

    @Test
    fun `the purchase carries the account's Apple token`() = runTest {
        val backup = backupOf(firstPhone)
        assertNull(backup.appleAccountToken(), "nobody signed in, nothing to buy for")
        backup.signedIn("owner-uid", "")
        assertEquals("27dfbde6-ec9d-28c3-6258-ee6edad10e40", backup.appleAccountToken())
    }

    @Test
    fun `another account's purchase is said so and the check unreachable keeps it for later`() =
        runTest {
            val backup = backupOf(firstPhone)
            backup.signedIn(ACCOUNT, "")

            cloud.answer =
                403 to """{"error":{"status":"PERMISSION_DENIED","message":"other-account"}}"""
            val other = backup.purchased("eyJ.signed.transaction")
            assertTrue(other.finish)
            assertTrue(other.note!!.text.startsWith("That purchase belongs to another"))

            cloud.answer = null
            val offline = backup.purchased("eyJ.signed.transaction")
            assertFalse(offline.finish, "StoreKit offers it again at the next start")
            assertEquals(false, backup.account()?.unlocked)
        }

    private fun backupOf(phone: RecorderWorld): IosCloud = IosCloud(
        apple = cloud,
        files = IosCloudFiles(phone.folderPath + "/cloud"),
        mainRows = phone.mainTransferDao(),
        settings = phone.settings,
        transfer =
            IosTransfer(
                export =
                    DataExport(
                        phone.mainTransferDao(),
                        phone.pointsTransferDao(),
                        phone.settings,
                        "0.1.0",
                        { phone.nowMs },
                        { testZone("America/Edmonton") },
                    ),
                import = DataImport(phone.mainTransferDao(), phone.pointsTransferDao()),
                settings = phone.settings,
                eventLog = phone.log,
                zone = { testZone("America/Edmonton") },
                clock = { phone.nowMs },
            ),
        eventLog = phone.log,
        changes = emptyFlow(),
        // Uploads the code starts by itself are left out: each test runs the one it means.
        scope = CoroutineScope(Job()).also { it.cancel() },
        appVersion = "0.1.0",
        clock = { phone.nowMs },
        zone = { testZone("America/Edmonton") },
        locale = { Locale("en_CA") },
    )

    private suspend fun driveOneTrip(phone: RecorderWorld) {
        val recorder = phone.recorder()
        recorder.start()
        phone.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
        recorder.end()
    }

    private companion object {
        const val ACCOUNT = "account-1"
    }
}

/** Firebase as the test says it is: one shared backup, answered at once. */
private class FakeFirebase : AppleCloud {
    val documents = mutableMapOf<String, MutableMap<String, String>>()
    val unlocked = mutableSetOf<String>()
    var signedIn: String? = null
    var calls = 0
    var writes = 0

    /** What the purchase check answers: its code and body, or null for no answer. */
    var answer: Pair<Int, String>? = 200 to "{}"
    var called: String? = null

    override fun signedInAccount(): String? {
        calls++
        return signedIn
    }

    override fun apply(
        account: String,
        documents: List<CloudDocument>,
        removals: List<String>,
        reply: CloudReply,
    ) {
        calls++
        if (account !in unlocked) {
            reply.refused("Firestore 7")
            return
        }
        writes++
        val held = this.documents.getOrPut(account) { mutableMapOf() }
        removals.forEach(held::remove)
        documents.forEach { held[it.path] = it.json }
        reply.done()
    }

    override fun read(account: String, path: String?, reply: CloudReadReply) {
        calls++
        val held = documents[account].orEmpty()
        reply.documents(if (path == null) held else held.filterKeys { it == path })
    }

    override fun holdsUnlock(account: String, reply: CloudHoldsReply) {
        calls++
        signedIn = account
        reply.holds(account in unlocked)
    }

    override fun call(name: String, body: String, reply: CloudCallReply) {
        calls++
        called = body
        val (code, text) = answer ?: return reply.offline()
        if (code == 200) signedIn?.let { unlocked += it }
        reply.answered(code, text)
    }
}
