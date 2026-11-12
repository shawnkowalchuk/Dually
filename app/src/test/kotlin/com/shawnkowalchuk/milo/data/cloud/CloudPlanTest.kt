package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.data.transfer.MainRows
import com.shawnkowalchuk.milo.data.transfer.changedSettings
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The comparison of what the phone has with what was uploaded. */
class CloudPlanTest {
    private fun document(path: String, json: String) = CloudDocument(path, json)

    @Test
    fun `new and changed documents are uploaded, unchanged ones are not`() {
        val same = document("trips/1", "{}")
        val changed = document("trips/2", "{\"label\":\"Work\"}")
        val new = document("trips/3", "{}")
        val uploaded = mapOf("trips/1" to same.fingerprint, "trips/2" to "older")

        val plan = cloudPlanFor(CloudSnapshot(listOf(same, changed, new)), uploaded)

        assertEquals(listOf(changed, new), plan.uploads)
        assertTrue(plan.removals.isEmpty())
    }

    @Test
    fun `a path that was uploaded and is gone is removed, unless it is unknown this time`() {
        val uploaded = mapOf("trips/7" to "x", "trips/1" to "y", CLOUD_SETTINGS to "z")

        val plan =
            cloudPlanFor(CloudSnapshot(emptyList(), unknown = setOf(CLOUD_SETTINGS)), uploaded)

        assertEquals(listOf("trips/1", "trips/7"), plan.removals)
    }

    @Test
    fun `nothing changed is an empty plan`() {
        val snapshot = cloudSnapshotOf(MainRows(everyKindOfTrip, sentReports), changedSettings)
        val uploaded = snapshot.documents.associate { it.path to it.fingerprint }

        assertTrue(cloudPlanFor(snapshot, uploaded).isEmpty)
    }

    @Test
    fun `the same rows always give the same fingerprints, and a change gives another`() {
        fun fingerprintOf(rows: MainRows) =
            cloudSnapshotOf(rows, changedSettings).documents.first().fingerprint
        val rows = MainRows(listOf(recordedTrip), sentReports)

        assertEquals(
            fingerprintOf(rows),
            fingerprintOf(MainRows(listOf(recordedTrip), sentReports)),
        )
        assertNotEquals(
            fingerprintOf(rows),
            fingerprintOf(
                MainRows(listOf(recordedTrip.copy(distanceMetres = 18_412.76)), sentReports),
            ),
        )
    }
}
