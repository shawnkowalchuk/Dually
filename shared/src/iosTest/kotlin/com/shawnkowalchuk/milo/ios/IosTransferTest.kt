package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.io.IosFileReader
import com.shawnkowalchuk.milo.core.time.testZone
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.ExportWritten
import com.shawnkowalchuk.milo.data.transfer.MainReplacement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * The export file on the iPhone (phase 4g): written by the shared DataExport through an okio
 * sink, read back by the shared reader through IosFileReader, and imported over what changed.
 */
class IosTransferTest {
    private val world = RecorderWorld()
    private val export =
        DataExport(
            world.mainTransferDao(),
            world.pointsTransferDao(),
            world.settings,
            "0.1.0",
            { world.nowMs },
            { testZone("America/Edmonton") },
        )
    private val import = DataImport(world.mainTransferDao(), world.pointsTransferDao())

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `a trip exported and imported back over a deleted one comes back with its points`() =
        runTest {
            val recorder = world.recorder()
            val trip = recorder.start()!!
            world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
            recorder.end()
            val distance = world.trips.findTrip(trip.tripId)!!.distanceMetres

            val path = world.folderPath + "/export.json"
            val written =
                FileSystem.SYSTEM.sink(path.toPath()).buffer().use { sink ->
                    val text = StringBuilder()
                    val result = export.writeTo(text, includePoints = true)
                    sink.writeUtf8(text.toString())
                    result
                }
            assertIs<ExportWritten.Done>(written)
            assertEquals(1, written.contents.trips)
            assertTrue((written.contents.points ?: 0) > 0)

            val reading = import.check { IosFileReader(path) }
            assertIs<ExportReading.Good>(reading)
            assertEquals(1, reading.export.trips.size)

            world.mainTransferDao().replaceUnlessOpen(
                emptyList(),
                emptyList(),
                TripStatus.OPEN,
                said("emptied"),
            )
            assertEquals(0, import.storedNow().trips)

            val replaced =
                assertIs<MainReplacement.Done>(import.replaceMain(reading.export, said("imported")))
            val highest = replaced.highestTripId
            val points = import.replacePoints({ IosFileReader(path) }, reading.export, highest)
            assertEquals(written.contents.points, points)
            assertEquals(distance, world.trips.findTrip(trip.tripId)!!.distanceMetres)
        }

    @Test
    fun `a file that is not an export is refused and changes nothing`() = runTest {
        val path = world.folderPath + "/not-an-export.json"
        FileSystem.SYSTEM.write(path.toPath()) { writeUtf8("{\"hello\": \"Montréal\"}") }
        assertIs<ExportReading.Refused>(import.check { IosFileReader(path) })
    }

    private fun said(what: String) =
        EventLogEntry(atMs = world.nowMs, category = EventCategory.REPORT, message = what)
}
