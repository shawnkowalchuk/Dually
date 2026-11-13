package com.shawnkowalchuk.milo.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// One phone backs up to an account at a time (ADR-012). The backup says which, in its owner
// document (`backup/owner`, the install's own number), and every upload looks first (slice 1e,
// 2026-11-12). A second phone that signs in to an account with trips in it is not let upload
// over them: it is asked to restore them, or to replace them with its own. The first phone,
// once another has taken over, stops at its next upload.

/** What a phone does about an account's backup, decided before each upload. */
enum class CloudStart {
    /** This phone backs up: upload. */
    BACK_UP,

    /** Another phone's trips are in the backup: ask Restore here, or Back up this phone. */
    ASK,

    /** This phone backed up, and another has taken over since: stop, and say so. */
    MOVED,
}

/**
 * @param owner the install number in the backup's owner document, or null if it has none.
 * @param install this install's own number.
 * @param backingUp what this phone last knew: true if it was backing up, false if it was
 * waiting for an answer, null if it has never decided (a phone that backed up before the owner
 * document existed, in builds 7 and 8).
 * @param cloudHasTrips whether the backup holds any trip.
 * @param uploadedHere whether this phone's ledger shows uploads to this account.
 */
fun cloudStart(
    owner: String?,
    install: String,
    backingUp: Boolean?,
    cloudHasTrips: Boolean,
    uploadedHere: Boolean,
): CloudStart = when {
    owner == install -> CloudStart.BACK_UP

    owner != null -> if (backingUp == true) CloudStart.MOVED else CloudStart.ASK

    // Nobody owns it yet: an empty backup, or one this phone filled before there was an owner,
    // is this phone's to take. Another phone's trips without an owner are asked about.
    !cloudHasTrips || uploadedHere -> CloudStart.BACK_UP

    else -> CloudStart.ASK
}

/** The owner document's JSON, as the backup keeps it: `{"install":"…"}`. */
@Serializable
private data class CloudOwner(val install: String)

private val ownerJson = Json { ignoreUnknownKeys = true }

/** The owner document that names [install] as the phone backing up. */
fun cloudOwnerDocument(install: String): CloudDocument = CloudDocument(
    CLOUD_OWNER,
    ownerJson.encodeToString(CloudOwner.serializer(), CloudOwner(install)),
)

/** The install an owner document names, or null if it cannot be read. */
fun cloudOwnerOf(json: String): String? = try {
    ownerJson.decodeFromString(CloudOwner.serializer(), json).install
} catch (unreadable: SerializationException) {
    null
} catch (unreadable: IllegalArgumentException) {
    null
}

/** What the Online backup tile is told when it asks how the backup stands. */
sealed interface CloudStartResult {
    /** This phone backs up. */
    data object BackingUp : CloudStartResult

    /** Another phone's [trips] are in the backup: Restore here, or Back up this phone. */
    data class Choose(val trips: Int) : CloudStartResult

    /** Another phone took the backup over: this one stopped. */
    data class Moved(val trips: Int) : CloudStartResult

    /** The backup could not be read: nothing was decided. */
    data object Offline : CloudStartResult
}

/** What "Restore my trips here" came to. */
sealed interface CloudRestoreResult {
    /** The phone holds the backup's [trips] now, and backs up from here. */
    data class Restored(val trips: Int) : CloudRestoreResult

    /** Not while a trip is being recorded: it would be replaced under the recording. */
    data object TripInProgress : CloudRestoreResult

    /** The backup could not be read. Nothing was changed. */
    data object Offline : CloudRestoreResult

    /** It did not work; [cause] is for the Log. Nothing was changed, or the safety copy has it. */
    data class Failed(val cause: String) : CloudRestoreResult
}
