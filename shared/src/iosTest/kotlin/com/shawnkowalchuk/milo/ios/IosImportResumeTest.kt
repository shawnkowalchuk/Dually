package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.io.IosFileReader
import com.shawnkowalchuk.milo.core.time.testZone
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.appFolderPath
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.ExportWritten
import com.shawnkowalchuk.milo.data.transfer.ImportBegun
import com.shawnkowalchuk.milo.data.transfer.MainReplacement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * An import the iPhone app is ended in the middle of (2026-11-16), finished at its next start as
 * Android finishes one (`ImportResume`): from Dually's copy of the file when the trips were
 * replaced, and with nothing changed when they were not.
 */
class IosImportResumeTest {
    private val world = RecorderWorld()
    private val zone = testZone("America/Edmonton")
    private val export =
        DataExport(
            world.mainTransferDao(),
            world.pointsTransferDao(),
            world.settings,
            "1.0.1",
            { world.nowMs },
            { zone },
        )
    private val import = DataImport(world.mainTransferDao(), world.pointsTransferDao())
    private val transfer =
        IosTransfer(export, import, world.settings, world.log, { zone }, { world.nowMs })
    private val folder = appFolderPath() + "/import"

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(folder.toPath())
        world.close()
    }

    /** A recorded trip with its points, written to Dually's kept copy, as an import leaves it. */
    private suspend fun keptExport(): Pair<ExportReading.Good, Long> {
        val recorder = world.recorder()
        recorder.start()
        world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
        recorder.end()
        FileSystem.SYSTEM.createDirectories(folder.toPath())
        val text = StringBuilder()
        val written = export.writeTo(text, includePoints = true)
        FileSystem.SYSTEM.write("$folder/incoming.json".toPath()) { writeUtf8(text.toString()) }
        val reading =
            assertIs<ExportReading.Good>(import.check { IosFileReader("$folder/incoming.json") })
        return reading to (assertIs<ExportWritten.Done>(written).contents.points ?: 0L)
    }

    private fun note(begun: ImportBegun) =
        FileSystem.SYSTEM.write("$folder/begun.txt".toPath()) { writeUtf8(begun.asText()) }

    @Test
    fun `trips replaced before the app was ended get their points at the next start`() = runTest {
        val (reading, points) = keptExport()
        val line =
            EventLogEntry(
                atMs = world.nowMs,
                category = EventCategory.REPORT,
                message = "Imported on the iPhone: test",
            )
        val highest = import.highestTripIdWith(reading.export)
        note(ImportBegun(highest, world.nowMs, line.atMs, line.message))
        assertIs<MainReplacement.Done>(import.replaceMain(reading.export, line))
        import.removePoints(Long.MAX_VALUE)

        val said = assertNotNull(transfer.finishCutOff())

        assertTrue(said.contains("has finished it at this start"), said)
        assertTrue(said.contains("$points raw GPS points"), said)
        assertFalse(FileSystem.SYSTEM.exists("$folder/begun.txt".toPath()))
        assertNull(transfer.finishCutOff(), "finished once")
    }

    @Test
    fun `an import ended before the trips were replaced leaves everything as it was`() = runTest {
        val (reading, _) = keptExport()
        val stored = import.storedNow().trips
        val highest = import.highestTripIdWith(reading.export)
        note(ImportBegun(highest, world.nowMs, world.nowMs, "never written"))

        val said = assertNotNull(transfer.finishCutOff())

        assertTrue(said.contains("before the trips were replaced"), said)
        assertEquals(stored, import.storedNow().trips)
        assertFalse(FileSystem.SYSTEM.exists("$folder/incoming.json".toPath()))
    }
}
