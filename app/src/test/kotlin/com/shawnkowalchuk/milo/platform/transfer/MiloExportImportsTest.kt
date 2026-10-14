package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * An export file written by MilO, before the app was renamed (ADR-010), is imported by Dually.
 * It is the one way a person's trips get from the old app to the new one: Dually has another
 * application ID, so Android installs it beside MilO as a new app, with nothing in it.
 *
 * The file is a real one: `src/test/resources/milo-export-0.3.1.json`, written on an emulator
 * on 2026-10-13 by the last build that called itself MilO (version 0.3.1, export format 3),
 * from three trips, one sent report, 108 GPS points and settings typed for the purpose. It says
 * "milo-export" of itself, and "MilO mileage log" to whoever opens it.
 */
class MiloExportImportsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // Gradle runs the unit tests from the module's folder, app/.
    private val fromMilo = File("src/test/resources/milo-export-0.3.1.json").readText()

    private val picked = "content://files/MilO-export-2026-10-13.json"

    private val world by lazy {
        TransferWorld(temporaryFolder.root).apply {
            // A phone Dually was just installed on: nothing stored, no truck paired.
            pairing = PairingHere(companionSupported = true, associatedAddresses = emptyList())
        }
    }

    @Test
    fun `the file is what MilO wrote, under its old name`() {
        assertTrue(fromMilo.contains(""""format": "$EXPORT_FORMAT""""))
        assertTrue(fromMilo.contains(""""appVersion": "0.3.1""""))
        assertTrue(fromMilo.contains("MilO mileage log"))
    }

    @Test
    fun `every trip, every point and the sent report come across`() = runTest {
        world.documents.files[picked] = fromMilo.toByteArray()
        val transfer = world.transfer(backgroundScope)

        transfer.offerImport(picked)
        val offered = transfer.settled()
        assertNotNull("The file was not taken: ${offered.outcome}", offered.offer)
        transfer.confirmImport()
        val outcome = transfer.settled().outcome

        assertTrue("$outcome", outcome is TransferOutcome.Imported)
        val done = (outcome as TransferOutcome.Imported).done
        assertEquals(3, done.trips)
        assertEquals(1, done.sentReports)
        assertEquals(108L, done.points)
        val trips = world.main.trips.sortedBy { it.startedAtMs }
        assertEquals(3, trips.size)
        assertTrue(trips.all { it.status == TripStatus.FINISHED })
        // The trip typed in by hand, with its two addresses and its 31.4 km, and the two that
        // were recorded, one of them with its label.
        val byHand = trips.single { it.addedByHand }
        assertEquals("118 Foundry Road", byHand.startAddress)
        assertEquals("40 Quarry Lane", byHand.endAddress)
        assertEquals(31_400.0, byHand.distanceMetres, 0.0)
        assertEquals(listOf("Supplier run"), trips.mapNotNull { it.label })
        assertEquals(2, trips.count { !it.addedByHand && it.distanceMetres > 0 })
        // Every GPS point, each still with its trip.
        assertEquals(108, world.points.points.size)
        assertTrue(world.points.points.all { point -> trips.any { it.id == point.tripId } })
        // The report recorded as sent: September 2026, one trip, 31.4 km.
        val report = world.main.sentReports.single()
        assertEquals(SentReportKind.MONTH, report.kind)
        assertEquals(1, report.tripCount)
        assertEquals(31_400.0, report.distanceMetres, 0.0)
    }

    @Test
    fun `the settings an export carries come across, and the others are left as they are`() =
        runTest {
            world.documents.files[picked] = fromMilo.toByteArray()
            val transfer = world.transfer(backgroundScope)
            transfer.offerImport(picked)
            transfer.settled()
            transfer.confirmImport()
            transfer.settled()

            val settings = runBlocking { world.settings.current() }
            assertEquals("Sam Carter", settings.reportName)
            assertEquals("Carter Plumbing Ltd", settings.reportCompany)
            assertEquals("Ram 3500", settings.reportVehicle)
            assertEquals("accounts@example.com", settings.accountantEmail)
            assertEquals(150, settings.gracePeriodSeconds)
            assertEquals(2, settings.reminderDay)
            assertTrue(settings.schedule.on(DayOfWeek.SATURDAY).tracked)
            // What an export file does not hold stays as the phone that imports has it: here, as a
            // new install has it. The odometer's readings are the ones that cannot be typed again
            // with their dates (ADR-010 lists every such setting).
            assertTrue(settings.odometerReadings.isEmpty())
            assertNull(settings.truckAddress)
        }
}
