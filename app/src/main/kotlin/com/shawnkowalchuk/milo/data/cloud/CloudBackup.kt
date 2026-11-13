package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.transfer.MainRows
import java.io.IOException

/**
 * How many writes and removals go to the cloud together. Firestore takes at most 500 in one
 * batch; this leaves room.
 */
const val CLOUD_BATCH = 400

/** The cloud could not be reached, or refused a batch. Nothing of that batch was stored there. */
class CloudUnreachableException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * The cloud refused the batch, and sending it again will not change that: the account does not
 * hold the unlock (ADR-013), or is no longer signed in. Nothing of that batch was stored.
 */
class CloudRefusedException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** Where the backup's documents go. Firestore in the app; a stand-in in the tests. */
interface CloudStore {
    /**
     * Writes [documents] and removes [removals] in [account]'s folder, all or nothing.
     * Together they are at most [CLOUD_BATCH]. Removing a path that holds nothing is no error.
     *
     * @throws CloudUnreachableException if the batch was not stored, and may be later.
     * @throws CloudRefusedException if the cloud said no to it.
     */
    suspend fun apply(account: String, documents: List<CloudDocument>, removals: List<String>)
}

/** What one run of the backup did. */
sealed interface CloudRun {
    val uploaded: Int
    val removed: Int

    /** The cloud holds what the phone had when the run read it. */
    data class Done(override val uploaded: Int, override val removed: Int) : CloudRun

    /**
     * The run stopped part way: the cloud could not be reached, or the ledger could not be
     * written. What was stored before that is kept, and the next run carries on from there.
     */
    data class Stopped(
        override val uploaded: Int,
        override val removed: Int,
        val cause: IOException,
    ) : CloudRun
}

/**
 * One run of the backup: reads what the phone has, compares it with what was uploaded, and
 * sends the difference in batches (ADR-012).
 *
 * The ledger is written after each batch, so a run cut off anywhere has lost at most one batch
 * of progress, and the next run sends that batch again with the same content. A change made
 * while a run is under way is not in what it read; the run that follows the change sends it.
 *
 * @param rows the trips and the sent reports, read in one transaction.
 * @param settings the settings that travel, or null if they cannot be read.
 */
class CloudBackup(
    private val rows: suspend () -> MainRows,
    private val settings: suspend () -> TransferredSettings?,
    private val ledger: CloudLedger,
    private val store: CloudStore,
) {
    suspend fun run(account: String): CloudRun {
        val snapshot = cloudSnapshotOf(rows(), settings())
        val uploaded = ledger.read(account).toMutableMap()
        val plan = cloudPlanFor(snapshot, uploaded)
        var uploadedCount = 0
        var removedCount = 0
        // Removals first: a restore that replaced every trip leaves nothing behind in the cloud
        // even if the run stops before the last upload.
        val work = plan.removals.map { Step.Remove(it) } + plan.uploads.map { Step.Upload(it) }
        for (batch in work.chunked(CLOUD_BATCH)) {
            val documents = batch.filterIsInstance<Step.Upload>().map { it.document }
            val removals = batch.filterIsInstance<Step.Remove>().map { it.path }
            try {
                store.apply(account, documents, removals)
                removals.forEach(uploaded::remove)
                documents.forEach { uploaded[it.path] = it.fingerprint }
                ledger.write(account, uploaded)
            } catch (stopped: IOException) {
                return CloudRun.Stopped(uploadedCount, removedCount, stopped)
            }
            uploadedCount += documents.size
            removedCount += removals.size
        }
        return CloudRun.Done(uploadedCount, removedCount)
    }

    private sealed interface Step {
        data class Upload(val document: CloudDocument) : Step

        data class Remove(val path: String) : Step
    }
}
