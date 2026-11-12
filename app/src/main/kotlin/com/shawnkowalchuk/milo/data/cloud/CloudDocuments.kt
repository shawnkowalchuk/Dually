package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import com.shawnkowalchuk.milo.data.transfer.ExportedSentReport
import com.shawnkowalchuk.milo.data.transfer.ExportedSettings
import com.shawnkowalchuk.milo.data.transfer.ExportedTrip
import com.shawnkowalchuk.milo.data.transfer.MainRows
import com.shawnkowalchuk.milo.data.transfer.toExported
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

// What the cloud backup keeps (ADR-012): one document per closed trip, one for the reports
// recorded as sent, one for the settings that mean the same on another phone. Never a GPS
// point, never an open trip. Each document holds the same fields as the export file, with the
// version of the export format it was written in, so a restore reads it with the code that
// reads an export.
//
// A document is named by its path under the account's own folder in the cloud
// (`users/{account}/trips/17`). A trip's path is its id: ids are never used twice on a phone
// (the table numbers upwards from the highest id it ever held), and an import and a restore
// keep them, so one id is one trip for as long as the backup follows that phone.

/** The folder of the trip documents. A trip's document is this, a slash, and its id. */
const val CLOUD_TRIPS = "trips"

/** The path of the document that holds every report recorded as sent. */
const val CLOUD_SENT_REPORTS = "backup/sentReports"

/** The path of the document that holds the settings that travel. */
const val CLOUD_SETTINGS = "backup/settings"

/** The path of a trip's document. */
fun cloudTripPath(tripId: Long): String = "$CLOUD_TRIPS/$tripId"

/** A trip's document. */
@Serializable
data class CloudTrip(val formatVersion: Int, val trip: ExportedTrip)

/** The document of the reports recorded as sent, all of them, oldest first. */
@Serializable
data class CloudSentReports(val formatVersion: Int, val sentReports: List<ExportedSentReport>)

/** The document of the settings that travel. */
@Serializable
data class CloudSettings(val formatVersion: Int, val settings: ExportedSettings)

/**
 * One document as the cloud is to hold it.
 *
 * @param path where it goes, under the account's folder.
 * @param json its whole content. Written by the export format's own JSON, so the same rows give
 * the same text, key for key and digit for digit.
 */
class CloudDocument(val path: String, val json: String) {
    /** What the backup remembers of an uploaded document, to know later whether it changed. */
    val fingerprint: String = sha256(json)

    override fun equals(other: Any?): Boolean =
        other is CloudDocument && other.path == path && other.json == json

    override fun hashCode(): Int = 31 * path.hashCode() + json.hashCode()

    override fun toString(): String = "CloudDocument($path, $fingerprint)"
}

/**
 * Everything the cloud is to hold, as the phone has it now.
 *
 * @param unknown the paths whose content could not be read this time (the settings, when their
 * file cannot be read). What the cloud holds there is left alone: neither written nor removed.
 */
data class CloudSnapshot(val documents: List<CloudDocument>, val unknown: Set<String> = emptySet())

/**
 * The documents for [rows], read in one transaction, and [settings], or null if the settings
 * could not be read. An open trip has no document: it has no end and no distance yet, and is
 * uploaded once it closes.
 */
fun cloudSnapshotOf(rows: MainRows, settings: TransferredSettings?): CloudSnapshot {
    val trips =
        rows.trips
            .filter { it.status != TripStatus.OPEN }
            .sortedBy { it.id }
            .map { trip ->
                CloudDocument(
                    path = cloudTripPath(trip.id),
                    json = ExportJson.encodeToString(
                        CloudTrip(EXPORT_FORMAT_VERSION, trip.toExported()),
                    ),
                )
            }
    val sentReports =
        CloudDocument(
            path = CLOUD_SENT_REPORTS,
            json =
                ExportJson.encodeToString(
                    CloudSentReports(
                        EXPORT_FORMAT_VERSION,
                        rows.sentReports.sortedBy { it.id }.map { it.toExported() },
                    ),
                ),
        )
    val settingsDocument =
        settings?.let {
            CloudDocument(
                path = CLOUD_SETTINGS,
                json = ExportJson.encodeToString(
                    CloudSettings(EXPORT_FORMAT_VERSION, it.toExported()),
                ),
            )
        }
    return CloudSnapshot(
        documents = trips + sentReports + listOfNotNull(settingsDocument),
        unknown = if (settings == null) setOf(CLOUD_SETTINGS) else emptySet(),
    )
}

private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
