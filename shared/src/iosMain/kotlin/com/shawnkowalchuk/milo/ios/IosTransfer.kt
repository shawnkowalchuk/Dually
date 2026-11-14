package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.io.IosFileReader
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.data.appFolderPath
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TruckChange
import com.shawnkowalchuk.milo.data.settings.replaceTransferred
import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.DataExport
import com.shawnkowalchuk.milo.data.transfer.DataImport
import com.shawnkowalchuk.milo.data.transfer.ExportReading
import com.shawnkowalchuk.milo.data.transfer.ExportWritten
import com.shawnkowalchuk.milo.data.transfer.MainReplacement
import com.shawnkowalchuk.milo.data.transfer.exportFileName
import kotlin.coroutines.cancellation.CancellationException
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * "Export all data" and "Import data" on the iPhone (phase 4g, third part): the export file both
 * phones write and read (DataExport, DataImport and the reader, shared since 2026-11-13), so an
 * export from Android imports on the iPhone and back.
 *
 * An import replaces every trip, sent report and raw point, and the settings the file holds, as on
 * Android. It writes a safety copy of what the phone holds first, as Android does. Not on the
 * iPhone: Android's finishing of an import the phone was switched off during (ImportBegun);
 * `[DEBT]` in the findings log. The truck's pairing is the phone's own and is kept: an iPhone has
 * no Bluetooth pairing.
 */
class IosTransfer internal constructor(
    private val export: DataExport,
    private val import: DataImport,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val zone: () -> ZoneId,
    private val clock: () -> Long,
) {
    /**
     * Writes everything, raw GPS points included, to a file in the app's folder for the share
     * sheet. Returns its path, or null while a trip is being recorded (its end is not known).
     */
    suspend fun exportAll(): ExportedFile? {
        val path = folder() + "/" + exportFileName(clock(), zone())
        val written = write(path)
        if (written !is ExportWritten.Done) {
            FileSystem.SYSTEM.delete(path.toPath())
            return null
        }
        eventLog.add(
            clock(),
            EventCategory.REPORT,
            "Exported from the iPhone: ${written.contents.trips} trips, " +
                "${written.contents.points ?: 0} GPS points",
        )
        return ExportedFile(path, written.contents.trips)
    }

    /** Reads and checks the file at [path], and changes nothing. */
    suspend fun check(path: String): ImportCheck =
        when (val reading = import.check { IosFileReader(path) }) {
            is ExportReading.Good ->
                ImportCheck(
                    tripCount = reading.export.trips.size,
                    hasSettings = reading.export.settings != null,
                    storedTrips = import.storedNow().trips,
                    problem = null,
                    checked = reading.export,
                )

            is ExportReading.Refused ->
                ImportCheck(0, false, 0, "This file cannot be imported: ${reading.problem}.", null)
        }

    /**
     * Replaces what the phone holds with the file at [path], checked by [check] as [check].
     * Returns what to say: done, or why nothing was replaced.
     */
    suspend fun importChecked(path: String, check: ImportCheck): String {
        val checked = check.checked ?: return "Nothing was imported."
        try {
            // A copy of what is replaced, as on Android, before anything is.
            if (write(
                    folder() + "/Before-import-" + exportFileName(clock(), zone()),
                ) !is ExportWritten.Done
            ) {
                return "A trip is being recorded. End it first; nothing was replaced."
            }
            val said =
                EventLogEntry(
                    atMs = clock(),
                    category = EventCategory.REPORT,
                    message =
                        "Imported on the iPhone: ${checked.trips.size} trips replace the " +
                            "${check.storedTrips} that were here",
                )
            val replaced = import.replaceMain(checked, said)
            if (replaced !is MainReplacement.Done) {
                return "A trip is being recorded. End it first; nothing was replaced."
            }
            import.replacePoints({ IosFileReader(path) }, checked, replaced.highestTripId)
            checked.settings?.let { settings.replaceTransferred(it, TruckChange.Keep) }
            return "Imported: ${checked.trips.size} trips" +
                if (checked.settings != null) ", and the settings." else "."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "The import failed",
                failure.stackTraceToString(),
            )
            return "The import failed: ${failure.message}. The safety copy is in Dually's files."
        }
    }

    private suspend fun write(path: String): ExportWritten =
        FileSystem.SYSTEM.sink(path.toPath()).buffer().use { sink ->
            export.writeTo(SinkAppendable(sink), includePoints = true)
        }

    private fun folder(): String = (appFolderPath() + "/exports").also {
        FileSystem.SYSTEM.createDirectories(it.toPath())
    }
}

/** An okio sink as the Appendable the shared export writes to, in UTF-8. */
private class SinkAppendable(private val sink: BufferedSink) : Appendable {
    override fun append(value: Char): Appendable = apply { sink.writeUtf8CodePoint(value.code) }

    override fun append(value: CharSequence?): Appendable = apply {
        sink.writeUtf8(value.toString())
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable = apply {
        sink.writeUtf8(value.toString(), startIndex, endIndex)
    }
}

/** An export written to [path], holding [tripCount] trips. */
data class ExportedFile(val path: String, val tripCount: Int)

/** What a file holds, and what an import of it would replace, before anything is. */
data class ImportCheck(
    val tripCount: Int,
    val hasSettings: Boolean,
    val storedTrips: Int,
    /** Why the file cannot be imported, or null. */
    val problem: String?,
    internal val checked: CheckedExport?,
)
