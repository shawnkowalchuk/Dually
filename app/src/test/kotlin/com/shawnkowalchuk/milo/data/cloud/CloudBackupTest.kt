package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import com.shawnkowalchuk.milo.data.transfer.MainRows
import com.shawnkowalchuk.milo.data.transfer.changedSettings
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import com.shawnkowalchuk.milo.data.transfer.toExported
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ACCOUNT = "account-1"

/** The cloud as a map of paths to documents, with a switch that makes it unreachable. */
private class FakeCloud : CloudStore {
    val folders = mutableMapOf<String, MutableMap<String, String>>()
    val batches = mutableListOf<Int>()

    /** How many more batches go through before every one after is refused. */
    var batchesUntilUnreachable = Int.MAX_VALUE

    override suspend fun apply(
        account: String,
        documents: List<CloudDocument>,
        removals: List<String>,
    ) {
        if (batchesUntilUnreachable-- <= 0) throw CloudUnreachableException("No network")
        assertTrue(documents.size + removals.size <= CLOUD_BATCH)
        batches += documents.size + removals.size
        val folder = folders.getOrPut(account) { mutableMapOf() }
        removals.forEach(folder::remove)
        documents.forEach { folder[it.path] = it.json }
    }

    fun of(account: String = ACCOUNT): Map<String, String> = folders[account].orEmpty()
}

class CloudBackupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val cloud = FakeCloud()
    private var trips: List<Trip> = everyKindOfTrip
    private var reports: List<SentReport> = sentReports
    private var settings: TransferredSettings? = changedSettings
    private val ledger by lazy { CloudLedger(File(temporaryFolder.root, "cloud")) }
    private val backup by lazy {
        CloudBackup(
            rows = { MainRows(trips, reports) },
            settings = { settings },
            ledger = ledger,
            store = cloud,
        )
    }

    private val tripPaths: Set<String> get() = trips.map { cloudTripPath(it.id) }.toSet()

    @Test
    fun `the first run uploads every closed trip, the sent reports and the settings`() = runTest {
        val run = backup.run(ACCOUNT)

        assertEquals(CloudRun.Done(uploaded = everyKindOfTrip.size + 2, removed = 0), run)
        assertEquals(tripPaths + CLOUD_SENT_REPORTS + CLOUD_SETTINGS, cloud.of().keys)
    }

    @Test
    fun `a trip's document holds what the export file holds of it, and no GPS point`() = runTest {
        backup.run(ACCOUNT)

        val stored =
            ExportJson.decodeFromString<CloudTrip>(
                cloud.of().getValue(cloudTripPath(recordedTrip.id)),
            )
        assertEquals(CloudTrip(EXPORT_FORMAT_VERSION, recordedTrip.toExported()), stored)
        assertFalse(cloud.of().keys.any { "point" in it.lowercase() })
    }

    @Test
    fun `a second run with nothing changed sends nothing`() = runTest {
        backup.run(ACCOUNT)
        cloud.batches.clear()

        assertEquals(CloudRun.Done(0, 0), backup.run(ACCOUNT))
        assertTrue(cloud.batches.isEmpty())
    }

    @Test
    fun `an open trip is not uploaded until it closes`() = runTest {
        val open = recordedTrip.copy(id = 20, status = TripStatus.OPEN, endedAtMs = null)
        trips = everyKindOfTrip + open
        backup.run(ACCOUNT)
        assertFalse(cloudTripPath(20) in cloud.of())

        trips = everyKindOfTrip + open.copy(status = TripStatus.FINISHED, endedAtMs = 1L)
        assertEquals(CloudRun.Done(1, 0), backup.run(ACCOUNT))
        assertTrue(cloudTripPath(20) in cloud.of())
    }

    @Test
    fun `a label, an address found later and a delete each send that trip again`() = runTest {
        backup.run(ACCOUNT)
        val before = cloud.of().getValue(cloudTripPath(recordedTrip.id))

        for (changed in listOf(
            recordedTrip.copy(label = "Supplier"),
            recordedTrip.copy(endAddress = "1 New St"),
            recordedTrip.copy(status = TripStatus.DELETED),
        )) {
            trips = everyKindOfTrip.map { if (it.id == changed.id) changed else it }

            assertEquals(CloudRun.Done(1, 0), backup.run(ACCOUNT))
            assertNotEquals(before, cloud.of().getValue(cloudTripPath(recordedTrip.id)))
        }
    }

    @Test
    fun `a new sent report sends the one document of the reports again`() = runTest {
        backup.run(ACCOUNT)
        reports = sentReports + sentReports.last().copy(id = 9, revision = 1)

        assertEquals(CloudRun.Done(1, 0), backup.run(ACCOUNT))
    }

    @Test
    fun `trips an import replaced are removed from the cloud`() = runTest {
        backup.run(ACCOUNT)
        trips = listOf(recordedTrip.copy(id = 30))

        val run = backup.run(ACCOUNT)

        assertEquals(CloudRun.Done(uploaded = 1, removed = everyKindOfTrip.size), run)
        assertEquals(setOf(cloudTripPath(30), CLOUD_SENT_REPORTS, CLOUD_SETTINGS), cloud.of().keys)
    }

    @Test
    fun `settings that cannot be read are neither sent nor removed`() = runTest {
        backup.run(ACCOUNT)
        val stored = cloud.of().getValue(CLOUD_SETTINGS)
        settings = null

        assertEquals(CloudRun.Done(0, 0), backup.run(ACCOUNT))
        assertEquals(stored, cloud.of().getValue(CLOUD_SETTINGS))
    }

    @Test
    fun `many trips go in batches the cloud can take`() = runTest {
        trips = (1L..1_000L).map { recordedTrip.copy(id = it) }

        backup.run(ACCOUNT)

        assertEquals(listOf(CLOUD_BATCH, CLOUD_BATCH, 1_000 + 2 - 2 * CLOUD_BATCH), cloud.batches)
    }

    @Test
    fun `a run cut off by the network keeps what it stored, and the next carries on`() = runTest {
        trips = (1L..1_000L).map { recordedTrip.copy(id = it) }
        cloud.batchesUntilUnreachable = 1

        val stopped = backup.run(ACCOUNT)

        assertTrue(stopped is CloudRun.Stopped)
        assertEquals(CLOUD_BATCH, stopped.uploaded)
        cloud.batchesUntilUnreachable = Int.MAX_VALUE
        assertEquals(CloudRun.Done(1_000 + 2 - CLOUD_BATCH, 0), backup.run(ACCOUNT))
        assertEquals(1_002, cloud.of().size)
    }

    @Test
    fun `another account gets everything, and the first keeps what it has`() = runTest {
        backup.run(ACCOUNT)

        assertEquals(CloudRun.Done(everyKindOfTrip.size + 2, 0), backup.run("account-2"))
        assertEquals(cloud.of(), cloud.of("account-2"))
    }

    @Test
    fun `a ledger that cannot be written stops the run, and nothing is lost`() = runTest {
        File(temporaryFolder.root, "cloud").writeText("a file where the folder belongs")

        val run = backup.run(ACCOUNT)

        assertTrue(run is CloudRun.Stopped)
        // The cloud took the batch; only the ledger failed. The next run sends it again.
        assertFalse((run as CloudRun.Stopped).cause is CloudUnreachableException)
        assertEquals(everyKindOfTrip.size + 2, cloud.of().size)
    }
}
