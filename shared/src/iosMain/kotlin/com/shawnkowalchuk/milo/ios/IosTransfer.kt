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
import com.shawnkowalchuk.milo.data.transfer.ImportBegun
import com.shawnkowalchuk.milo.data.transfer.MainReplacement
import com.shawnkowalchuk.milo.data.transfer.exportFileName
import com.shawnkowalchuk.milo.data.transfer.importBegunOf
import kotlin.coroutines.cancellation.CancellationException
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/** How many starts set about finishing a cut-off import before it is left as it is. */
private const val MAX_TRIES = 2

/**
 * "Export all data" and "Import data" on the iPhone (phase 4g, third part): the export file both
 * phones write and read (DataExport, DataImport and the reader, shared since 2026-11-13), so an
 * export from Android imports on the iPhone and back.
 *
 * An import replaces every trip, sent report and raw point, and the settings the file holds, as on
 * Android. It writes a safety copy of what the phone holds first, as Android does. Since
 * 2026-11-16 an import the app is ended in the middle of is finished at the next start, as on
 * Android ([finishCutOff], `ImportBegun`). The truck's pairing is the phone's own and is kept: an
 * iPhone has no Bluetooth pairing.
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
    suspend fun importChecked(path: String, check: ImportCheck): String =
        when (val outcome = replaceWith(path, check, "Imported on the iPhone")) {
            is ImportOutcome.Done ->
                "Imported: ${outcome.trips} trips" +
                    if (outcome.settings) ", and the settings." else "."

            ImportOutcome.TripRecording ->
                "A trip is being recorded. End it first; nothing was replaced."

            ImportOutcome.NothingChecked -> "Nothing was imported."

            is ImportOutcome.Failed ->
                "The import failed: ${outcome.cause}. The safety copy is in Dually's files."
        }

    /**
     * The import itself, for [importChecked] and the online backup's restore (IosCloud): a
     * safety copy first, then the trips, the points and the settings of the file at [path].
     *
     * @param said how the line in the Log begins: "Imported on the iPhone".
     */
    internal suspend fun replaceWith(
        path: String,
        check: ImportCheck,
        said: String,
    ): ImportOutcome {
        val checked = check.checked ?: return ImportOutcome.NothingChecked
        try {
            // A copy of what is replaced, as on Android, before anything is.
            val safetyCopyAtMs = clock()
            if (write(
                    folder() + "/Before-import-" + exportFileName(safetyCopyAtMs, zone()),
                ) !is ExportWritten.Done
            ) {
                return ImportOutcome.TripRecording
            }
            val line =
                EventLogEntry(
                    atMs = clock(),
                    category = EventCategory.REPORT,
                    message =
                        "$said: ${checked.trips.size} trips replace the " +
                            "${check.storedTrips} that were here",
                )
            // Dually's own copy of the file and the note, as on Android, before the trips are
            // replaced: the trips and the points are in two databases, and an app ended
            // between the two is finished from them at its next start.
            FileSystem.SYSTEM.createDirectories(importFolder().toPath())
            FileSystem.SYSTEM.delete(keptCopy().toPath())
            FileSystem.SYSTEM.copy(path.toPath(), keptCopy().toPath())
            val begun =
                ImportBegun(
                    import.highestTripIdWith(checked),
                    safetyCopyAtMs,
                    line.atMs,
                    line.message,
                )
            writeNote(begun)
            val replaced = import.replaceMain(checked, line)
            if (replaced !is MainReplacement.Done) {
                clearCutOff()
                return ImportOutcome.TripRecording
            }
            import.replacePoints({ IosFileReader(keptCopy()) }, checked, replaced.highestTripId)
            checked.settings?.let { settings.replaceTransferred(it, TruckChange.Keep) }
            clearCutOff()
            return ImportOutcome.Done(checked.trips.size, checked.settings != null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "The import failed",
                failure.stackTraceToString(),
            )
            return ImportOutcome.Failed(failure.message.orEmpty())
        }
    }

    /**
     * Finishes an import the app was ended in the middle of, at its next start, as Android does
     * (`ImportResume`), and returns the Log's line, or null when none was cut off. The note says
     * whether the trips were replaced (the Log's line written with them); from Dually's copy of
     * the file the points and the settings are stored, or else the points of the trips that are
     * gone are removed. Two starts that could not finish it leave it as it is.
     */
    suspend fun finishCutOff(): String? {
        val notePath = note().toPath()
        if (!FileSystem.SYSTEM.exists(notePath)) {
            FileSystem.SYSTEM.delete(keptCopy().toPath())
            return null
        }
        val begun = importBegunOf(FileSystem.SYSTEM.read(notePath) { readUtf8() })
        if (begun == null) {
            clearCutOff()
            return null
        }
        val replaced = begun.tries > 0 || import.wasReplaced(begun)
        val line =
            when {
                !replaced -> {
                    clearCutOff()
                    say(
                        "Dually was ended just as an import began, before the trips were " +
                            "replaced. Everything on this iPhone is as it was; the file has to " +
                            "be imported again",
                    )
                }

                begun.tries >= MAX_TRIES -> {
                    clearCutOff()
                    say(
                        "Dually was ended in the middle of an import, and two later starts " +
                            "could not finish it. It is left as it is: the trips and the sent " +
                            "reports are the file's, and raw GPS points of the trips that are " +
                            "gone may still be stored under their trip ids. Importing the file " +
                            "again puts that right",
                        problem = true,
                    )
                }

                else -> finishFrom(begun)
            }
        return line
    }

    private suspend fun finishFrom(begun: ImportBegun): String {
        // Counted before anything is tried, so a start ended in the middle of this is counted.
        writeNote(begun.copy(tries = begun.tries + 1))
        val copy = keptCopy()
        val checked =
            if (begun.tries == 0 && FileSystem.SYSTEM.exists(copy.toPath())) {
                runCatching {
                    (import.check { IosFileReader(copy) } as? ExportReading.Good)?.export
                }.getOrNull()
            } else {
                null
            }
        val line =
            if (checked != null) {
                val points = import.replacePoints({
                    IosFileReader(copy)
                }, checked, begun.highestTripId)
                checked.settings?.let { settings.replaceTransferred(it, TruckChange.Keep) }
                say(
                    "Dually was ended in the middle of an import, and has finished it at this " +
                        "start from its copy of the file: the ${checked.trips.size} trips were " +
                        "stored before Dually was ended, and $points raw GPS points with them now" +
                        if (checked.settings != null) ". The file's settings are in force" else "",
                )
            } else {
                val removed = import.removePoints(begun.highestTripId)
                say(
                    "Dually was ended in the middle of an import, after the trips and the sent " +
                        "reports were replaced and before the file's raw GPS points were all " +
                        "stored. Its copy of the file could not be used to finish, so the " +
                        "$removed raw GPS points of the trips that are gone were removed: the " +
                        "imported trips have none. Importing the file again makes it whole",
                )
            }
        clearCutOff()
        return line
    }

    private suspend fun say(line: String, problem: Boolean = false): String {
        eventLog.add(clock(), if (problem) EventCategory.ERROR else EventCategory.REPORT, line)
        return line
    }

    private fun writeNote(begun: ImportBegun) {
        FileSystem.SYSTEM.write(note().toPath()) { writeUtf8(begun.asText()) }
    }

    private fun clearCutOff() {
        FileSystem.SYSTEM.delete(note().toPath())
        FileSystem.SYSTEM.delete(keptCopy().toPath())
    }

    private fun importFolder(): String = appFolderPath() + "/import"

    private fun keptCopy(): String = importFolder() + "/incoming.json"

    private fun note(): String = importFolder() + "/begun.txt"

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

/** What an import came to. */
internal sealed interface ImportOutcome {
    data class Done(val trips: Int, val settings: Boolean) : ImportOutcome

    /** A trip is being recorded: nothing was replaced. */
    data object TripRecording : ImportOutcome

    /** The file was never found good. */
    data object NothingChecked : ImportOutcome

    data class Failed(val cause: String) : ImportOutcome
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
