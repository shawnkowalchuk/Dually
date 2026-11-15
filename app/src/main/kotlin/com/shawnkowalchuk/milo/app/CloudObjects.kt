package com.shawnkowalchuk.milo.app

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.ListenableWorker
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.cloud.CLOUD_BACKUP
import com.shawnkowalchuk.milo.data.cloud.CLOUD_OWNER
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudAccountFile
import com.shawnkowalchuk.milo.data.cloud.CloudBackup
import com.shawnkowalchuk.milo.data.cloud.CloudCopy
import com.shawnkowalchuk.milo.data.cloud.CloudInstall
import com.shawnkowalchuk.milo.data.cloud.CloudLedger
import com.shawnkowalchuk.milo.data.cloud.CloudRestoreException
import com.shawnkowalchuk.milo.data.cloud.CloudRestoreResult
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import com.shawnkowalchuk.milo.data.cloud.CloudStartResult
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockResult
import com.shawnkowalchuk.milo.data.cloud.CloudUploadOutcome
import com.shawnkowalchuk.milo.data.cloud.buildCloudAccountFile
import com.shawnkowalchuk.milo.data.cloud.buildCloudInstall
import com.shawnkowalchuk.milo.data.cloud.buildCloudLedger
import com.shawnkowalchuk.milo.data.cloud.cloudAccountTag
import com.shawnkowalchuk.milo.data.cloud.cloudAsksToMerge
import com.shawnkowalchuk.milo.data.cloud.cloudCopyOf
import com.shawnkowalchuk.milo.data.cloud.cloudOwnerOf
import com.shawnkowalchuk.milo.data.cloud.cloudPhoneDocument
import com.shawnkowalchuk.milo.data.cloud.cloudPhoneFolder
import com.shawnkowalchuk.milo.data.cloud.cloudRemovableBy
import com.shawnkowalchuk.milo.data.cloud.cloudRestoreExport
import com.shawnkowalchuk.milo.data.cloud.inFolder
import com.shawnkowalchuk.milo.data.cloud.putBackReplaced
import com.shawnkowalchuk.milo.data.cloud.readOtherPhones
import com.shawnkowalchuk.milo.data.cloud.restoreFrom
import com.shawnkowalchuk.milo.data.cloud.syncLogbook
import com.shawnkowalchuk.milo.data.cloud.syncShared
import com.shawnkowalchuk.milo.data.cloud.unlockAfter
import com.shawnkowalchuk.milo.data.cloud.uploadUntilQuiet
import com.shawnkowalchuk.milo.data.cloud.withEdits
import com.shawnkowalchuk.milo.data.cloud.withShared
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.transferred
import com.shawnkowalchuk.milo.data.transfer.MainTransferDao
import com.shawnkowalchuk.milo.data.trip.TripDao
import com.shawnkowalchuk.milo.feature.settings.CloudBackupViewModel
import com.shawnkowalchuk.milo.platform.cloud.CloudFirebase
import com.shawnkowalchuk.milo.platform.cloud.CloudUnlockCheck
import com.shawnkowalchuk.milo.platform.cloud.CloudUploads
import com.shawnkowalchuk.milo.platform.cloud.FirestoreCloudStore
import com.shawnkowalchuk.milo.platform.cloud.GoogleCloudSignIn
import com.shawnkowalchuk.milo.platform.cloud.PlayBilling
import com.shawnkowalchuk.milo.platform.cloud.ReplacedChangeNotification
import com.shawnkowalchuk.milo.platform.system.readInstalledVersion
import com.shawnkowalchuk.milo.platform.transfer.TransferOutcome
import com.shawnkowalchuk.milo.platform.transfer.TransferRefusal
import java.io.IOException
import java.time.ZoneId
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
 * @param tripTable the trips, for the other phones' trips this phone holds (ADR-017).
 * @param tableChanges one item each time the trips or the sent reports change.
 */
class CloudObjects(
    private val appContext: Context,
    private val container: AppContainer,
    private val mainRows: MainTransferDao,
    private val tripTable: TripDao,
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

    private val ledger: CloudLedger by lazy { buildCloudLedger(appContext) }
    private val store: FirestoreCloudStore by lazy { FirestoreCloudStore(firebase) }
    private val install: CloudInstall by lazy { buildCloudInstall(appContext) }

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
            ledger = ledger,
            store = store,
            shape = { snapshot ->
                val phone = withContext(Dispatchers.IO) { install.id() }
                snapshot.inFolder(
                    cloudPhoneFolder(phone),
                    cloudPhoneDocument(phone, phoneName(appContext), "android"),
                ).withEdits(withContext(Dispatchers.IO) { tripTable.findFromOtherPhones() })
                    .withShared(
                        try {
                            container.settingsStore.current()
                        } catch (unreadable: IOException) {
                            null
                        },
                    )
            },
            removable = { path ->
                cloudRemovableBy(withContext(Dispatchers.IO) { install.id() }, path)
            },
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

    private val replacedNotification: ReplacedChangeNotification by lazy {
        ReplacedChangeNotification(appContext, opens = MainActivity::class.java)
    }

    /**
     * "Put back" on a trip whose history holds a change another phone's replaced (ADR-017):
     * the trip takes that change again, and the upload sends it to every phone.
     */
    suspend fun putBack(tripId: Long): Boolean {
        val done =
            withContext(Dispatchers.IO) {
                putBackReplaced(tripTable, tripId, container.clock(), phoneName(appContext))
            }
        if (done) {
            log("Cloud backup: trip $tripId: the change that was replaced was put back.")
            uploads.uploadNow()
        }
        return done
    }

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
     * How the backup stands for the account signed in, for the Online backup tile (ADR-017):
     * this phone backs up and holds the other phones' trips, or it has trips of its own while
     * the others have theirs, and asks once whether to merge them. Asked when the tile opens and
     * after each unlock; it runs an upload when nothing is to be asked.
     */
    suspend fun startBackup(): CloudStartResult {
        val account = signedIn.value?.takeIf { it.unlocked } ?: return CloudStartResult.Offline
        val others =
            try {
                readOtherPhones(store, account.id, withContext(Dispatchers.IO) { install.id() })
            } catch (unreachable: IOException) {
                return CloudStartResult.Offline
            }
        val own = ownFinished()
        if (cloudAsksToMerge(own, others, account.backingUp)) {
            if (account.backingUp != false) keep(account.copy(backingUp = false))
            return CloudStartResult.Merge(own, others.tripCount)
        }
        if (account.backingUp != true) keep(account.copy(backingUp = true))
        uploads.uploadNow()
        return CloudStartResult.BackingUp
    }

    /** "Merge": this phone's trips join the logbook, and it backs up from now on. */
    suspend fun merge(): Boolean {
        val account = signedIn.value?.takeIf { it.unlocked } ?: return false
        keep(account.copy(backingUp = true))
        log("Cloud backup: this phone's trips were merged into the logbook of the account.")
        uploads.uploadNow()
        return true
    }

    /**
     * "Use only the backup's trips": this phone's own trips are taken off it, through the
     * import (a safety copy first) of a logbook with none, and it holds the other phones'
     * trips from then on (ADR-017). Asked first on the tile.
     */
    suspend fun useBackupOnly(): CloudRestoreResult {
        val account = signedIn.value?.takeIf { it.unlocked }
            ?: return CloudRestoreResult.Failed("Not unlocked")
        val export =
            cloudRestoreExport(
                cloudCopyOf(emptyMap()),
                container.clock(),
                ZoneId.systemDefault(),
                readInstalledVersion(appContext).name,
            )
        return when (val outcome = container.transfer.dataTransfer.restoreFromBackup(export)) {
            is TransferOutcome.Imported -> {
                withContext(Dispatchers.IO) { ledger.write(account.id, emptyMap()) }
                keep(account.copy(backingUp = true))
                log("Cloud backup: this phone's own trips were set aside; it holds the logbook's.")
                uploads.uploadNow()
                CloudRestoreResult.Restored(0)
            }

            is TransferOutcome.Refused ->
                if (outcome.why == TransferRefusal.TripInProgress) {
                    CloudRestoreResult.TripInProgress
                } else {
                    CloudRestoreResult.Failed("Import refused: ${outcome.why}")
                }

            null -> CloudRestoreResult.Failed("Another export or import is under way")

            else -> CloudRestoreResult.Failed("Import: $outcome")
        }
    }

    /**
     * "Delete my online backup and account": the server deletes the backup, the unlock and the
     * sign-in account, and releases the purchase; then this phone forgets the account.
     *
     * @return false if the server could not be reached or refused; nothing is forgotten then.
     */
    suspend fun deleteAccount(context: Context): Boolean {
        if (signedIn.value == null) return false
        if (!unlockCheck.deleteAccount()) {
            log("Cloud backup: the account could not be deleted. Nothing was changed.")
            return false
        }
        withContext(Dispatchers.IO) {
            ledger.clear()
            accountFile.clear()
        }
        googleSignIn.signOut(context)
        signedIn.value = null
        log("Cloud backup: the online backup and the account were deleted, at the user's request.")
        return true
    }

    /** How many finished trips of its own this phone holds. */
    private suspend fun ownFinished(): Int = withContext(Dispatchers.IO) {
        tripTable.findOwn().count { it.status == TripStatus.FINISHED }
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
        // Every phone backs up into its own folder (ADR-017), once it has joined the logbook.
        val phone = withContext(Dispatchers.IO) { install.id() }
        val others =
            try {
                readOtherPhones(store, account.id, phone)
            } catch (unreachable: IOException) {
                return ListenableWorker.Result.retry()
            }
        if (cloudAsksToMerge(ownFinished(), others, account.backingUp)) {
            if (account.backingUp != false) keep(account.copy(backingUp = false))
            return ListenableWorker.Result.success()
        }
        if (account.backingUp != true) keep(account.copy(backingUp = true))
        try {
            withContext(Dispatchers.IO) {
                val notices =
                    syncLogbook(
                        store,
                        account.id,
                        phone,
                        phoneName(appContext),
                        container.clock(),
                        others,
                        tripTable,
                        ledger,
                        ::log,
                    )
                if (notices.isNotEmpty()) replacedNotification.show(notices)
                syncShared(store, account.id, container.settingsStore, ledger, ::log)
            }
        } catch (unreachable: IOException) {
            return ListenableWorker.Result.retry()
        }
        return when (uploadUntilQuiet(backup, account.id, ::log)) {
            CloudUploadOutcome.DONE -> {
                forgetEarlierOwner(account, phone)
                ListenableWorker.Result.success()
            }

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

    /**
     * The backup an older version kept at the account's top named this phone as its owner:
     * its documents were moved into this phone's folder by the run that just ended (the
     * ledger's old paths were removed as the new ones were written), so the owner document
     * goes too.
     */
    private suspend fun forgetEarlierOwner(account: CloudAccount, phone: String) {
        try {
            val earlier = store.readCollections(account.id, listOf(CLOUD_BACKUP))
            if (earlier[CLOUD_OWNER]?.let(::cloudOwnerOf) == phone) {
                store.apply(account.id, emptyList(), listOf(CLOUD_OWNER))
                log("Cloud backup: this phone's backup moved into its own folder.")
            }
        } catch (unreachable: IOException) {
            // Tried again after the next upload.
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
                startBackup = cloud::startBackup,
                useBackupOnly = cloud::useBackupOnly,
                merge = cloud::merge,
                deleteAccount = cloud::deleteAccount,
                eventLog = container.eventLogRepository,
                clock = container.clock,
            )
        }
    }

/**
 * What this phone is called in the logbook (ADR-017): the name the owner gave it in Android's
 * settings ("Shawn's Galaxy"), else its model.
 */
private fun phoneName(context: Context): String = android.provider.Settings.Global.getString(
    context.contentResolver,
    android.provider.Settings.Global.DEVICE_NAME,
)?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
