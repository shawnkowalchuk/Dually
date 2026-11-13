package com.shawnkowalchuk.milo.platform.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** The upload asked for by a change. One at a time: a second request while one waits is the same. */
private const val UPLOAD_NOW = "cloud-backup"

/** The daily upload, a safety net for a change no request followed. */
private const val UPLOAD_DAILY = "cloud-backup-daily"

/**
 * How long the trips, the sent reports and the travelling settings must stay unchanged before an
 * upload is asked for. A trip's end, its address found a moment later and a label set on the
 * trip screen then make one upload, not three.
 */
private const val QUIET_MS = 30_000L

/** The first wait before an upload that stopped is tried again; WorkManager doubles it each time. */
private const val RETRY_FIRST_S = 60L

/**
 * When the cloud backup uploads (ADR-012): after every change to what it keeps, and once a day,
 * each time with a network, through WorkManager. While no account is signed in, or the one
 * signed in does not hold the unlock (ADR-013), it asks for nothing and cancels what was asked.
 *
 * Asking only schedules work; it starts no Firebase. The job does that, and only with an account
 * signed in ([CloudBackupWorker]).
 *
 * @param account the account signed in, as Dually keeps it.
 * @param changes one item for every change to the trips, the sent reports or the travelling
 * settings, from wherever it was made: the phone's screens, Android Auto, an import.
 */
class CloudUploads(
    private val context: Context,
    private val account: StateFlow<CloudAccount?>,
    private val changes: Flow<Unit>,
) {
    private val network = Constraints.Builder().setRequiredNetworkType(
        NetworkType.CONNECTED,
    ).build()

    /** Follows [account] and [changes] for as long as [scope] lives. Called once, at the app's start. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            account.collectLatest { signedIn ->
                val work = WorkManager.getInstance(context)
                if (signedIn == null || !signedIn.unlocked) {
                    work.cancelUniqueWork(UPLOAD_NOW)
                    work.cancelUniqueWork(UPLOAD_DAILY)
                    return@collectLatest
                }
                work.enqueueUniquePeriodicWork(
                    UPLOAD_DAILY,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<CloudBackupWorker>(1, TimeUnit.DAYS)
                        .setConstraints(network)
                        .build(),
                )
                // At a sign-in, and at each start of the app: whatever changed while it was not
                // running is sent.
                request()
                changes.debounce(QUIET_MS).collect { request() }
            }
        }
    }

    private fun request() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UPLOAD_NOW,
            // A request while one waits, or runs, is the same request: the upload runs until it
            // finds nothing left to send (CLOUD_RUNS_PER_UPLOAD), and the daily one follows.
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudBackupWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_FIRST_S, TimeUnit.SECONDS)
                .build(),
        )
    }
}

/** The job WorkManager runs for an upload. The work is `CloudObjects.upload`; this only runs it. */
class CloudBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        (applicationContext as MiloApplication).container.cloud.upload()
}
