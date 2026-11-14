package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.io.IOException
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.appFolderPath
import com.shawnkowalchuk.milo.data.cloud.CLOUD_BACKUP
import com.shawnkowalchuk.milo.data.cloud.CLOUD_OWNER
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudBackup
import com.shawnkowalchuk.milo.data.cloud.CloudDocument
import com.shawnkowalchuk.milo.data.cloud.CloudRefusedException
import com.shawnkowalchuk.milo.data.cloud.CloudStartResult
import com.shawnkowalchuk.milo.data.cloud.CloudStore
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockResult
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockServer
import com.shawnkowalchuk.milo.data.cloud.CloudUnreachableException
import com.shawnkowalchuk.milo.data.cloud.CloudUploadOutcome
import com.shawnkowalchuk.milo.data.cloud.OtherPhones
import com.shawnkowalchuk.milo.data.cloud.appleUnlockRequestBody
import com.shawnkowalchuk.milo.data.cloud.cloudAppleAccountToken
import com.shawnkowalchuk.milo.data.cloud.cloudAsksToMerge
import com.shawnkowalchuk.milo.data.cloud.cloudCopyOf
import com.shawnkowalchuk.milo.data.cloud.cloudOwnerOf
import com.shawnkowalchuk.milo.data.cloud.cloudPhoneDocument
import com.shawnkowalchuk.milo.data.cloud.cloudPhoneFolder
import com.shawnkowalchuk.milo.data.cloud.cloudRestoreExport
import com.shawnkowalchuk.milo.data.cloud.inFolder
import com.shawnkowalchuk.milo.data.cloud.logbookChange
import com.shawnkowalchuk.milo.data.cloud.readOtherPhones
import com.shawnkowalchuk.milo.data.cloud.restoreFrom
import com.shawnkowalchuk.milo.data.cloud.unlockAnswer
import com.shawnkowalchuk.milo.data.cloud.uploadUntilQuiet
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.transferred
import com.shawnkowalchuk.milo.data.transfer.MainTransferDao
import com.shawnkowalchuk.milo.data.trip.TripDao
import com.shawnkowalchuk.milo.feature.cloud.englishCloudFormat
import com.shawnkowalchuk.milo.feature.cloud.englishCloudText
import com.shawnkowalchuk.milo.feature.service.englishServiceText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath

// The online backup on the iPhone (phase 4h, 2026-11-14; ADR-012, ADR-013, ADR-016): Android's
// backup (app/CloudObjects.kt) with the same documents, the same comparison, the same "one phone
// per account", the same restore through the import and the same unlock. What is Apple's own is
// in Swift: Firebase's iPhone libraries, Sign in with Apple and Google, and StoreKit 2.

/** How long one answer from Firebase is waited for, as Android's batch timeout. */
private const val ANSWER_TIMEOUT_MS = 60_000L

/**
 * How long the trips must stay unchanged before an upload starts. Android waits 30 seconds, with
 * WorkManager to run it later; an iPhone app may be put to sleep soon after a trip ends, so the
 * iPhone waits less.
 */
private const val QUIET_MS = 5_000L

/** The first wait before an upload stopped by the network is tried again, doubling each time. */
private const val RETRY_FIRST_MS = 60_000L

private const val RETRY_LONGEST_MS = 60L * 60L * 1_000L

private const val UNLOCK_FUNCTION = "unlockBackup"
private const val DELETE_FUNCTION = "deleteMyAccount"

/**
 * Firebase, as the Swift code reaches it (FirebaseCloud.swift). In Swift because Firebase's
 * iPhone libraries are Swift and Objective-C. Firebase is started by the first of these calls,
 * and only an account signed in makes one (ADR-012: Firebase stays silent until backup is on).
 * Each call answers its reply exactly once.
 */
interface AppleCloud {
    /** The Firebase account signed in now, or null. */
    fun signedInAccount(): String?

    /** Writes [documents] and removes [removals] under `users/[account]/`, all or nothing. */
    fun apply(
        account: String,
        documents: List<CloudDocument>,
        removals: List<String>,
        reply: CloudReply,
    )

    /**
     * Every document of the [collections] of the account's folder (`phones`,
     * `phones/{install}/trips`, `trips`, `backup`), read from the server, by its path under the
     * account's folder: `phones/{install}`, `phones/{install}/trips/17` (ADR-017).
     */
    fun readCollections(account: String, collections: List<String>, reply: CloudReadReply)

    /** Whether `unlocks/[account]` exists, read from the server. */
    fun holdsUnlock(account: String, reply: CloudHoldsReply)

    /** Calls the server's function [name] with [body], signed in as the account. */
    fun call(name: String, body: String, reply: CloudCallReply)
}

/** Where a write's answer goes. */
class CloudReply internal constructor(private val answer: (IOException?) -> Unit) {
    fun done() = answer(null)

    /** Not stored: no network, a timeout, the server busy. Tried again later. */
    fun unreachable(reason: String) = answer(CloudUnreachableException(reason))

    /** The rules said no: no unlock, or not signed in. */
    fun refused(reason: String) = answer(CloudRefusedException(reason))
}

/** Where a read's answer goes: each document's path under the account's folder, and its JSON. */
class CloudReadReply internal constructor(
    private val answer: (Map<String, String>?, IOException?) -> Unit,
) {
    fun documents(byPath: Map<String, String>) = answer(byPath, null)

    fun unreachable(reason: String) = answer(null, CloudUnreachableException(reason))

    fun refused(reason: String) = answer(null, CloudRefusedException(reason))
}

/** Where the answer to "does this account hold the unlock?" goes. */
class CloudHoldsReply internal constructor(private val answer: (Boolean?) -> Unit) {
    fun holds(unlocked: Boolean) = answer(unlocked)

    /** The server could not be reached. */
    fun unknown() = answer(null)
}

/** Where a call's answer goes: the HTTP [code] and [body], or no answer at all. */
class CloudCallReply internal constructor(private val answer: (Int?, String) -> Unit) {
    fun answered(code: Int, body: String) = answer(code, body)

    fun offline() = answer(null, "")
}

/**
 * The online backup, for the iPhone's Settings tile and its uploads.
 *
 * @param changes one item each time the trips or the sent reports change.
 */
class IosCloud internal constructor(
    private val apple: AppleCloud,
    private val files: IosCloudFiles,
    private val mainRows: MainTransferDao,
    private val trips: TripDao,
    private val settings: SettingsStore,
    private val transfer: IosTransfer,
    private val eventLog: EventLogRepository,
    private val changes: Flow<Unit>,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    private val store = AppleCloudStore()
    private val server = CloudUnlockServer { signed -> callUnlock(signed) }
    private val backup =
        CloudBackup(
            rows = mainRows::allRows,
            settings = {
                try {
                    settings.current().transferred()
                } catch (unreadable: IOException) {
                    null
                }
            },
            ledger = files,
            store = store,
            shape = { snapshot ->
                val phone = files.install()
                snapshot.inFolder(
                    cloudPhoneFolder(phone),
                    cloudPhoneDocument(phone, "iPhone", "iphone"),
                )
            },
        )
    private val uploading = Mutex()
    private var watching: Job? = null
    private var retry: Job? = null
    private var retryWaitMs = RETRY_FIRST_MS

    /** The account signed in, as kept on this iPhone, or null. Reads one small file. */
    fun account(): CloudAccount? = files.account()

    /**
     * What a purchase is to carry to Apple as its `appAccountToken` (ADR-016), for the account
     * signed in; null if none is.
     */
    fun appleAccountToken(): String? = files.account()?.let { cloudAppleAccountToken(it.id) }

    /**
     * Uploads follow every change from now on, five seconds after the last, and once now (the
     * app's start). Called once, at the start of an app built with Firebase's settings; it
     * starts no Firebase while no account is signed in.
     */
    // TODO(debt): no daily upload on the iPhone, as Android's WorkManager runs; a failed upload
    //  waits for the next change, trip end or visit to the app. FINDINGS_LOG, 2026-11-14.
    @OptIn(FlowPreview::class)
    fun watch() {
        if (watching != null) return
        val settingsChanges =
            settings.settings
                .map { it.transferred() }
                .catch { unreadable -> if (unreadable !is IOException) throw unreadable }
                .distinctUntilChanged()
                .drop(1)
                .map { }
        watching = scope.launch {
            merge(changes, settingsChanges).debounce(QUIET_MS).collect { upload("a change") }
        }
    }

    // ---- The tile ------------------------------------------------------------------------------

    /**
     * The Online backup tile as it stands. For an account that holds the unlock, the backup is
     * read to see whether this iPhone backs up to it (`cloudStart`), as Android's tile does
     * when it opens.
     *
     * @param available whether this build has Firebase's settings.
     * @param price the purchase's price as the App Store shows it, or null.
     */
    suspend fun tile(available: Boolean, price: String?): CloudTile {
        val words = locale()
        val title = englishCloudText("settings_cloud_title")
        val cancel = englishServiceText("action_cancel")
        if (!available) {
            return CloudTile(title, englishCloudText("settings_cloud_unavailable"), cancel = cancel)
        }
        val account = files.account()
        val purchase =
            price?.let { englishCloudFormat("settings_cloud_purchase_price", words, it) }
                ?: englishCloudText("settings_cloud_purchase")
        val restorePurchase = englishCloudText("settings_cloud_restore")
        if (account == null) {
            return CloudTile(
                title = title,
                text = englishCloudText("settings_cloud_explained"),
                purchase = purchase,
                restorePurchase = restorePurchase,
                signIn = SignInWords.sheet(),
                cancel = cancel,
            )
        }
        val email = shown(account)
        val delete = deleteQuestion()
        if (!account.unlocked) {
            return CloudTile(
                title = title,
                text = englishCloudFormat("settings_cloud_locked", words, email),
                purchase = purchase,
                restorePurchase = restorePurchase,
                signOut = englishCloudText("settings_cloud_sign_out"),
                deleteAccount = delete,
                signedIn = true,
                cancel = cancel,
            )
        }
        val merge = startBackup() as? CloudStartResult.Merge
        if (merge == null) {
            return CloudTile(
                title = title,
                text = englishCloudFormat("settings_cloud_signed_in", words, email),
                on = englishCloudText("settings_cloud_on"),
                signOut = englishCloudText("settings_cloud_sign_out"),
                deleteAccount = delete,
                signedIn = true,
                cancel = cancel,
            )
        }
        // ADR-017: a phone with trips of its own asks once before it joins the logbook.
        return CloudTile(
            title = title,
            text =
                englishCloudFormat(
                    "settings_cloud_merge_question",
                    words,
                    email,
                    merge.own,
                    merge.others,
                ),
            merge = englishCloudText("settings_cloud_merge"),
            useBackup =
                CloudQuestion(
                    button = englishCloudText("settings_cloud_use_backup"),
                    title = englishCloudText("settings_cloud_use_backup_title"),
                    text = englishCloudText("settings_cloud_use_backup_text"),
                    confirm = englishCloudText("settings_cloud_use_backup_confirm"),
                ),
            signOut = englishCloudText("settings_cloud_sign_out"),
            deleteAccount = delete,
            signedIn = true,
            cancel = cancel,
        )
    }

    // ---- Signing in and out --------------------------------------------------------------------

    /**
     * Swift signed in to Firebase as [id] ([email], or empty if Apple hid it): the account is
     * kept, with whether it holds the unlock already (bought on another phone, or on Android).
     *
     * @return the note to show, if any.
     */
    suspend fun signedIn(id: String, email: String): CloudNote? {
        val unlocked = holdsUnlock(id) == true
        val kept = files.account()?.takeIf { it.id == id }
        keep(CloudAccount(id, email, unlocked, kept?.backingUp))
        log(
            "Cloud backup: signed in on the iPhone." +
                if (unlocked) " The account holds the unlock." else "",
        )
        if (unlocked) scope.launch { upload("a sign-in") }
        return if (unlocked) good(englishCloudText("settings_cloud_unlocked")) else null
    }

    /**
     * A sign-in that did not happen: closed by the user (nothing to say), offline, or failed for
     * [cause], which goes to the Log, never the account.
     */
    suspend fun signInFailed(cancelled: Boolean, offline: Boolean, cause: String): CloudNote? {
        if (cancelled) return null
        if (offline) return bad(IPHONE_OFFLINE)
        eventLog.add(clock(), EventCategory.ERROR, "Cloud backup sign-in failed. $cause")
        return bad(englishCloudText("settings_cloud_failed"))
    }

    /** Signs out on this iPhone; uploads stop. The cloud keeps what it has. */
    suspend fun signedOut() {
        files.forgetAccount()
        retry?.cancel()
        log("Cloud backup: signed out on the iPhone.")
    }

    // ---- The purchase --------------------------------------------------------------------------

    /**
     * StoreKit's sheet sold the backup: its signed transaction, [signed], goes to the purchase
     * check. [PurchaseAnswer.finish] says whether Swift may finish the transaction: only once the
     * server has given its answer for good, so that a purchase the check could not hear is
     * offered again at the next start (StoreKit's updates).
     */
    suspend fun purchased(signed: String): PurchaseAnswer {
        val result = afterUnlock(server.unlock(signed))
        return PurchaseAnswer(noteOf(result), finish = result.isFinal())
    }

    /**
     * A purchase StoreKit delivered by itself (approved later, or made on another device of the
     * Apple account): sent to the check as [purchased], if an account is signed in and does not
     * hold the unlock yet. Nothing is shown.
     *
     * @return whether Swift may finish the transaction.
     */
    suspend fun delivered(signed: String): Boolean {
        val account = files.account() ?: return false
        if (account.unlocked) return true
        return afterUnlock(server.unlock(signed)).isFinal()
    }

    /** StoreKit said the payment waits for approval (Ask to Buy, a payment method). */
    fun purchasePending(): CloudNote = bad(IPHONE_PENDING)

    /** StoreKit cannot sell the backup here: no product, a country it is not sold in. */
    fun notOffered(): CloudNote = bad(IPHONE_NOT_OFFERED)

    /** StoreKit's sheet failed for [cause], for the Log. */
    suspend fun purchaseFailed(cause: String): CloudNote {
        log("Cloud backup unlock failed. App Store: $cause")
        return bad(englishCloudText("settings_cloud_unlock_failed"))
    }

    /**
     * "Restore purchase": each purchase of the backup the Apple account holds ([signed], from
     * StoreKit's current entitlements after a sync) goes to the check until one unlocks.
     */
    suspend fun restorePurchases(signed: List<String>): CloudNote? =
        noteOf(afterUnlock(restoreFrom(signed, server)))

    // ---- The logbook of every phone, deletion (ADR-017) -----------------------------------------

    /** "Merge": this iPhone's trips join the logbook, and it backs up from now on. */
    suspend fun merge(): CloudNote {
        val account = files.account()?.takeIf { it.unlocked }
            ?: return bad(englishCloudText("settings_cloud_backup_offline"))
        keep(account.copy(backingUp = true))
        log("Cloud backup: this phone's trips were merged into the logbook of the account.")
        scope.launch { upload("Merge") }
        return good(englishCloudText("settings_cloud_merged"))
    }

    /**
     * "Use only the backup's trips": this iPhone's own trips are taken off it, through the
     * import (a safety copy first) of a logbook with none, and it holds the other phones' trips
     * from then on. Asked first on the tile.
     */
    suspend fun useBackupOnly(): CloudNote {
        val failed = bad(englishCloudText("settings_cloud_restore_failed"))
        val account = files.account()?.takeIf { it.unlocked } ?: return failed
        val empty = cloudRestoreExport(cloudCopyOf(emptyMap()), clock(), zone(), appVersion)
        val path = restoreFolder() + "/Logbook-without-this-phone.json"
        try {
            FileSystem.SYSTEM.write(path.toPath()) { writeUtf8(empty) }
        } catch (unwritable: okio.IOException) {
            log("Cloud backup: this phone's trips could not be set aside. ${unwritable.message}")
            return failed
        }
        try {
            val check = transfer.check(path)
            if (check.problem != null) return failed
            val said = "Set aside for the online backup's logbook"
            return when (transfer.replaceWith(path, check, said)) {
                is ImportOutcome.Done -> {
                    files.write(account.id, emptyMap())
                    keep(account.copy(backingUp = true))
                    log(
                        "Cloud backup: this phone's own trips were set aside; it holds the logbook's.",
                    )
                    scope.launch { upload("Use only the backup's trips") }
                    good(englishCloudText("settings_cloud_using_backup"))
                }

                ImportOutcome.TripRecording ->
                    bad(englishCloudText("settings_cloud_trip_in_progress"))

                ImportOutcome.NothingChecked, is ImportOutcome.Failed -> failed
            }
        } catch (unwritable: IOException) {
            log("Cloud backup: this phone's trips could not be set aside. ${unwritable.message}")
            return failed
        } finally {
            try {
                FileSystem.SYSTEM.delete(path.toPath())
            } catch (stays: okio.IOException) {
                // Left in the exports folder; the next one writes over it.
            }
        }
    }

    /**
     * "Delete my online backup and account", once Swift has revoked Apple's tokens for an
     * account made with Sign in with Apple (ADR-016): the server deletes the backup, the unlock
     * and the account, and releases the purchase; then this iPhone forgets the account.
     *
     * @return the note, and whether the account is gone (Swift then signs out of Firebase).
     */
    suspend fun deleteAccount(): DeleteAnswer {
        val failed = DeleteAnswer(bad(englishCloudText("settings_cloud_delete_failed")), false)
        files.account() ?: return failed
        val (code, _) = call(DELETE_FUNCTION, "{\"data\":{}}")
        if (code == null || code !in 200..299) {
            log("Cloud backup: the account could not be deleted. Nothing was changed.")
            return failed
        }
        files.forgetLedger()
        files.forgetAccount()
        retry?.cancel()
        log("Cloud backup: the online backup and the account were deleted, at the user's request.")
        return DeleteAnswer(good(englishCloudText("settings_cloud_deleted")), true)
    }

    // ---- The upload ----------------------------------------------------------------------------

    /**
     * One upload, as Android's job runs it (`CloudObjects.upload`): only for an account that
     * holds the unlock, signed in to Firebase as the account kept here, and backing up from
     * this iPhone. One at a time: a request while one runs is dropped, since the one running
     * goes on until nothing is left. Stopped by the network, it is tried again from a minute
     * later, doubling, while the app runs.
     *
     * @param reason what prompted it, in words, for nothing but the reader of this code.
     * @return true if the cloud holds what this iPhone has (or nothing was to be sent).
     */
    suspend fun upload(@Suppress("UNUSED_PARAMETER") reason: String): Boolean {
        if (!uploading.tryLock()) return true
        try {
            val account = files.account() ?: return true
            if (!account.unlocked) return true
            if (apple.signedInAccount() != account.id) {
                log("Cloud backup: not signed in to Firebase as the account kept here. Not sent.")
                return false
            }
            // Every phone backs up into its own folder (ADR-017), once it has joined the logbook.
            val phone = files.install()
            val others =
                try {
                    readOtherPhones(store, account.id, phone)
                } catch (unreachable: IOException) {
                    tryAgainLater()
                    return false
                }
            if (cloudAsksToMerge(ownFinished(), others, account.backingUp)) {
                if (account.backingUp != false) keep(account.copy(backingUp = false))
                return true
            }
            if (account.backingUp != true) keep(account.copy(backingUp = true))
            takeOthers(others)
            return when (uploadUntilQuiet(backup, account.id, ::log)) {
                CloudUploadOutcome.DONE -> {
                    retryWaitMs = RETRY_FIRST_MS
                    forgetEarlierOwner(account, phone)
                    true
                }

                CloudUploadOutcome.TRY_LATER -> {
                    tryAgainLater()
                    false
                }

                CloudUploadOutcome.REFUSED -> {
                    // Refused because the unlock is gone (a refund)? Then uploads stop here too,
                    // and the tile offers the unlock again.
                    if (holdsUnlock(account.id) == false) {
                        keep(account.copy(unlocked = false))
                        log(
                            "Cloud backup: this account no longer holds the unlock. Uploads " +
                                "stopped.",
                        )
                    }
                    false
                }
            }
        } catch (unwritable: IOException) {
            log("Cloud backup: a file of the backup could not be written. ${unwritable.message}")
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Swift cannot be handed an exception it was not told of: it would end the app.
            val what = "Cloud backup failed"
            eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
            return false
        } finally {
            uploading.unlock()
        }
    }

    private fun tryAgainLater() {
        if (retry?.isActive == true) return
        val wait = retryWaitMs
        retryWaitMs = (retryWaitMs * 2).coerceAtMost(RETRY_LONGEST_MS)
        retry = scope.launch {
            delay(wait)
            upload("tried again")
        }
    }

    // ---- What the backup holds -----------------------------------------------------------------

    /**
     * How the backup stands for the account signed in (ADR-017), as Android's tile asks it
     * (`CloudObjects.startBackup`): this iPhone backs up and holds the other phones' trips, or it
     * asks once whether to merge its own with theirs. It uploads when nothing is to be asked.
     */
    private suspend fun startBackup(): CloudStartResult {
        val account = files.account()?.takeIf { it.unlocked } ?: return CloudStartResult.Offline
        val others =
            try {
                readOtherPhones(store, account.id, files.install())
            } catch (unreachable: IOException) {
                return CloudStartResult.Offline
            }
        val own = ownFinished()
        if (cloudAsksToMerge(own, others, account.backingUp)) {
            if (account.backingUp != false) keep(account.copy(backingUp = false))
            return CloudStartResult.Merge(own, others.tripCount)
        }
        if (account.backingUp != true) keep(account.copy(backingUp = true))
        scope.launch { upload("the tile") }
        return CloudStartResult.BackingUp
    }

    /** How many finished trips of its own this iPhone holds. */
    private suspend fun ownFinished(): Int =
        trips.findOwn().count { it.status == TripStatus.FINISHED }

    /**
     * Brings the other phones' trips into this iPhone's trips table, the same drive recorded
     * here too left out (ADR-017).
     */
    private suspend fun takeOthers(others: OtherPhones) {
        val change = logbookChange(trips.findFromOtherPhones(), trips.findOwn(), others)
        if (change.isEmpty) return
        trips.replaceFromOtherPhones(change.insert, change.update, change.delete)
        log(
            "Cloud backup: from the other phones, ${change.insert.size} trips added, " +
                "${change.update.size} changed, ${change.delete.size} removed.",
        )
    }

    /**
     * The backup an older version kept at the account's top named this iPhone as its owner: the
     * run that just ended moved it into this iPhone's folder, so the owner document goes too.
     */
    private suspend fun forgetEarlierOwner(account: CloudAccount, phone: String) {
        try {
            val earlier = readCollections(account.id, listOf(CLOUD_BACKUP))
            if (earlier[CLOUD_OWNER]?.let(::cloudOwnerOf) == phone) {
                store.apply(account.id, emptyList(), listOf(CLOUD_OWNER))
                log("Cloud backup: this phone's backup moved into its own folder.")
            }
        } catch (unreachable: IOException) {
            // Tried again after the next upload.
        }
    }

    // ---- Asking Swift --------------------------------------------------------------------------

    private suspend fun readCollections(
        account: String,
        collections: List<String>,
    ): Map<String, String> {
        val answer: ReadAnswer =
            withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    apple.readCollections(
                        account,
                        collections,
                        CloudReadReply { documents, failure ->
                            if (continuation.isActive) {
                                continuation.resume(ReadAnswer(documents, failure))
                            }
                        },
                    )
                }
            } ?: throw CloudUnreachableException("No answer in ${ANSWER_TIMEOUT_MS / 1_000} s")
        answer.failure?.let { throw it }
        return answer.documents.orEmpty()
    }

    private suspend fun holdsUnlock(account: String): Boolean? =
        withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                apple.holdsUnlock(
                    account,
                    CloudHoldsReply { if (continuation.isActive) continuation.resume(it) },
                )
            }
        }

    private suspend fun call(name: String, body: String): Pair<Int?, String> =
        withTimeoutOrNull(ANSWER_TIMEOUT_MS * 2) {
            suspendCancellableCoroutine { continuation ->
                apple.call(
                    name,
                    body,
                    CloudCallReply { code, answer ->
                        if (continuation.isActive) continuation.resume(code to answer)
                    },
                )
            }
        } ?: (null to "")

    private suspend fun callUnlock(signed: String): CloudUnlockResult {
        val (code, body) = call(UNLOCK_FUNCTION, appleUnlockRequestBody(signed))
        return if (code == null) CloudUnlockResult.Offline else unlockAnswer(code, body)
    }

    /** Firestore through Swift, as the backup's run writes to it. */
    private inner class AppleCloudStore : CloudStore {
        override suspend fun readCollections(
            account: String,
            collections: List<String>,
        ): Map<String, String> = this@IosCloud.readCollections(account, collections)

        override suspend fun apply(
            account: String,
            documents: List<CloudDocument>,
            removals: List<String>,
        ) {
            val failure =
                withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        apple.apply(
                            account,
                            documents,
                            removals,
                            CloudReply {
                                if (continuation.isActive) continuation.resume(Answer(it))
                            },
                        )
                    }
                } ?: Answer(
                    CloudUnreachableException("No answer in ${ANSWER_TIMEOUT_MS / 1_000} s"),
                )
            failure.failure?.let { throw it }
        }
    }

    private class Answer(val failure: IOException?)

    private class ReadAnswer(val documents: Map<String, String>?, val failure: IOException?)

    // ---- Small things --------------------------------------------------------------------------

    private suspend fun afterUnlock(result: CloudUnlockResult): CloudUnlockResult {
        val account = files.account() ?: return CloudUnlockResult.Failed("Not signed in")
        when (result) {
            CloudUnlockResult.Unlocked -> {
                keep(account.copy(unlocked = true))
                log("Cloud backup unlocked.")
                scope.launch { upload("the unlock") }
            }

            is CloudUnlockResult.Failed -> log("Cloud backup unlock failed. ${result.cause}")

            else -> Unit
        }
        return result
    }

    private fun noteOf(result: CloudUnlockResult): CloudNote? = when (result) {
        CloudUnlockResult.Unlocked -> good(englishCloudText("settings_cloud_unlocked"))
        CloudUnlockResult.Cancelled -> null
        CloudUnlockResult.Pending -> bad(IPHONE_PENDING)
        CloudUnlockResult.NotOffered -> bad(IPHONE_NOT_OFFERED)
        CloudUnlockResult.OtherAccount -> bad(IPHONE_OTHER_ACCOUNT)
        CloudUnlockResult.Refunded -> bad(englishCloudText("settings_cloud_refunded"))
        CloudUnlockResult.NothingToRestore -> bad(IPHONE_NOTHING_TO_RESTORE)
        CloudUnlockResult.Offline, CloudUnlockResult.PlayNotReady -> bad(IPHONE_OFFLINE)
        is CloudUnlockResult.Failed -> bad(englishCloudText("settings_cloud_unlock_failed"))
    }

    /** Whether the server's answer is for good, so that StoreKit may forget the transaction. */
    private fun CloudUnlockResult.isFinal(): Boolean = when (this) {
        CloudUnlockResult.Unlocked,
        CloudUnlockResult.OtherAccount,
        CloudUnlockResult.Refunded,
        -> true

        else -> false
    }

    private fun deleteQuestion() = CloudQuestion(
        button = englishCloudText("settings_cloud_delete"),
        title = englishCloudText("settings_cloud_delete_title"),
        text = IPHONE_DELETE_TEXT,
        confirm = englishCloudText("settings_cloud_delete_confirm"),
    )

    private suspend fun keep(account: CloudAccount) {
        try {
            files.keep(account)
        } catch (unwritable: IOException) {
            log("Cloud backup: the account's file could not be written. ${unwritable.message}")
        }
    }

    private suspend fun log(line: String) {
        eventLog.add(clock(), EventCategory.CLOUD, line)
    }

    private fun restoreFolder(): String = (appFolderPath() + "/exports").also {
        FileSystem.SYSTEM.createDirectories(it.toPath())
    }
}

/** The email the tile names: Apple may have hidden it. */
private fun shown(account: CloudAccount): String = account.email.ifEmpty { "your Apple account" }

private fun good(text: String) = CloudNote(text, good = true)

private fun bad(text: String) = CloudNote(text, good = false)

// Where Android names Google Play or Google, the iPhone names the App Store or Apple.
private const val IPHONE_PENDING =
    "Payment pending. The backup unlocks when the App Store says it is paid; press Restore " +
        "purchase then."
private const val IPHONE_NOT_OFFERED =
    "The App Store cannot sell the backup on this iPhone right now."
private const val IPHONE_OTHER_ACCOUNT =
    "That purchase belongs to another Dually backup account. Sign in with the account you used " +
        "when you bought it."
private const val IPHONE_NOTHING_TO_RESTORE =
    "No purchase of the backup was found in this iPhone's Apple account."
private const val IPHONE_OFFLINE =
    "The online backup could not be reached. Check the iPhone is online and try again."
private const val IPHONE_DELETE_TEXT =
    "Your trips are deleted from Dually's server, and your Dually account with them. The trips " +
        "on this iPhone stay. Your purchase stays with your Apple account: sign in again later " +
        "and press Restore purchase."

/** The sign-in sheet's words: the iPhone offers Apple's sign-in and Google's (ADR-012). */
private object SignInWords {
    fun sheet() = CloudSignIn(
        title = "Sign in to your online backup",
        text =
            "Your backup is kept under the account you sign in with. Use the same one on every " +
                "phone: bought it on Android? Sign in with that Google account.",
        google = "Sign in with Google",
    )
}

/**
 * The Online backup tile, in English. A null button is not shown.
 *
 * @param text the tile's sentence: what the backup is, or which account it goes to.
 * @param on "On" while this iPhone backs up.
 * @param signIn the sheet shown before a purchase or a restore when nobody is signed in.
 * @param signedIn whether an account is signed in.
 */
data class CloudTile(
    val title: String,
    val text: String,
    val on: String? = null,
    val purchase: String? = null,
    val restorePurchase: String? = null,
    val merge: String? = null,
    val useBackup: CloudQuestion? = null,
    val signOut: String? = null,
    val deleteAccount: CloudQuestion? = null,
    val signIn: CloudSignIn? = null,
    val signedIn: Boolean = false,
    val cancel: String,
)

/** A button that asks first: its label, then the question's title, text and yes. */
data class CloudQuestion(
    val button: String,
    val title: String,
    val text: String,
    val confirm: String,
)

/** The sign-in sheet: Apple's own button goes above [google]. */
data class CloudSignIn(val title: String, val text: String, val google: String)

/** What a press came to, in a line: good news or a problem. */
data class CloudNote(val text: String, val good: Boolean)

/** What a purchase came to, and whether StoreKit may finish its transaction. */
data class PurchaseAnswer(val note: CloudNote?, val finish: Boolean)

/** What "Delete" came to, and whether the account is gone. */
data class DeleteAnswer(val note: CloudNote, val deleted: Boolean)
