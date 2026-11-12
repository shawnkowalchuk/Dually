package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockResult
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the last press of a button came to, when it is worth saying. */
enum class CloudNote {
    NO_GOOGLE_ACCOUNT,
    OFFLINE,
    SIGN_IN_FAILED,
    UNLOCKED,
    PENDING,
    NOT_OFFERED,
    OTHER_ACCOUNT,
    REFUNDED,
    NOTHING_TO_RESTORE,
    UNLOCK_FAILED,
}

/**
 * The Cloud backup tile.
 *
 * @param available false in a build without the Firebase settings: then the tile says so and
 * offers nothing.
 * @param account the account signed in, or null. Unknown until the file was read: [read].
 * @param read whether the account's file has been read yet. No button is offered before.
 * @param price the unlock's price as Google Play shows it, once asked, or null.
 */
data class CloudBackupCardState(
    val available: Boolean,
    val account: CloudAccount? = null,
    val read: Boolean = false,
    val working: Boolean = false,
    val note: CloudNote? = null,
    val price: String? = null,
)

/**
 * The Settings tile of the optional cloud backup (ADR-012, ADR-013): signs in to a Google
 * account and out again, unlocks the backup with the one-time purchase or restores it, and says
 * which account it is. Restore of the trips, account deletion and the finished tile come in
 * slice 1e (FINDINGS_LOG, 2026-11-11). Phone only: Android Auto has no Settings, and an account
 * is never chosen while driving.
 *
 * @param H what Google's sheets are shown over: the activity the tile is in (a `Context`) in the
 * app, a stand-in in the tests, which cannot make a `Context`. It is held for as long as a sheet
 * is open.
 * @param signIn shows Google's sign-in sheet over the [H] it is handed.
 * @param unlock shows Google Play's purchase sheet over it, then asks the server.
 */
class CloudBackupViewModel<H>(
    available: Boolean,
    private val readAccount: suspend () -> CloudAccount?,
    private val signIn: suspend (H) -> CloudSignInResult,
    private val signOut: suspend (H) -> Unit,
    private val price: suspend () -> String?,
    private val unlock: suspend (H) -> CloudUnlockResult,
    private val restore: suspend () -> CloudUnlockResult,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CloudBackupCardState(available = available))
    val state: StateFlow<CloudBackupCardState> = mutableState.asStateFlow()

    init {
        if (available) {
            viewModelScope.launch {
                val account = readAccount()
                mutableState.update { it.copy(account = account, read = true) }
                askPriceIfLocked()
            }
        }
    }

    /** "Sign in with Google". */
    fun onSignIn(host: H) {
        val shown = state.value
        if (!shown.available || !shown.read || shown.working || shown.account != null) return
        work {
            val result = signIn(host)
            if (result is CloudSignInResult.Failed) {
                eventLog.add(
                    clock(),
                    EventCategory.ERROR,
                    "Cloud backup sign-in failed. ${result.cause}",
                )
            }
            mutableState.update {
                it.copy(
                    account = (result as? CloudSignInResult.SignedIn)?.account,
                    note =
                        when (result) {
                            is CloudSignInResult.SignedIn, CloudSignInResult.Cancelled -> null
                            CloudSignInResult.NoGoogleAccount -> CloudNote.NO_GOOGLE_ACCOUNT
                            CloudSignInResult.Offline -> CloudNote.OFFLINE
                            is CloudSignInResult.Failed -> CloudNote.SIGN_IN_FAILED
                        },
                )
            }
            askPriceIfLocked()
        }
    }

    /** "Unlock", with the price on it. */
    fun onUnlock(host: H) {
        if (lockedAndIdle()) work { unlocked(unlock(host)) }
    }

    /** "Restore purchase". */
    fun onRestore() {
        if (lockedAndIdle()) work { unlocked(restore()) }
    }

    /** "Sign out". */
    fun onSignOut(host: H) {
        val shown = state.value
        if (shown.working || shown.account == null) return
        work {
            signOut(host)
            mutableState.update { it.copy(account = null, note = null) }
        }
    }

    private fun lockedAndIdle(): Boolean {
        val shown = state.value
        return !shown.working && shown.account?.unlocked == false
    }

    private fun unlocked(result: CloudUnlockResult) {
        mutableState.update {
            it.copy(
                account =
                    if (result == CloudUnlockResult.Unlocked) {
                        it.account?.copy(unlocked = true)
                    } else {
                        it.account
                    },
                note =
                    when (result) {
                        CloudUnlockResult.Unlocked -> CloudNote.UNLOCKED
                        CloudUnlockResult.Cancelled -> null
                        CloudUnlockResult.Pending -> CloudNote.PENDING
                        CloudUnlockResult.NotOffered -> CloudNote.NOT_OFFERED
                        CloudUnlockResult.OtherAccount -> CloudNote.OTHER_ACCOUNT
                        CloudUnlockResult.Refunded -> CloudNote.REFUNDED
                        CloudUnlockResult.NothingToRestore -> CloudNote.NOTHING_TO_RESTORE
                        CloudUnlockResult.Offline -> CloudNote.OFFLINE
                        is CloudUnlockResult.Failed -> CloudNote.UNLOCK_FAILED
                    },
            )
        }
    }

    /** Asks Google Play for the price, for an account that does not hold the unlock. */
    private suspend fun askPriceIfLocked() {
        if (state.value.account?.unlocked == false && state.value.price == null) {
            val asked = price()
            mutableState.update { it.copy(price = asked) }
        }
    }

    /** Runs [block] with the buttons held, and clears the last note first. */
    private fun work(block: suspend () -> Unit) {
        mutableState.update { it.copy(working = true, note = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }
}
