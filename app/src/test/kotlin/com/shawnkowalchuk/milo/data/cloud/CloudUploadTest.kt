package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.data.transfer.MainRows
import com.shawnkowalchuk.milo.data.transfer.changedSettings
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** One upload: runs until nothing is left, and what it tells the job and the log. */
class CloudUploadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** A cloud that stores, or throws [failure] when it is set. */
    private class Cloud : CloudStore {
        val stored = mutableMapOf<String, String>()
        var failure: java.io.IOException? = null

        override suspend fun apply(
            account: String,
            documents: List<CloudDocument>,
            removals: List<String>,
        ) {
            failure?.let { throw it }
            removals.forEach(stored::remove)
            documents.forEach { stored[it.path] = it.json }
        }
    }

    private val cloud = Cloud()
    private val lines = mutableListOf<String>()
    private var trips: List<Trip> = everyKindOfTrip

    /** Called at each read of the rows, after it: a change made while a run is under way. */
    private var afterRead: () -> Unit = {}

    private val backup by lazy {
        CloudBackup(
            rows = { MainRows(trips, sentReports).also { afterRead() } },
            settings = { changedSettings },
            ledger = CloudLedger(File(temporaryFolder.root, "cloud")),
            store = cloud,
        )
    }

    private suspend fun upload() = uploadUntilQuiet(backup, "account-1", { lines += it })

    @Test
    fun `the first upload sends everything, then finds nothing left and says so once`() = runTest {
        assertEquals(CloudUploadOutcome.DONE, upload())

        assertEquals(everyKindOfTrip.size + 2, cloud.stored.size)
        assertEquals(listOf("Cloud backup: ${everyKindOfTrip.size + 2} sent, 0 removed."), lines)
    }

    @Test
    fun `an upload with nothing to send writes no line`() = runTest {
        upload()
        lines.clear()

        assertEquals(CloudUploadOutcome.DONE, upload())
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `a trip that ends while a run is under way is sent by the same upload`() = runTest {
        upload()
        lines.clear()
        val ended = recordedTrip.copy(id = 40)
        trips = everyKindOfTrip + recordedTrip.copy(id = 39)
        afterRead = {
            trips = trips + ended
            afterRead = {}
        }

        assertEquals(CloudUploadOutcome.DONE, upload())

        assertTrue(cloudTripPath(39) in cloud.stored)
        assertTrue(cloudTripPath(40) in cloud.stored)
        assertEquals(2, lines.size)
    }

    @Test
    fun `no network is tried again later, and the log says why`() = runTest {
        cloud.failure = CloudUnreachableException("Firestore UNAVAILABLE")

        assertEquals(CloudUploadOutcome.TRY_LATER, upload())
        assertEquals(
            "Cloud backup stopped after 0 sent and 0 removed: CloudUnreachableException, " +
                "Firestore UNAVAILABLE. Tried again later.",
            lines.single(),
        )
    }

    @Test
    fun `a refusal is not tried again`() = runTest {
        cloud.failure = CloudRefusedException("Firestore PERMISSION_DENIED")

        assertEquals(CloudUploadOutcome.REFUSED, upload())
        assertTrue(lines.single().endsWith("Not tried again."))
    }

    @Test
    fun `an upload makes five runs at most, however much keeps changing`() = runTest {
        var next = 100L
        afterRead = { trips = trips + recordedTrip.copy(id = next++) }

        assertEquals(CloudUploadOutcome.DONE, upload())

        assertEquals(CLOUD_RUNS_PER_UPLOAD, lines.size)
    }
}
