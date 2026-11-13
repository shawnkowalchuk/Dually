package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.data.MILO_DATABASE_VERSION
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import com.shawnkowalchuk.milo.data.transfer.ExportSource
import com.shawnkowalchuk.milo.data.transfer.problem
import com.shawnkowalchuk.milo.data.transfer.toSentReport
import com.shawnkowalchuk.milo.data.transfer.toTransferred
import com.shawnkowalchuk.milo.data.transfer.toTrip
import com.shawnkowalchuk.milo.data.transfer.writeExport
import java.time.ZoneId
import kotlinx.serialization.SerializationException

// The restore of ADR-012 (slice 1e, 2026-11-12): the online backup, read back, becomes an export
// file of format 3 without GPS points, and the import that reads a picked file does the rest
// (`DataTransfer.restoreFromBackup`): the check, the safety copy, the replacing, the settings and
// the line in the Log. Nothing here is a second way of storing trips.

/** The document of the backup that says which phone backs up to it. Not a part of the copy. */
const val CLOUD_OWNER = "backup/owner"

/** The backup could not be turned into a file: a document is not what Dually writes. */
class CloudRestoreException(message: String) : Exception(message)

/** What a restore would bring back, said before anything is replaced. */
data class CloudCopy(val trips: Int, val documents: Map<String, String>)

/**
 * The backup's documents as they were read: each path under the account's folder and its JSON.
 * The owner document is left out: it is not a part of the copy.
 */
fun cloudCopyOf(documents: Map<String, String>): CloudCopy {
    val copy = documents.filterKeys { it != CLOUD_OWNER }
    return CloudCopy(trips = copy.keys.count { it.startsWith("$CLOUD_TRIPS/") }, documents = copy)
}

/**
 * The backup written as an export file, for the import. Every document is read and checked as
 * an export's own parts are; one that is not right stops the restore before anything is
 * replaced.
 *
 * @throws CloudRestoreException naming the first document that is not right.
 */
suspend fun cloudRestoreExport(
    copy: CloudCopy,
    restoredAtMs: Long,
    zone: ZoneId,
    appVersion: String,
): String {
    val trips =
        copy.documents
            .filterKeys { it.startsWith("$CLOUD_TRIPS/") }
            .map { (path, json) ->
                val document = decode<CloudTrip>(path, json)
                checkVersion(path, document.formatVersion)
                document.trip.problem(document.formatVersion)?.let { refuse(path, it) }
                document.trip.toTrip()
            }.sortedBy { it.id }
    val sentReports =
        copy.documents[CLOUD_SENT_REPORTS]?.let { json ->
            val document = decode<CloudSentReports>(CLOUD_SENT_REPORTS, json)
            checkVersion(CLOUD_SENT_REPORTS, document.formatVersion)
            document.sentReports.map { report ->
                report.problem(document.formatVersion)?.let { refuse(CLOUD_SENT_REPORTS, it) }
                report.toSentReport()
            }
        }.orEmpty()
    val settings =
        copy.documents[CLOUD_SETTINGS]?.let { json ->
            val document = decode<CloudSettings>(CLOUD_SETTINGS, json)
            checkVersion(CLOUD_SETTINGS, document.formatVersion)
            document.settings.problem()?.let { refuse(CLOUD_SETTINGS, it) }
            document.settings.toTransferred()
        }
    val out = StringBuilder()
    writeExport(
        out,
        ExportSource(
            exportedAtMs = restoredAtMs,
            zone = zone,
            appVersion = appVersion,
            databaseVersion = MILO_DATABASE_VERSION,
            settings = settings,
            sentReports = sentReports,
            trips = trips,
            pointCount = null,
            points = { },
        ),
    )
    return out.toString()
}

/**
 * What the account's folder holds, as the ledger keeps it: each document's fingerprint. Written
 * after a restore and before this phone takes the backup over, so that the next upload sends
 * only what differs, and removes from the backup what this phone does not have.
 */
fun cloudLedgerOf(copy: CloudCopy): Map<String, String> =
    copy.documents.mapValues { (path, json) -> CloudDocument(path, json).fingerprint }

private inline fun <reified T> decode(path: String, json: String): T = try {
    ExportJson.decodeFromString<T>(json)
} catch (unreadable: SerializationException) {
    refuse(path, "it cannot be read")
} catch (unreadable: IllegalArgumentException) {
    refuse(path, "it cannot be read")
}

private fun checkVersion(path: String, version: Int) {
    if (version > EXPORT_FORMAT_VERSION) refuse(path, "it is of a newer format, $version")
}

private fun refuse(path: String, why: String): Nothing =
    throw CloudRestoreException("The online backup's $path is not right: $why")
