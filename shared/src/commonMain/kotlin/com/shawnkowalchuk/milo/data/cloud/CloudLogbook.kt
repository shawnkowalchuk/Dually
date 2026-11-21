package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.keepOtherPhonesSent
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import com.shawnkowalchuk.milo.data.transfer.ExportedTrip
import com.shawnkowalchuk.milo.data.transfer.problem
import com.shawnkowalchuk.milo.data.transfer.toExported
import com.shawnkowalchuk.milo.data.transfer.toSentReport
import com.shawnkowalchuk.milo.data.transfer.toTrip
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripDao
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

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
        .mapNotNull(::cloudTripOf)
        .filter { it.status == TripStatus.FINISHED }

/**
 * The sent reports among [documents] (any paths: one folder's `backup`), as the phone that
 * recorded them has them (issue #164). A document that cannot be read, or is of a newer format,
 * gives none, and a report that is not right is left out: one bad document must not stop the
 * sync, and leaves only that phone's reports out here.
 */
fun cloudSentReportsOf(documents: Map<String, String>): List<SentReport> {
    val json =
        documents.entries
            .firstOrNull { (path, _) ->
                path == CLOUD_SENT_REPORTS || path.endsWith("/$CLOUD_SENT_REPORTS")
            }
            ?.value ?: return emptyList()
    val document =
        try {
            ExportJson.decodeFromString(CloudSentReports.serializer(), json)
        } catch (unreadable: SerializationException) {
            return emptyList()
        } catch (unreadable: IllegalArgumentException) {
            return emptyList()
        }
    if (document.formatVersion > EXPORT_FORMAT_VERSION) return emptyList()
    return document.sentReports
        .filter { it.problem(document.formatVersion) == null }
        .map { it.toSentReport() }
}

/**
 * The other phones of an account, as their folders were read.
 *
 * @param trips by phone, its finished trips with that phone's own ids.
 * @param names by phone, what it is called.
 * @param sentReports by phone, the reports it recorded as sent, with its own ids (issue #164).
 */
data class OtherPhones(
    val trips: Map<String, List<Trip>>,
    val names: Map<String, String>,
    val sentReports: Map<String, List<SentReport>> = emptyMap(),
) {
    /** How many trips they hold together. */
    val tripCount: Int get() = trips.values.sumOf { it.size }

    /** Every report they recorded as sent, all phones' in one list. */
    val sentList: List<SentReport> get() = sentReports.values.flatten()
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
    val sent = mutableMapOf<String, List<SentReport>>()
    for ((path, json) in records) {
        val phone = path.removePrefix("$CLOUD_PHONES/")
        if (phone == install || phone.contains('/')) continue
        val folder = store.readCollections(
            account,
            listOf("$path/$CLOUD_TRIPS", "$path/$CLOUD_BACKUP"),
        )
        trips[phone] = cloudTripsOf(folder)
        sent[phone] = cloudSentReportsOf(folder)
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
            sent[phone] = cloudSentReportsOf(earlier)
        }
    }
    return OtherPhones(trips, names, sent)
}

/**
 * After [syncLogbook], on both phones: keeps the reports the other phones recorded as sent
 * (issue #164), so that a month sent on any phone is submitted on this one too: its trips are
 * locked, and the monthly reminder stops for it.
 *
 * @return whether they changed, for the phone to look at its reminder again.
 */
suspend fun syncOtherPhonesSent(
    others: OtherPhones,
    settings: SettingsStore,
    log: suspend (String) -> Unit,
): Boolean {
    val reports = others.sentList
    val changed = settings.keepOtherPhonesSent(reports)
    if (changed) {
        log("Cloud backup: the other phones' sent reports came in, ${reports.size} in all.")
    }
    return changed
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
            historyOf(trip),
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
 * phone's, nor a shared document: a trip this phone no longer holds of theirs is still theirs.
 */
fun cloudRemovableBy(install: String, path: String): Boolean = path == cloudPhoneFolder(install) ||
    path.startsWith("${cloudPhoneFolder(install)}/") ||
    path.startsWith("$CLOUD_TRIPS/") ||
    path.startsWith("$CLOUD_BACKUP/")

/**
 * Before each upload (ADR-017, steps 2 and 4): the changes other phones made to this phone's
 * own trips come in; the other phones' trips are brought up to date, except those changed here
 * and not yet uploaded; and the ledger learns what this phone now holds of theirs, so that only
 * a change made here is uploaded.
 *
 * Two changes to one trip: the one uploaded last wins. The phone that finds the other's change
 * under its own keeps its own, which its upload sends next, and adds the other's to the trip's
 * history; the phone whose change it was finds it there, marked replaced, and is told
 * (`ReplacedNotice`), once (the entry is then marked noticed, on every phone).
 *
 * @param phoneName what this phone is called, for the history.
 * @return the changes of this phone's that another phone's replaced, newly found.
 * @throws com.shawnkowalchuk.milo.core.io.IOException if the cloud or the ledger fails.
 */
suspend fun syncLogbook(
    store: CloudStore,
    account: String,
    install: String,
    phoneName: String,
    nowMs: Long,
    others: OtherPhones,
    trips: TripDao,
    ledger: CloudLedgerStore,
    log: suspend (String) -> Unit,
): List<ReplacedNotice> {
    val known = ledger.read(account).toMutableMap()
    val folder = cloudPhoneFolder(install)
    val notices = mutableListOf<ReplacedNotice>()
    // 1. This phone's own trips, as other phones may have changed them in its folder.
    val own = trips.findOwn().associateBy { it.id }
    val inFolder = store.readCollections(account, listOf("$folder/$CLOUD_TRIPS"))
    val written = mutableListOf<Trip>()
    for ((path, json) in inFolder) {
        val cloud = CloudDocument(path, json).fingerprint
        val sent = known[path] ?: continue
        if (cloud == sent) continue
        val id = path.substringAfterLast('/').toLongOrNull() ?: continue
        val row = own[id] ?: continue
        val theirs = cloudTripOf(json) ?: continue
        if (tripDocument(path, row).fingerprint != sent) {
            // Changed here too: this phone's is kept, and theirs goes into the history.
            written += row.replacing(theirs, cloud, nowMs, phoneName)
            log(
                "Cloud backup: trip $id was changed here and on another phone; this phone's is kept.",
            )
            continue
        }
        val (taken, told) = theirs.noticing(sent)
        notices += told
        written += taken.copy(id = row.id, homePhone = null, homeTripId = null)
        // What the cloud holds is known; a notice marked here differs, and is uploaded.
        known[path] = cloud
    }
    if (written.isNotEmpty()) {
        trips.updateAll(written)
        log("Cloud backup: ${written.size} of this phone's trips were changed on another phone.")
    }
    // 2. The other phones' trips, but for those changed here and not yet uploaded.
    val stored = trips.findFromOtherPhones()
    val byHome = others.trips.flatMap { (phone, list) ->
        list.map { (phone to it.id) to it }
    }.toMap()
    val pending = mutableSetOf<Long>()
    val marked = mutableMapOf<Pair<String, Long>, Trip>()
    val cloudPrints = mutableMapOf<String, String>()
    val replaced = mutableListOf<Trip>()
    for (row in stored) {
        val home = row.homePhone ?: continue
        val homeId = row.homeTripId ?: continue
        val document = cloudHomeDocument(row) ?: continue
        val sent = known[document.path]
        val cloudTrip = byHome[home to homeId]
        val cloudPrint =
            cloudTrip?.let { cloudHomeDocument(it.copy(homePhone = home, homeTripId = homeId)) }
                ?.fingerprint
        cloudPrint?.let { cloudPrints[document.path] = it }
        if (sent != null && sent != document.fingerprint) {
            pending += row.id
            if (cloudTrip != null && cloudPrint != sent) {
                // Changed here and, since this phone last saw it, there: this phone's is kept.
                replaced += row.replacing(cloudTrip, cloudPrint.orEmpty(), nowMs, phoneName)
                log(
                    "Cloud backup: a trip was changed here and on another phone; this phone's is kept.",
                )
            }
            continue
        }
        if (cloudTrip != null && sent != null) {
            val (taken, told) = cloudTrip.noticing(sent)
            if (told.isNotEmpty()) {
                notices += told
                marked[home to homeId] = taken
            }
        }
    }
    if (replaced.isNotEmpty()) trips.updateAll(replaced)
    val noticedOthers =
        OtherPhones(
            trips =
                others.trips.mapValues { (phone, list) ->
                    list.map { marked[phone to it.id] ?: it }
                },
            names = others.names,
        )
    val change = logbookChange(stored, own.values.toList(), noticedOthers)
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
    // A row marked here (a notice) is known as the cloud holds it, so that its upload follows.
    val markedKeys = marked.keys
    val now = trips.findFromOtherPhones()
    val held = mutableSetOf<String>()
    for (row in now) {
        val document = cloudHomeDocument(row) ?: continue
        held += document.path
        when {
            row.id in pending -> Unit

            (row.homePhone to row.homeTripId) in markedKeys ->
                cloudPrints[document.path]?.let { known[document.path] = it }

            else -> known[document.path] = document.fingerprint
        }
    }
    known.keys.removeAll { path ->
        path.startsWith("$CLOUD_PHONES/") && !path.startsWith("$folder/") && path != folder &&
            path !in held
    }
    ledger.write(account, known)
    return notices
}

/**
 * A version of a trip that another phone's change replaced, kept in the trip's history
 * (ADR-017).
 *
 * @param atMs when it was replaced.
 * @param keptOn the phone whose change was kept, by name ("iPhone").
 * @param replacedFingerprint the fingerprint of the replaced version's document, by which the
 * phone that uploaded it knows it as its own.
 * @param trip the replaced version.
 * @param noticed whether the phone whose change it was has been told.
 */
@Serializable
data class ReplacedChange(
    val atMs: Long,
    val keptOn: String,
    val replacedFingerprint: String,
    val trip: ExportedTrip,
    val noticed: Boolean = false,
)

/** A change of this phone's that another phone's replaced: [keptOn] is the other phone's name. */
data class ReplacedNotice(val tripStartedAtMs: Long, val keptOn: String)

/** How many replaced versions a trip keeps. */
private const val HISTORY_KEPT = 10

private val historySerializer = ListSerializer(ReplacedChange.serializer())

/** The replaced versions [trip] keeps, oldest first. */
fun historyOf(trip: Trip): List<ReplacedChange> = trip.changeHistory?.let { json ->
    try {
        ExportJson.decodeFromString(historySerializer, json)
    } catch (unreadable: SerializationException) {
        null
    } catch (unreadable: IllegalArgumentException) {
        null
    }
}.orEmpty()

private fun historyJson(history: List<ReplacedChange>): String? =
    history.takeIf { it.isNotEmpty() }?.let {
        ExportJson.encodeToString(historySerializer, it.takeLast(HISTORY_KEPT))
    }

/** Two histories as one, each version once, oldest first. */
private fun merged(a: List<ReplacedChange>, b: List<ReplacedChange>): List<ReplacedChange> =
    (a + b).groupBy { it.atMs to it.replacedFingerprint }
        .map { (_, same) -> same.first().copy(noticed = same.any { it.noticed }) }
        .sortedBy { it.atMs }

/** This version kept over [theirs]: its history holds theirs as replaced, and theirs too. */
private fun Trip.replacing(theirs: Trip, theirsPrint: String, nowMs: Long, keptOn: String): Trip {
    val entry =
        ReplacedChange(
            atMs = nowMs,
            keptOn = keptOn,
            replacedFingerprint = theirsPrint,
            trip = theirs.copy(
                homePhone = null,
                homeTripId = null,
                changeHistory = null,
            ).toExported(),
        )
    return copy(changeHistory = historyJson(merged(historyOf(this), historyOf(theirs)) + entry))
}

/**
 * This trip with the history entries that replaced [mine] (the fingerprint of what this phone
 * last uploaded or saw of it) marked noticed, and the notices for them.
 */
private fun Trip.noticing(mine: String): Pair<Trip, List<ReplacedNotice>> {
    val history = historyOf(this)
    val found = history.filter { !it.noticed && it.replacedFingerprint == mine }
    if (found.isEmpty()) return this to emptyList()
    val marked = history.map { if (it in found) it.copy(noticed = true) else it }
    return copy(changeHistory = historyJson(marked)) to
        found.map { ReplacedNotice(startedAtMs, it.keptOn) }
}

/**
 * "Put back my change" (ADR-017): the trip [tripId] takes the newest version its history holds,
 * and the version it had goes into the history in its place, as replaced by this phone. Its
 * next upload sends it to every phone.
 *
 * @return false if the trip has no history, or cannot be changed here.
 */
suspend fun putBackReplaced(trips: TripDao, tripId: Long, nowMs: Long, phoneName: String): Boolean {
    val row = trips.findById(tripId) ?: return false
    if (cloudIsEarlierBackup(row.homePhone)) return false
    val history = historyOf(row)
    val newest = history.lastOrNull() ?: return false
    val path = cloudHomeDocument(row)?.path ?: cloudTripPath(row.id)
    val now =
        ReplacedChange(
            atMs = nowMs,
            keptOn = phoneName,
            replacedFingerprint = tripDocument(path, row).fingerprint,
            trip = row.copy(homePhone = null, homeTripId = null, changeHistory = null).toExported(),
            noticed = true,
        )
    val back =
        newest.trip.toTrip().copy(
            id = row.id,
            homePhone = row.homePhone,
            homeTripId = row.homeTripId,
            changeHistory = historyJson(history.dropLast(1) + now),
        )
    trips.updateAll(listOf(back))
    return true
}

/** A trip of any status from its document, with its history, or null if it cannot be read. */
private fun cloudTripOf(json: String): Trip? = try {
    val document = ExportJson.decodeFromString(CloudTrip.serializer(), json)
    document.trip.toTrip().copy(changeHistory = historyJson(document.history))
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
