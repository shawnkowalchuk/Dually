package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.io.IOException
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.service.ServiceBook
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.KnownVehicles
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.accountVehicles
import com.shawnkowalchuk.milo.data.settings.addOdometerReading
import com.shawnkowalchuk.milo.data.settings.changeServiceBook
import com.shawnkowalchuk.milo.data.settings.knownVehiclesJson
import com.shawnkowalchuk.milo.data.settings.knownVehiclesOf
import com.shawnkowalchuk.milo.data.settings.mergeKnownVehicles
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

// The settings every phone of an online backup account shares (ADR-017, step 3, 2026-11-14):
// the report details, the service book and the odometer readings. Work hours and the trip
// rules stay each phone's own: each phone records its own trips.
//
// Each is one document under `users/{account}/shared/`. The report details and the service
// book: the change uploaded last wins, as for trips. The odometer readings: every reading any
// phone typed is kept, since a reading is a fact and two cannot contradict each other.

/** The collection of the shared documents. */
const val CLOUD_SHARED = "shared"

const val SHARED_REPORT = "$CLOUD_SHARED/report"
const val SHARED_SERVICE = "$CLOUD_SHARED/service"
const val SHARED_ODOMETER = "$CLOUD_SHARED/odometer"

/**
 * The account's vehicles (ADR-018, 2026-11-16): every phone's paired vehicles, and which of an
 * iPhone's are the same as one of Android's. Merged as a set, as the odometer readings are.
 */
const val SHARED_VEHICLES = "$CLOUD_SHARED/vehicles"

/** The details the report prints, as every phone shares them. */
@Serializable
data class SharedReport(
    val name: String? = null,
    val company: String? = null,
    val vehicle: String? = null,
    val accountantEmail: String? = null,
)

/**
 * An odometer reading as every phone shares it. Which trip was being recorded when it was typed
 * stays on the phone that typed it: another phone numbers that trip differently.
 */
@Serializable
data class SharedReading(
    val atMs: Long,
    val value: Long,
    val unit: String,
    val vehicle: String? = null,
)

@Serializable
data class SharedOdometer(val readings: List<SharedReading>)

private fun OdometerReading.shared() = SharedReading(atMs, value, unit.name, vehicle)

private fun SharedReading.reading(): OdometerReading? =
    DistanceUnit.entries.firstOrNull { it.name == unit }?.let {
        OdometerReading(atMs = atMs, value = value, unit = it, vehicle = vehicle)
    }

/** The three shared documents as [settings] make them. */
fun sharedDocuments(settings: MiloSettings): List<CloudDocument> = listOf(
    CloudDocument(
        SHARED_REPORT,
        ExportJson.encodeToString(
            SharedReport.serializer(),
            SharedReport(
                settings.reportName,
                settings.reportCompany,
                settings.reportVehicle,
                settings.accountantEmail,
            ),
        ),
    ),
    CloudDocument(
        SHARED_SERVICE,
        ExportJson.encodeToString(ServiceBook.serializer(), settings.serviceBook),
    ),
    CloudDocument(SHARED_ODOMETER, odometerJson(settings.odometerReadings.map { it.shared() })),
    CloudDocument(SHARED_VEHICLES, knownVehiclesJson(settings.accountVehicles())),
)

private fun odometerJson(readings: Collection<SharedReading>): String = ExportJson.encodeToString(
    SharedOdometer.serializer(),
    SharedOdometer(readings.distinct().sortedWith(compareBy({ it.atMs }, { it.value }))),
)

/**
 * The snapshot with the shared documents added, from [settings], or as unknown when the
 * settings could not be read: then what the cloud holds is neither written nor removed.
 */
fun CloudSnapshot.withShared(settings: MiloSettings?): CloudSnapshot = if (settings == null) {
    copy(unknown = unknown + setOf(SHARED_REPORT, SHARED_SERVICE, SHARED_ODOMETER, SHARED_VEHICLES))
} else {
    copy(documents = documents + sharedDocuments(settings))
}

/**
 * Before each upload (ADR-017, step 3): what other phones changed in the shared documents
 * comes into this phone's settings, and the ledger learns it. A phone that has never shared
 * takes what the account holds. A document changed here too is kept, and this phone's upload
 * replaces the other's. The odometer readings of every phone are added to this phone's.
 *
 * @throws IOException if the cloud or the ledger fails.
 */
suspend fun syncShared(
    store: CloudStore,
    account: String,
    settings: SettingsStore,
    ledger: CloudLedgerStore,
    log: suspend (String) -> Unit,
) {
    val cloud = store.readCollections(account, listOf(CLOUD_SHARED))
    if (cloud.isEmpty()) return
    val known = ledger.read(account).toMutableMap()
    val mine = sharedDocuments(settings.current()).associateBy { it.path }
    for (path in listOf(SHARED_REPORT, SHARED_SERVICE)) {
        val json = cloud[path] ?: continue
        val theirs = CloudDocument(path, json).fingerprint
        val sent = known[path]
        val ours = mine.getValue(path).fingerprint
        when {
            theirs == sent -> Unit

            theirs == ours -> known[path] = theirs

            sent != null && ours != sent ->
                log(
                    "Cloud backup: $path was changed here and on another phone; this phone's is kept.",
                )

            take(path, json, settings) -> {
                known[path] = theirs
                log("Cloud backup: $path came from another phone.")
            }
        }
    }
    cloud[SHARED_ODOMETER]?.let { json ->
        val theirs = readingsOf(json) ?: return@let
        val ours = settings.current().odometerReadings.map { it.shared() }.toSet()
        val missing = theirs - ours
        for (reading in missing) reading.reading()?.let { settings.addOdometerReading(it) }
        if (missing.isNotEmpty()) {
            log("Cloud backup: ${missing.size} odometer readings came from another phone.")
        }
        // Holding every reading the cloud has, and no more: nothing of ours is to be sent.
        if (ours + missing ==
            theirs
        ) {
            known[SHARED_ODOMETER] = CloudDocument(SHARED_ODOMETER, json).fingerprint
        }
    }
    cloud[SHARED_VEHICLES]?.let { json ->
        val theirs = knownVehiclesOf(json) ?: return@let
        if (settings.mergeKnownVehicles(theirs)) {
            log("Cloud backup: the account's vehicles came from another phone.")
        }
        // Holding everything the cloud has, and no more: nothing of ours is to be sent.
        if (settings.current().accountVehicles() == theirs.with(KnownVehicles())) {
            known[SHARED_VEHICLES] = CloudDocument(SHARED_VEHICLES, json).fingerprint
        }
    }
    ledger.write(account, known)
}

/** Writes the shared document [json] at [path] into this phone's settings. */
private suspend fun take(path: String, json: String, settings: SettingsStore): Boolean = try {
    when (path) {
        SHARED_REPORT -> {
            val report = ExportJson.decodeFromString(SharedReport.serializer(), json)
            settings.setReportName(report.name)
            settings.setReportCompany(report.company)
            settings.setReportVehicle(report.vehicle)
            settings.setAccountantEmail(report.accountantEmail)
        }

        SHARED_SERVICE -> {
            val book = ExportJson.decodeFromString(ServiceBook.serializer(), json)
            settings.changeServiceBook { book }
        }
    }
    true
} catch (unreadable: SerializationException) {
    false
} catch (refused: IllegalArgumentException) {
    false
}

private fun readingsOf(json: String): Set<SharedReading>? = try {
    ExportJson.decodeFromString(SharedOdometer.serializer(), json).readings.toSet()
} catch (unreadable: SerializationException) {
    null
} catch (unreadable: IllegalArgumentException) {
    null
}
