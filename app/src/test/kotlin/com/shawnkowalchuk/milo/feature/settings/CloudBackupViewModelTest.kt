package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
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
 * The Cloud backup tile: what it shows before and after a sign-in, each way a sign-in can end,
 * and a sign-out. Google's sheet is a stand-in that answers what the test says; the tile's
 * "activity" is [Unit].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupViewModelTest {
    private val log = FakeEventLogDao()
    private val account = CloudAccount(id = "uid-1", email = "driver@example.com")
    private var stored: CloudAccount? = null
    private var sheets = 0
    private var signOuts = 0

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

        tile.onSignIn(Unit)
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
    fun `a sign-in shows the account, and a second press while the sheet is open does nothing`() =
        runTest {
            val tile = viewModel()

            tile.onSignIn(Unit)
            runCurrent()
            assertTrue(tile.state.value.working)
            tile.onSignIn(Unit)
            runCurrent()
            answer.complete(CloudSignInResult.SignedIn(account))
            runCurrent()

            assertEquals(1, sheets)
            assertEquals(account, tile.state.value.account)
            assertFalse(tile.state.value.working)
            assertNull(tile.state.value.note)
        }

    @Test
    fun `each way a sign-in fails has its note, and closing the sheet has none`() = runTest {
        val tile = viewModel()
        val cases =
            listOf(
                CloudSignInResult.Cancelled to null,
                CloudSignInResult.NoGoogleAccount to CloudSignInNote.NO_GOOGLE_ACCOUNT,
                CloudSignInResult.Offline to CloudSignInNote.OFFLINE,
                CloudSignInResult.Failed("Firebase: refused") to CloudSignInNote.FAILED,
            )
        for ((result, note) in cases) {
            answer = CompletableDeferred(result)
            tile.onSignIn(Unit)
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

        tile.onSignIn(Unit)
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
}
