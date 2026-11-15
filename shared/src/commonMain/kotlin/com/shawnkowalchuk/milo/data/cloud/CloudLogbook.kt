package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import com.shawnkowalchuk.milo.data.transfer.toExported
import com.shawnkowalchuk.milo.data.transfer.toTrip
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripDao
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

// One logbook on every phone of an online backup account (ADR-017, 2026-11-14). Each phone
// backs up into a folder of its own, `phones/{its install number}/`, with a record of itself at
// `phones/{install}`; each phone reads the other phones' folders, and their finished trips
// become rows of its own trips table, marked with the phone they came from. The same drive
// recorded by two phones is counted once.
//
// Paths here are under the account's folder in the cloud (`users/{account}/`), as every
// backup path is.

/** The collection of the phones' folders. */
const val CLOUD_PHONES = "phones"

/** The collection of a backup's documents other than its trips. */
const val CLOUD_BACKUP = "backup"

/** The folder a phone backs up into, and its record's path. */
fun cloudPhoneFolder(install: String): String = "$CLOUD_PHONES/$install"

/**
 * The phone [LEGACY_PHONE] names, for a backup written by a version of Dually from before
 * ADR-017 that never named its phone (builds 7 and 8 of Android).
 */
const val LEGACY_PHONE = "earlier-backup"

/** A phone's record: what it is called, and which kind of phone it is. */
@Serializable
data class CloudPhone(val name: String, val platform: String)

/** The record of this phone, in its folder. */
fun cloudPhoneDocument(install: String, name: String, platform: String): CloudDocument =
    CloudDocument(
        cloudPhoneFolder(install),
        ExportJson.encodeToString(CloudPhone.serializer(), CloudPhone(name, platform)),
    )

/** A phone's record, or null if it cannot be read. */
fun cloudPhoneOf(json: String): CloudPhone? = try {
    ExportJson.decodeFromString(CloudPhone.serializer(), json)
} catch (unreadable: SerializationException) {
    null
} catch (unreadable: IllegalArgumentException) {
    null
}

/**
 * The snapshot with every path moved into [folder], and the phone's record added: what this
 * phone uploads since ADR-017. An older version's ledger names the same documents at the
 * account's top, so the first run of this version removes those as it writes these.
 */
fun CloudSnapshot.inFolder(folder: String, record: CloudDocument): CloudSnapshot = CloudSnapshot(
    documents = documents.map { CloudDocument("$folder/${it.path}", it.json) } + record,
    unknown = unknown.map { "$folder/$it" }.toSet(),
)

/**
 * The finished trips among [documents] (any paths: those of one folder's `trips`), as the phone
 * that wrote them has them, its own ids included. A document that cannot be read is left out:
 * one bad document must not empty another phone's logbook here.
 */
fun cloudTripsOf(documents: Map<String, String>): List<Trip> =
    documents.filterKeys { it.substringBeforeLast('/').endsWith(CLOUD_TRIPS) }
        .values
        .mapNotNull { json ->
            try {
                ExportJson.decodeFromString(CloudTrip.serializer(), json).trip.toTrip()
            } catch (unreadable: SerializationException) {
                null
            } catch (unreadable: IllegalArgumentException) {
                null
            }
        }.filter { it.status == TripStatus.FINISHED }

/**
 * The other phones of an account, as their folders were read.
 *
 * @param trips by phone, its finished trips with that phone's own ids.
 * @param names by phone, what it is called.
 */
data class OtherPhones(val trips: Map<String, List<Trip>>, val names: Map<String, String>) {
    /** How many trips they hold together. */
    val tripCount: Int get() = trips.values.sumOf { it.size }
}

/**
 * Reads every other phone's folder of [account]. A backup written at the account's top by a
 * version from before ADR-017 counts as the phone its owner document names, until that phone
 * has moved it into its own folder.
 *
 * @throws com.shawnkowalchuk.milo.core.io.IOException if the backup cannot be read.
 */
suspend fun readOtherPhones(store: CloudStore, account: String, install: String): OtherPhones {
    val records = store.readCollections(account, listOf(CLOUD_PHONES))
    val trips = mutableMapOf<String, List<Trip>>()
    val names = mutableMapOf<String, String>()
    for ((path, json) in records) {
        val phone = path.removePrefix("$CLOUD_PHONES/")
        if (phone == install || phone.contains('/')) continue
        trips[phone] =
            cloudTripsOf(store.readCollections(account, listOf("$path/$CLOUD_TRIPS")))
        names[phone] = cloudPhoneOf(json)?.name ?: "Another phone"
    }
    val earlier = store.readCollections(account, listOf(CLOUD_TRIPS, CLOUD_BACKUP))
    val owner = earlier[CLOUD_OWNER]?.let(::cloudOwnerOf)
    if (owner != install && owner !in trips) {
        val earlierTrips = cloudTripsOf(earlier)
        if (earlierTrips.isNotEmpty()) {
            // Read-only here: the phone that wrote it reads no other phone's changes.
            val phone = if (owner == null) LEGACY_PHONE else "$LEGACY_PHONE:$owner"
            trips[phone] = earlierTrips
            names[phone] = "Your other phone"
        }
    }
    return OtherPhones(trips, names)
}

/**
 * Whether [phone], a trip's home, is the backup an older version keeps at the account's top
 * (ADR-017): its trips are read-only on the other phones, since that version reads no other
 * phone's changes, and goes to its own folder once it is updated.
 */
fun cloudIsEarlierBackup(phone: String?): Boolean = phone?.startsWith(LEGACY_PHONE) == true

/** A trip's document as [path] holds it: the export format's JSON, as every trip's. */
private fun tripDocument(path: String, trip: Trip): CloudDocument = CloudDocument(
    path,
    ExportJson.encodeToString(
        CloudTrip.serializer(),
        CloudTrip(
            EXPORT_FORMAT_VERSION,
            trip.copy(homePhone = null, homeTripId = null).toExported(),
        ),
    ),
)

/**
 * Another phone's trip as this phone would write it into that phone's folder (step 4 of
 * ADR-017): under its home, with its id there. Null for a trip of an older version's backup,
 * which is read-only.
 */
fun cloudHomeDocument(row: Trip): CloudDocument? {
    val home = row.homePhone ?: return null
    val id = row.homeTripId ?: return null
    if (cloudIsEarlierBackup(home)) return null
    return tripDocument("${cloudPhoneFolder(home)}/${cloudTripPath(id)}", row.copy(id = id))
}

/**
 * The snapshot with this phone's rows of other phones' trips added, each at its home: a row
 * changed here differs from what the ledger has for it, and its upload is the change reaching
 * the phone that recorded it and every other phone (step 4 of ADR-017).
 */
fun CloudSnapshot.withEdits(otherPhonesRows: List<Trip>): CloudSnapshot =
    copy(documents = documents + otherPhonesRows.mapNotNull(::cloudHomeDocument))

/**
 * Whether a run of this phone ([install]) may remove [path] from the cloud: its own documents,
 * and an older version's at the account's top, which it moves into its folder. Never another
 * phone's: a trip this phone no longer holds of theirs is still theirs.
 */
fun cloudRemovableBy(install: String, path: String): Boolean =
    !path.startsWith("$CLOUD_PHONES/") || path == cloudPhoneFolder(install) ||
        path.startsWith("${cloudPhoneFolder(install)}/")

/**
 * Before each upload (ADR-017, steps 2 and 4): the changes other phones made to this phone's
 * own trips come in; the other phones' trips are brought up to date, except those changed here
 * and not yet uploaded; and the ledger learns what this phone now holds of theirs, so that only
 * a change made here is uploaded.
 *
 * Two changes to one trip: the one uploaded last wins. A change from another phone to a trip
 * changed here too is not taken: this phone's upload follows, and replaces it. A line in the
 * Log says which trips changed.
 *
 * @throws com.shawnkowalchuk.milo.core.io.IOException if the cloud or the ledger fails.
 */
suspend fun syncLogbook(
    store: CloudStore,
    account: String,
    install: String,
    others: OtherPhones,
    trips: TripDao,
    ledger: CloudLedgerStore,
    log: suspend (String) -> Unit,
) {
    val known = ledger.read(account).toMutableMap()
    val folder = cloudPhoneFolder(install)
    // 1. This phone's own trips, as other phones may have changed them in its folder.
    val own = trips.findOwn().associateBy { it.id }
    val inFolder = store.readCollections(account, listOf("$folder/$CLOUD_TRIPS"))
    val taken = mutableListOf<Trip>()
    for ((path, json) in inFolder) {
        val cloud = CloudDocument(path, json).fingerprint
        val sent = known[path] ?: continue
        if (cloud == sent) continue
        val id = path.substringAfterLast('/').toLongOrNull() ?: continue
        val row = own[id] ?: continue
        if (tripDocument(path, row).fingerprint != sent) {
            log(
                "Cloud backup: trip $id was changed here and on another phone; this phone's is kept.",
            )
            continue
        }
        val changed = cloudTripOf(json) ?: continue
        taken += changed.copy(id = row.id, homePhone = null, homeTripId = null)
        known[path] = cloud
    }
    if (taken.isNotEmpty()) {
        trips.updateAll(taken)
        log("Cloud backup: ${taken.size} of this phone's trips were changed on another phone.")
    }
    // 2. The other phones' trips, but for those changed here and not yet uploaded.
    val stored = trips.findFromOtherPhones()
    val pending =
        stored.filter { row ->
            val document = cloudHomeDocument(row) ?: return@filter false
            val sent = known[document.path]
            sent != null && sent != document.fingerprint
        }.map { it.id }.toSet()
    val change = logbookChange(stored, own.values.toList(), others)
    val kept =
        LogbookChange(
            insert = change.insert,
            update = change.update.filter { it.id !in pending },
            delete = change.delete.filter { it !in pending },
        )
    if (!kept.isEmpty) {
        trips.replaceFromOtherPhones(kept.insert, kept.update, kept.delete)
        log(
            "Cloud backup: from the other phones, ${kept.insert.size} trips added, " +
                "${kept.update.size} changed, ${kept.delete.size} removed.",
        )
    }
    // 3. The ledger: what this phone holds of the other phones' trips, as it would write them.
    val now = trips.findFromOtherPhones()
    val held = mutableSetOf<String>()
    for (row in now) {
        val document = cloudHomeDocument(row) ?: continue
        held += document.path
        if (row.id !in pending) known[document.path] = document.fingerprint
    }
    known.keys.removeAll { path ->
        path.startsWith("$CLOUD_PHONES/") && !path.startsWith("$folder/") && path != folder &&
            path !in held
    }
    ledger.write(account, known)
}

/** A trip of any status from its document, or null if it cannot be read. */
private fun cloudTripOf(json: String): Trip? = try {
    ExportJson.decodeFromString(CloudTrip.serializer(), json).trip.toTrip()
} catch (unreadable: SerializationException) {
    null
} catch (unreadable: IllegalArgumentException) {
    null
}

/** What changes in this phone's rows of other phones' trips. */
data class LogbookChange(val insert: List<Trip>, val update: List<Trip>, val delete: List<Long>) {
    val isEmpty: Boolean get() = insert.isEmpty() && update.isEmpty() && delete.isEmpty()
}

/**
 * How this phone's rows of other phones' trips are to change so that they hold [others]:
 * each finished trip of another phone, unless it is a drive this phone recorded too
 * ([sameDrive] with one of [own]'s finished trips) or one another phone's trip already kept
 * covers (the one that started first is kept).
 *
 * @param stored this phone's rows of other phones' trips, as they are now.
 * @param own this phone's own trips.
 */
fun logbookChange(stored: List<Trip>, own: List<Trip>, others: OtherPhones): LogbookChange {
    val ownDrives = own.filter { it.status == TripStatus.FINISHED }
    val kept = mutableListOf<Trip>()
    val candidates =
        others.trips.flatMap { (phone, trips) ->
            trips.map { it.copy(id = 0, homePhone = phone, homeTripId = it.id) }
        }.sortedWith(compareBy({ it.startedAtMs }, { it.homePhone }, { it.homeTripId }))
    for (trip in candidates) {
        if (ownDrives.any { sameDrive(it, trip) } || kept.any { sameDrive(it, trip) }) continue
        kept += trip
    }
    val byHome = stored.associateBy { it.homePhone to it.homeTripId }
    val insert = mutableListOf<Trip>()
    val update = mutableListOf<Trip>()
    for (trip in kept) {
        val row = byHome[trip.homePhone to trip.homeTripId]
        when {
            row == null -> insert += trip
            trip.copy(id = row.id) != row -> update += trip.copy(id = row.id)
        }
    }
    val keptKeys = kept.map { it.homePhone to it.homeTripId }.toSet()
    val delete = stored.filter { (it.homePhone to it.homeTripId) !in keptKeys }.map { it.id }
    return LogbookChange(insert, update, delete)
}

/**
 * Whether [a] and [b] are the same drive, recorded twice: they overlap by more than half of
 * the shorter of the two. A trip without an end is no drive yet.
 */
fun sameDrive(a: Trip, b: Trip): Boolean {
    val aEnd = a.endedAtMs ?: return false
    val bEnd = b.endedAtMs ?: return false
    val overlap = minOf(aEnd, bEnd) - maxOf(a.startedAtMs, b.startedAtMs)
    val shorter = minOf(aEnd - a.startedAtMs, bEnd - b.startedAtMs)
    return shorter > 0 && overlap * 2 > shorter
}

/**
 * Whether a phone that has just signed in is to be asked before it joins the logbook: it has
 * finished trips of its own and the other phones have trips too, and it has not answered.
 * Otherwise it joins at once, with nothing to press (Shawn, 2026-11-14: "i dont like the idea
 * of hitting extra buttons").
 */
fun cloudAsksToMerge(ownFinished: Int, others: OtherPhones, answered: Boolean?): Boolean =
    answered != true && ownFinished > 0 && others.tripCount > 0
