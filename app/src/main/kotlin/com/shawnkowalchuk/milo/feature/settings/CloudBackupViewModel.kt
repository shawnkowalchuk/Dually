package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudRestoreResult
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import com.shawnkowalchuk.milo.data.cloud.CloudStartResult
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
    PLAY_NOT_READY,

    /** The backup's trips are on this phone now: [CloudBackupCardState.restoredTrips]. */
    RESTORED,

    /** Not while a trip is being recorded. */
    TRIP_IN_PROGRESS,

    /** The restore did not work; nothing was changed, or the safety copy has it. */
    RESTORE_FAILED,

    /** This phone backs up to the account now, in place of the other. */
    BACKING_UP_HERE,

    /** The online backup could not be reached; nothing was changed. */
    BACKUP_OFFLINE,

    /** The account could not be deleted; nothing was changed. */
    DELETE_FAILED,

    /** The online backup and the account are deleted. */
    DELETED,
}

/**
 * The Online backup tile (named Cloud backup until 2026-11-12).
 *
 * @param available false in a build without the Firebase settings: then the tile says so and
 * offers nothing.
 * @param account the account signed in, or null. Unknown until the file was read: [read].
 * @param read whether the account's file has been read yet. No button is offered before.
 * @param price the unlock's price as Google Play shows it, once asked, or null.
 * @param standing how the backup stands for an account that holds the unlock (since
 * 2026-11-12): this phone backs up, or another phone's trips are in it and the user chooses.
 * Null until it was asked.
 * @param restoredTrips how many trips the last restore brought back, for its note.
 */
data class CloudBackupCardState(
    val available: Boolean,
    val account: CloudAccount? = null,
    val read: Boolean = false,
    val working: Boolean = false,
    val note: CloudNote? = null,
    val price: String? = null,
    val standing: CloudStartResult? = null,
    val restoredTrips: Int = 0,
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
    private val startBackup: suspend () -> CloudStartResult = { CloudStartResult.BackingUp },
    private val restoreTrips: suspend () -> CloudRestoreResult = {
        CloudRestoreResult.Failed("Not offered")
    },
    private val backUpHere: suspend () -> Boolean = { false },
    private val deleteAccount: suspend (H) -> Boolean = { false },
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
                lookAtBackup()
            }
        }
    }

    /**
     * "Purchase online backup" (since 2026-11-12, Shawn: "it should probably state something
     * like purchase online backup"): signs in with Google first where no account is, then shows
     * Google Play's purchase sheet. An account that turns out to hold the unlock already is not
     * asked to pay again.
     */
    fun onPurchase(host: H) {
        if (readyToBuy()) work { if (signedIn(host) && locked()) unlocked(unlock(host)) }
    }

    /** "Restore purchase": signs in first where no account is, then looks for the purchase. */
    fun onRestore(host: H) {
        if (readyToBuy()) work { if (signedIn(host) && locked()) unlocked(restore()) }
    }

    /** "Restore my trips here", once the user said yes to the question. */
    fun onRestoreTrips() {
        if (!unlockedAndIdle()) return
        work {
            val note =
                when (val result = restoreTrips()) {
                    is CloudRestoreResult.Restored -> {
                        mutableState.update {
                            it.copy(
                                restoredTrips = result.trips,
                                standing = CloudStartResult.BackingUp,
                            )
                        }
                        CloudNote.RESTORED
                    }

                    CloudRestoreResult.TripInProgress -> CloudNote.TRIP_IN_PROGRESS

                    CloudRestoreResult.Offline -> CloudNote.BACKUP_OFFLINE

                    is CloudRestoreResult.Failed -> CloudNote.RESTORE_FAILED
                }
            mutableState.update { it.copy(note = note) }
        }
    }

    /** "Back up this phone instead", once the user said yes to the question. */
    fun onBackUpHere() {
        if (!unlockedAndIdle()) return
        work {
            val done = backUpHere()
            mutableState.update {
                if (done) {
                    it.copy(standing = CloudStartResult.BackingUp, note = CloudNote.BACKING_UP_HERE)
                } else {
                    it.copy(note = CloudNote.BACKUP_OFFLINE)
                }
            }
        }
    }

    /** "Delete my online backup and account", once the user said yes to the question. */
    fun onDeleteAccount(host: H) {
        val shown = state.value
        if (shown.working || shown.account == null) return
        work {
            val done = deleteAccount(host)
            mutableState.update {
                if (done) {
                    it.copy(account = null, standing = null, note = CloudNote.DELETED)
                } else {
                    it.copy(note = CloudNote.DELETE_FAILED)
                }
            }
        }
    }

    /** "Sign out". */
    fun onSignOut(host: H) {
        val shown = state.value
        if (shown.working || shown.account == null) return
        work {
            signOut(host)
            mutableState.update { it.copy(account = null, note = null, standing = null) }
        }
    }

    /** Whether a purchase or a restore may start: the backup is offered, read, idle and locked. */
    private fun readyToBuy(): Boolean {
        val shown = state.value
        return shown.available && shown.read && !shown.working && shown.account?.unlocked != true
    }

    private fun locked(): Boolean = state.value.account?.unlocked == false

    /**
     * True when an account is signed in, after Google's sign-in sheet if there was none. What
     * a sign-in that did not happen came to is said in the tile's note.
     */
    private suspend fun signedIn(host: H): Boolean {
        if (state.value.account != null) return true
        val result = signIn(host)
        if (result is CloudSignInResult.Failed) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "Cloud backup sign-in failed. ${result.cause}",
            )
        }
        val account = (result as? CloudSignInResult.SignedIn)?.account
        mutableState.update {
            it.copy(
                account = account,
                note =
                    when (result) {
                        // An account that holds the unlock already: nothing to buy.
                        is CloudSignInResult.SignedIn ->
                            CloudNote.UNLOCKED.takeIf { account?.unlocked == true }

                        CloudSignInResult.Cancelled -> null

                        CloudSignInResult.NoGoogleAccount -> CloudNote.NO_GOOGLE_ACCOUNT

                        CloudSignInResult.Offline -> CloudNote.OFFLINE

                        is CloudSignInResult.Failed -> CloudNote.SIGN_IN_FAILED
                    },
            )
        }
        askPriceIfLocked()
        lookAtBackup()
        return account != null
    }

    private fun unlockedAndIdle(): Boolean {
        val shown = state.value
        return !shown.working && shown.account?.unlocked == true
    }

    /** Asks how the backup stands, for an account that holds the unlock. */
    private suspend fun lookAtBackup() {
        if (state.value.account?.unlocked == true) {
            val standing = startBackup()
            mutableState.update { it.copy(standing = standing) }
        }
    }

    private suspend fun unlocked(result: CloudUnlockResult) {
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
                        CloudUnlockResult.PlayNotReady -> CloudNote.PLAY_NOT_READY
                        is CloudUnlockResult.Failed -> CloudNote.UNLOCK_FAILED
                    },
            )
        }
        lookAtBackup()
    }

    /**
     * Asks Google Play for the price, for the Purchase button: before anyone is signed in too,
     * since 2026-11-12, because the button shows it then.
     */
    private suspend fun askPriceIfLocked() {
        if (state.value.account?.unlocked != true && state.value.price == null) {
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
