package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the last press of "Sign in with Google" came to, when it is worth saying. */
enum class CloudSignInNote { NO_GOOGLE_ACCOUNT, OFFLINE, FAILED }

/**
 * The Cloud backup tile.
 *
 * @param available false in a build without the Firebase settings: then the tile says so and
 * offers nothing.
 * @param account the account signed in, or null. Unknown until the file was read: [read].
 * @param read whether the account's file has been read yet. No button is offered before.
 */
data class CloudBackupCardState(
    val available: Boolean,
    val account: CloudAccount? = null,
    val read: Boolean = false,
    val working: Boolean = false,
    val note: CloudSignInNote? = null,
)

/**
 * The Settings tile of the optional cloud backup (ADR-012). In this first slice it signs in to a
 * Google account and out again, and shows which account it is; the upload, the one-time unlock
 * and the restore come in the slices after (FINDINGS_LOG, 2026-11-11). Phone only: Android Auto
 * has no Settings, and an account is never chosen while driving.
 *
 * @param H what Google's sheet is shown over: the activity the tile is in (a `Context`) in the
 * app, a stand-in in the tests, which cannot make a `Context`. It is held for as long as the
 * sheet is open.
 * @param signIn shows Google's sheet over the [H] it is handed.
 */
class CloudBackupViewModel<H>(
    available: Boolean,
    private val readAccount: suspend () -> CloudAccount?,
    private val signIn: suspend (H) -> CloudSignInResult,
    private val signOut: suspend (H) -> Unit,
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
            }
        }
    }

    /** "Sign in with Google". */
    fun onSignIn(host: H) {
        val shown = state.value
        if (!shown.available || !shown.read || shown.working || shown.account != null) return
        mutableState.update { it.copy(working = true, note = null) }
        viewModelScope.launch {
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
                    working = false,
                    account = (result as? CloudSignInResult.SignedIn)?.account,
                    note =
                        when (result) {
                            is CloudSignInResult.SignedIn, CloudSignInResult.Cancelled -> null
                            CloudSignInResult.NoGoogleAccount -> CloudSignInNote.NO_GOOGLE_ACCOUNT
                            CloudSignInResult.Offline -> CloudSignInNote.OFFLINE
                            is CloudSignInResult.Failed -> CloudSignInNote.FAILED
                        },
                )
            }
        }
    }

    /** "Sign out". */
    fun onSignOut(host: H) {
        val shown = state.value
        if (shown.working || shown.account == null) return
        mutableState.update { it.copy(working = true, note = null) }
        viewModelScope.launch {
            signOut(host)
            mutableState.update { it.copy(working = false, account = null) }
        }
    }
}
