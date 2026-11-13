package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.transfer.DENVER
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.MainRows
import com.shawnkowalchuk.milo.data.transfer.changedSettings
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.read
import com.shawnkowalchuk.milo.data.transfer.sentReports
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The restore of slice 1e (2026-11-12): the online backup, read back, is an export file the
 * import reads as one Shawn picked, and holds every closed trip, the sent reports and the
 * settings that were uploaded, and nothing else.
 */
class CloudRestoreTest {
    private val rows = MainRows(trips = everyKindOfTrip, sentReports = sentReports)
    private val uploaded = cloudSnapshotOf(rows, changedSettings).documents

    /** The backup as the server answers a read: each path and its JSON, with the owner. */
    private fun backup(): Map<String, String> =
        uploaded.associate { it.path to it.json } + (CLOUD_OWNER to """{"install":"a1"}""")

    private fun restored(documents: Map<String, String>): ExportReading = runBlocking {
        read(cloudRestoreExport(cloudCopyOf(documents), 1_000L, DENVER, "0.3.4"))
    }

    @Test
    fun `the backup comes back as the trips, sent reports and settings that were uploaded`() {
        val reading = restored(backup())

        val export = (reading as ExportReading.Good).export
        val closed = everyKindOfTrip.filter { it.status != TripStatus.OPEN }
        assertEquals(closed.sortedBy { it.id }, export.trips)
        assertEquals(sentReports.sortedBy { it.id }, export.sentReports.sortedBy { it.id })
        assertEquals(changedSettings, export.settings)
        // Without points: a restore has no routes.
        assertNull(export.pointCount)
    }

    @Test
    fun `the owner document is not a part of the copy, and the trips are counted`() {
        val copy = cloudCopyOf(backup())

        assertFalse(CLOUD_OWNER in copy.documents)
        assertEquals(uploaded.count { it.path.startsWith("trips/") }, copy.trips)
    }

    @Test
    fun `the ledger written after a restore is what the next upload would have sent`() {
        val ledger = cloudLedgerOf(cloudCopyOf(backup()))
        val plan = cloudPlanFor(cloudSnapshotOf(rows, changedSettings), ledger)

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a document that is not what Dually writes stops the restore before anything`() {
        val damaged = backup() + ("trips/1" to "{not json")
        try {
            restored(damaged)
            fail("expected a refusal")
        } catch (refused: CloudRestoreException) {
            assertTrue(refused.message.orEmpty().contains("trips/1"))
        }
    }

    @Test
    fun `a document of a newer format than this app reads stops it too`() {
        val newer = backup().mapValues { (path, json) ->
            if (path ==
                CLOUD_SETTINGS
            ) {
                json.replace("\"formatVersion\":3", "\"formatVersion\":99")
            } else {
                json
            }
        }
        try {
            restored(newer)
            fail("expected a refusal")
        } catch (refused: CloudRestoreException) {
            assertTrue(refused.message.orEmpty().contains("newer"))
        }
    }

    @Test
    fun `an empty backup restores to no trips at all`() {
        val export = (restored(emptyMap()) as ExportReading.Good).export

        assertEquals(emptyList<Any>(), export.trips)
        assertNull(export.settings)
    }
}
