package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudRestoreResult
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import com.shawnkowalchuk.milo.data.cloud.CloudStartResult
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockResult
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Online backup tile (the Cloud backup tile until 2026-11-12): what it shows before and
 * after a sign-in, each way a sign-in can end, a purchase and a restore that sign in first, and a
 * sign-out. Google's sheet is a stand-in that answers what the test says; the tile's
 * "activity" is [Unit].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupViewModelTest {
    private val log = FakeEventLogDao()
    private val account = CloudAccount(id = "uid-1", email = "driver@example.com")
    private var stored: CloudAccount? = null
    private var sheets = 0
    private var signOuts = 0
    private var priceAsked = 0

    /** What Google Play's purchase sheet, then the server, answer next. */
    private var unlockAnswer: CloudUnlockResult = CloudUnlockResult.Unlocked
    private var restoreAnswer: CloudUnlockResult = CloudUnlockResult.Unlocked

    /** How the backup stands, and what a restore, a take-over and a deletion come to. */
    private var standing: CloudStartResult = CloudStartResult.BackingUp
    private var restoreTripsAnswer: CloudRestoreResult = CloudRestoreResult.Restored(12)
    private var backUpHereAnswer = true
    private var deleteAnswer = true
    private var deletions = 0

    /** What Google's sheet answers next. Completed by the test, so it can look while it is open. */
    private var answer = CompletableDeferred<CloudSignInResult>()

    private fun TestScope.viewModel(available: Boolean = true): CloudBackupViewModel<Unit> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return CloudBackupViewModel<Unit>(
            available = available,
            readAccount = { stored },
            signIn = {
                sheets++
                answer.await()
            },
            signOut = {
                signOuts++
                stored = null
            },
            price = {
                priceAsked++
                "US$19.99"
            },
            unlock = { unlockAnswer },
            restore = { restoreAnswer },
            startBackup = { standing },
            restoreTrips = { restoreTripsAnswer },
            backUpHere = { backUpHereAnswer },
            deleteAccount = {
                deletions++
                deleteAnswer
            },
            eventLog = EventLogRepository(log),
            clock = { 1_000L },
        ).also { runCurrent() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a build without the Firebase settings offers nothing and reads nothing`() = runTest {
        stored = account
        val tile = viewModel(available = false)

        tile.onPurchase(Unit)
        runCurrent()

        assertEquals(CloudBackupCardState(available = false), tile.state.value)
        assertEquals(0, sheets)
    }

    @Test
    fun `an account signed in before is shown as soon as its file is read`() = runTest {
        stored = account

        val state = viewModel().state.value

        assertEquals(account, state.account)
        assertTrue(state.read)
    }

    @Test
    fun `Purchase signs in first, then buys, and a second press meanwhile does nothing`() =
        runTest {
            val tile = viewModel()

            tile.onPurchase(Unit)
            runCurrent()
            assertTrue(tile.state.value.working)
            tile.onPurchase(Unit)
            runCurrent()
            answer.complete(CloudSignInResult.SignedIn(account))
            runCurrent()

            assertEquals(1, sheets)
            assertEquals(account.copy(unlocked = true), tile.state.value.account)
            assertEquals(CloudNote.UNLOCKED, tile.state.value.note)
            assertFalse(tile.state.value.working)
        }

    @Test
    fun `an account that turns out to hold the unlock is not asked to pay again`() = runTest {
        val tile = viewModel()
        unlockAnswer = CloudUnlockResult.Failed("should not be asked")
        answer = CompletableDeferred(CloudSignInResult.SignedIn(account.copy(unlocked = true)))

        tile.onPurchase(Unit)
        runCurrent()

        assertEquals(true, tile.state.value.account?.unlocked)
        assertEquals(CloudNote.UNLOCKED, tile.state.value.note)
    }

    @Test
    fun `Restore with nobody signed in signs in, then restores`() = runTest {
        val tile = viewModel()
        answer = CompletableDeferred(CloudSignInResult.SignedIn(account))

        tile.onRestore(Unit)
        runCurrent()

        assertEquals(1, sheets)
        assertEquals(true, tile.state.value.account?.unlocked)
    }

    @Test
    fun `each way a sign-in fails has its note, and closing the sheet has none`() = runTest {
        val tile = viewModel()
        val cases =
            listOf(
                CloudSignInResult.Cancelled to null,
                CloudSignInResult.NoGoogleAccount to CloudNote.NO_GOOGLE_ACCOUNT,
                CloudSignInResult.Offline to CloudNote.OFFLINE,
                CloudSignInResult.Failed("Firebase: refused") to CloudNote.SIGN_IN_FAILED,
            )
        for ((result, note) in cases) {
            answer = CompletableDeferred(result)
            tile.onPurchase(Unit)
            runCurrent()

            assertEquals(note, tile.state.value.note)
            assertNull(tile.state.value.account)
            assertFalse(tile.state.value.working)
        }
    }

    @Test
    fun `a failure is written to the log with its kind and without the account`() = runTest {
        val tile = viewModel()
        answer = CompletableDeferred(CloudSignInResult.Failed("Firebase: refused"))

        tile.onPurchase(Unit)
        runCurrent()

        val line = log.entries.single()
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals("Cloud backup sign-in failed. Firebase: refused", line.message)
    }

    @Test
    fun `a sign-out forgets the account`() = runTest {
        stored = account
        val tile = viewModel()

        tile.onSignOut(Unit)
        runCurrent()

        assertEquals(1, signOuts)
        assertNull(tile.state.value.account)
        assertFalse(tile.state.value.working)
    }

    @Test
    fun `the price is shown before a sign-in and when locked, never to one that has it`() =
        runTest {
            assertEquals("US$19.99", viewModel().state.value.price)
            stored = account
            assertEquals("US$19.99", viewModel().state.value.price)

            stored = account.copy(unlocked = true)
            priceAsked = 0
            assertNull(viewModel().state.value.price)
            assertEquals(0, priceAsked)
        }

    @Test
    fun `an unlock unlocks the account shown and says so`() = runTest {
        stored = account
        val tile = viewModel()

        tile.onPurchase(Unit)
        runCurrent()

        assertEquals(true, tile.state.value.account?.unlocked)
        assertEquals(CloudNote.UNLOCKED, tile.state.value.note)
        assertFalse(tile.state.value.working)
    }

    @Test
    fun `each way an unlock or a restore ends has its note, and the account stays locked`() =
        runTest {
            stored = account
            val tile = viewModel()
            val cases =
                listOf(
                    CloudUnlockResult.Cancelled to null,
                    CloudUnlockResult.Pending to CloudNote.PENDING,
                    CloudUnlockResult.NotOffered to CloudNote.NOT_OFFERED,
                    CloudUnlockResult.OtherAccount to CloudNote.OTHER_ACCOUNT,
                    CloudUnlockResult.Refunded to CloudNote.REFUNDED,
                    CloudUnlockResult.NothingToRestore to CloudNote.NOTHING_TO_RESTORE,
                    CloudUnlockResult.Offline to CloudNote.OFFLINE,
                    CloudUnlockResult.Failed("Purchase check: INTERNAL") to CloudNote.UNLOCK_FAILED,
                )
            for ((result, note) in cases) {
                unlockAnswer = result
                tile.onPurchase(Unit)
                runCurrent()
                assertEquals(note, tile.state.value.note)

                restoreAnswer = result
                tile.onRestore(Unit)
                runCurrent()
                assertEquals(note, tile.state.value.note)
                assertEquals(false, tile.state.value.account?.unlocked)
            }
        }

    @Test
    fun `an account that holds the unlock is offered no unlock`() = runTest {
        stored = account.copy(unlocked = true)
        val tile = viewModel()
        unlockAnswer = CloudUnlockResult.Failed("should not be asked")

        tile.onPurchase(Unit)
        tile.onRestore(Unit)
        runCurrent()

        assertNull(tile.state.value.note)
    }

    @Test
    fun `an account that holds the unlock is told how the backup stands when the tile opens`() =
        runTest {
            stored = account.copy(unlocked = true)
            standing = CloudStartResult.Choose(trips = 12)

            assertEquals(CloudStartResult.Choose(12), viewModel().state.value.standing)
        }

    @Test
    fun `Restore my trips here brings them back, says how many, and backs up from here`() =
        runTest {
            stored = account.copy(unlocked = true)
            standing = CloudStartResult.Choose(trips = 12)
            val tile = viewModel()

            tile.onRestoreTrips()
            runCurrent()

            assertEquals(CloudNote.RESTORED, tile.state.value.note)
            assertEquals(12, tile.state.value.restoredTrips)
            assertEquals(CloudStartResult.BackingUp, tile.state.value.standing)
        }

    @Test
    fun `each way a restore does not happen has its note, and nothing else changes`() = runTest {
        stored = account.copy(unlocked = true)
        standing = CloudStartResult.Choose(trips = 12)
        val tile = viewModel()
        val cases =
            listOf(
                CloudRestoreResult.TripInProgress to CloudNote.TRIP_IN_PROGRESS,
                CloudRestoreResult.Offline to CloudNote.BACKUP_OFFLINE,
                CloudRestoreResult.Failed("Import refused") to CloudNote.RESTORE_FAILED,
            )
        for ((result, note) in cases) {
            restoreTripsAnswer = result
            tile.onRestoreTrips()
            runCurrent()

            assertEquals(note, tile.state.value.note)
            assertEquals(CloudStartResult.Choose(12), tile.state.value.standing)
        }
    }

    @Test
    fun `Back up this phone instead takes the backup over, or says it could not`() = runTest {
        stored = account.copy(unlocked = true)
        standing = CloudStartResult.Moved(trips = 3)
        val tile = viewModel()

        backUpHereAnswer = false
        tile.onBackUpHere()
        runCurrent()
        assertEquals(CloudNote.BACKUP_OFFLINE, tile.state.value.note)
        assertEquals(CloudStartResult.Moved(3), tile.state.value.standing)

        backUpHereAnswer = true
        tile.onBackUpHere()
        runCurrent()
        assertEquals(CloudNote.BACKING_UP_HERE, tile.state.value.note)
        assertEquals(CloudStartResult.BackingUp, tile.state.value.standing)
    }

    @Test
    fun `deleting the account forgets it here, and a failed one changes nothing`() = runTest {
        stored = account.copy(unlocked = true)
        val tile = viewModel()

        deleteAnswer = false
        tile.onDeleteAccount(Unit)
        runCurrent()
        assertEquals(CloudNote.DELETE_FAILED, tile.state.value.note)
        assertEquals(true, tile.state.value.account?.unlocked)

        deleteAnswer = true
        tile.onDeleteAccount(Unit)
        runCurrent()
        assertEquals(CloudNote.DELETED, tile.state.value.note)
        assertNull(tile.state.value.account)
        assertEquals(2, deletions)
    }

    @Test
    fun `a locked account is offered no restore and no take-over`() = runTest {
        stored = account
        val tile = viewModel()

        tile.onRestoreTrips()
        tile.onBackUpHere()
        runCurrent()

        assertNull(tile.state.value.note)
        assertNull(tile.state.value.standing)
    }
}
