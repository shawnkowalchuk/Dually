package com.shawnkowalchuk.milo.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// Until ADR-017 (2026-11-14) one phone backed up to an account, named in the backup's owner
// document (`backup/owner`). Since then every phone backs up into a folder of its own
// (CloudLogbook.kt); the owner document is only read, to know which phone an older version's
// backup at the account's top belongs to, and that phone removes it once it has moved its
// backup into its own folder.

/** The owner document's JSON, as the backup keeps it: `{"install":"…"}`. */
@Serializable
private data class CloudOwner(val install: String)

private val ownerJson = Json { ignoreUnknownKeys = true }

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
    /** This phone backs up, and holds the other phones' trips. */
    data object BackingUp : CloudStartResult

    /**
     * This phone has [own] trips of its own and the other phones [others]: it asks once whether
     * to merge them into one logbook (ADR-017) before it uploads or takes theirs.
     */
    data class Merge(val own: Int, val others: Int) : CloudStartResult

    /** The backup could not be read: nothing was decided. */
    data object Offline : CloudStartResult
}

/** What "Use only the backup's trips" came to (ADR-017). */
sealed interface CloudRestoreResult {
    /** This phone's own trips were taken off it; it holds the other phones' [trips]. */
    data class Restored(val trips: Int) : CloudRestoreResult

    /** Not while a trip is being recorded: it would be replaced under the recording. */
    data object TripInProgress : CloudRestoreResult

    /** The backup could not be read. Nothing was changed. */
    data object Offline : CloudRestoreResult

    /** It did not work; [cause] is for the Log. Nothing was changed, or the safety copy has it. */
    data class Failed(val cause: String) : CloudRestoreResult
}
