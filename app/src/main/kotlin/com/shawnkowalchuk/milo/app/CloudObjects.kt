package com.shawnkowalchuk.milo.app

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.ListenableWorker
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudAccountFile
import com.shawnkowalchuk.milo.data.cloud.CloudBackup
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockResult
import com.shawnkowalchuk.milo.data.cloud.CloudUploadOutcome
import com.shawnkowalchuk.milo.data.cloud.buildCloudAccountFile
import com.shawnkowalchuk.milo.data.cloud.buildCloudLedger
import com.shawnkowalchuk.milo.data.cloud.cloudAccountTag
import com.shawnkowalchuk.milo.data.cloud.restoreFrom
import com.shawnkowalchuk.milo.data.cloud.unlockAfter
import com.shawnkowalchuk.milo.data.cloud.uploadUntilQuiet
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.transferred
import com.shawnkowalchuk.milo.data.transfer.MainTransferDao
import com.shawnkowalchuk.milo.feature.settings.CloudBackupViewModel
import com.shawnkowalchuk.milo.platform.cloud.CloudFirebase
import com.shawnkowalchuk.milo.platform.cloud.CloudUnlockCheck
import com.shawnkowalchuk.milo.platform.cloud.CloudUploads
import com.shawnkowalchuk.milo.platform.cloud.FirestoreCloudStore
import com.shawnkowalchuk.milo.platform.cloud.GoogleCloudSignIn
import com.shawnkowalchuk.milo.platform.cloud.PlayBilling
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The long-lived objects of the optional cloud backup (ADR-012): the way into Firebase, the
 * account signed in, the sign-in, the one-time unlock (ADR-013), the upload and when it runs. Creating them starts nothing:
 * Firebase starts at a sign-in, a sign-out or an upload, and an upload runs only with an account.
 *
 * Like [WidgetObjects], a part of the [AppContainer], which creates it once
 * (`container.cloud`); a class of its own only because the container is at its size limit. It
 * reads the trips, the sent reports and the settings, and writes none of them.
 *
 * @param mainRows the one-transaction read of the trips and the sent reports.
 * @param tableChanges one item each time the trips or the sent reports change.
 */
class CloudObjects(
    appContext: Context,
    private val container: AppContainer,
    private val mainRows: MainTransferDao,
    tableChanges: Flow<Unit>,
) {
    val firebase: CloudFirebase by lazy { CloudFirebase(appContext) }

    private val accountFile: CloudAccountFile by lazy { buildCloudAccountFile(appContext) }

    private val googleSignIn: GoogleCloudSignIn by lazy { GoogleCloudSignIn(firebase, accountFile) }

    private val billing: PlayBilling by lazy { PlayBilling(appContext) }

    private val unlockCheck: CloudUnlockCheck by lazy { CloudUnlockCheck(firebase) }

    private val signedIn = MutableStateFlow<CloudAccount?>(null)

    /** The account signed in, once [start] has read it, and as sign-ins and sign-outs change it. */
    val account: StateFlow<CloudAccount?> = signedIn.asStateFlow()

    private val backup: CloudBackup by lazy {
        CloudBackup(
            rows = mainRows::allRows,
            settings = {
                try {
                    container.settingsStore.current().transferred()
                } catch (unreadable: IOException) {
                    null
                }
            },
            ledger = buildCloudLedger(appContext),
            store = FirestoreCloudStore(firebase),
        )
    }

    /** A change to the settings that travel; any other setting changes nothing in the cloud. */
    private val settingsChanges: Flow<Unit> =
        container.settingsStore.settings
            .map { it.transferred() }
            .catch { unreadable -> if (unreadable !is IOException) throw unreadable }
            .distinctUntilChanged()
            .drop(1)
            .map { }

    private val uploads = CloudUploads(appContext, account, merge(tableChanges, settingsChanges))

    /**
     * Reads which account is signed in, then has uploads follow it. Called once at the app's
     * start; it reads one small file and starts no Firebase.
     */
    fun start(scope: CoroutineScope) {
        if (!firebase.isConfigured) return
        scope.launch {
            signedIn.value = withContext(Dispatchers.IO) { accountFile.read() }
            uploads.start(scope)
        }
    }

    /** The account in its file, for a screen opened before [start] has read it. */
    fun readAccount(): CloudAccount? = accountFile.read()

    /**
     * Google's sheet over [context], then Firebase, then whether the account holds the unlock
     * already (bought on another phone). An unlocked account starts the uploads.
     */
    suspend fun signIn(context: Context): CloudSignInResult {
        val result = googleSignIn.signIn(context)
        if (result !is CloudSignInResult.SignedIn) return result
        val account = result.account.copy(
            unlocked =
                unlockCheck.holdsUnlock(result.account.id) == true,
        )
        keep(account)
        return CloudSignInResult.SignedIn(account)
    }

    /** The unlock's price, as Google Play shows it to this user, or null if it cannot say. */
    suspend fun price(): String? = billing.price()

    /** Google Play's purchase sheet over [context], then the purchase check on the server. */
    suspend fun unlock(context: Context): CloudUnlockResult {
        val account = signedIn.value ?: return CloudUnlockResult.Failed("Not signed in")
        val activity = context as? Activity ?: return CloudUnlockResult.Failed("No activity")
        val result =
            unlockAfter(
                billing.buy(activity, cloudAccountTag(account.id)),
                billing::owned,
                unlockCheck,
            )
        return afterUnlock(account, result)
    }

    /** "Restore purchase": the purchases this phone's Google Play account holds, to the server. */
    suspend fun restore(): CloudUnlockResult {
        val account = signedIn.value ?: return CloudUnlockResult.Failed("Not signed in")
        return afterUnlock(account, restoreFrom(billing.owned(), unlockCheck))
    }

    private suspend fun afterUnlock(
        account: CloudAccount,
        result: CloudUnlockResult,
    ): CloudUnlockResult {
        when (result) {
            CloudUnlockResult.Unlocked -> {
                keep(account.copy(unlocked = true))
                log("Cloud backup unlocked.")
            }

            is CloudUnlockResult.Failed -> log("Cloud backup unlock failed. ${result.cause}")

            else -> Unit
        }
        return result
    }

    /** Keeps [account] in its file and as the account signed in. */
    private suspend fun keep(account: CloudAccount) {
        try {
            withContext(Dispatchers.IO) { accountFile.write(account) }
        } catch (unwritable: IOException) {
            log(
                "Cloud backup: the account's file could not be written. ${unwritable.javaClass.simpleName}",
            )
        }
        signedIn.value = account
    }

    /** Signs out on this phone; the uploads stop. The cloud keeps what it has. */
    suspend fun signOut(context: Context) {
        googleSignIn.signOut(context)
        signedIn.value = null
    }

    /**
     * One upload, for WorkManager's job (`CloudBackupWorker`), which may run in a process that
     * has just started: the account is read from its file if [start] has not yet.
     */
    suspend fun upload(): ListenableWorker.Result {
        val account =
            signedIn.value ?: withContext(Dispatchers.IO) { accountFile.read() }
                ?: return ListenableWorker.Result.success()
        if (!firebase.isConfigured || !account.unlocked) return ListenableWorker.Result.success()
        if (firebase.auth().currentUser?.uid != account.id) {
            log("Cloud backup: not signed in to Firebase as the account kept here. Not sent.")
            return ListenableWorker.Result.failure()
        }
        return when (uploadUntilQuiet(backup, account.id, ::log)) {
            CloudUploadOutcome.DONE -> ListenableWorker.Result.success()

            CloudUploadOutcome.TRY_LATER -> ListenableWorker.Result.retry()

            CloudUploadOutcome.REFUSED -> {
                // Refused because the unlock is gone (a refund)? Then uploads stop here too,
                // and the tile offers the unlock again.
                if (unlockCheck.holdsUnlock(account.id) == false) {
                    keep(account.copy(unlocked = false))
                    log("Cloud backup: this account no longer holds the unlock. Uploads stopped.")
                }
                ListenableWorker.Result.failure()
            }
        }
    }

    private suspend fun log(line: String) {
        container.eventLogRepository.add(container.clock(), EventCategory.CLOUD, line)
    }
}

/** How the Settings screen's Cloud backup tile gets its ViewModel. */
internal fun cloudBackupViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val cloud = container.cloud
            CloudBackupViewModel<Context>(
                available = cloud.firebase.isConfigured,
                readAccount = {
                    // The file is read at the app's start; until then, read it here.
                    cloud.account.value ?: withContext(Dispatchers.IO) { cloud.readAccount() }
                },
                signIn = cloud::signIn,
                signOut = cloud::signOut,
                price = cloud::price,
                unlock = cloud::unlock,
                restore = cloud::restore,
                eventLog = container.eventLogRepository,
                clock = container.clock,
            )
        }
    }
