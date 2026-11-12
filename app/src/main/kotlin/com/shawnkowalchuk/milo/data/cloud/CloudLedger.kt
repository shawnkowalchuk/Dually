package com.shawnkowalchuk.milo.data.cloud

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The folder of the ledger, inside the app's never-backed-up files. */
private const val CLOUD_FOLDER = "cloud"

private const val LEDGER_FILE = "uploaded.json"

/** The file the ledger is written into before it takes the real one's place. */
private const val LEDGER_FILE_BEING_WRITTEN = "uploaded.json.new"

/** The layout of the file. Another number is a layout this build does not know. */
private const val LEDGER_LAYOUT = 1

@Serializable
private data class LedgerFile(
    val layout: Int,
    val account: String,
    val documents: Map<String, String>,
)

/**
 * What the cloud holds of one account, as far as this phone knows: the fingerprint of every
 * document it uploaded there, by path (`cloudPlanFor`).
 *
 * Under the no-backup folder, so neither Android's backup nor an export carries it to another
 * phone: what this phone sent is true of this phone. The account is written with it, and a
 * ledger of another account reads as empty, so signing in to another account uploads
 * everything to that one and removes nothing from the first.
 *
 * Plain `java.io`, so it is tested on the JVM.
 *
 * @param directory where the file goes: see [buildCloudLedger].
 */
class CloudLedger(private val directory: File) {
    private val file = File(directory, LEDGER_FILE)

    /**
     * The fingerprints uploaded to [account], or none: no file yet, a file of another account,
     * of another layout, or one that cannot be read. None is a full answer: the next run then
     * uploads every document again, which writes over what the cloud has with the same content.
     */
    fun read(account: String): Map<String, String> {
        val text =
            try {
                file.readText()
            } catch (unreadable: IOException) {
                return emptyMap()
            }
        val ledger =
            try {
                Json.decodeFromString<LedgerFile>(text)
            } catch (damaged: SerializationException) {
                return emptyMap()
            } catch (damaged: IllegalArgumentException) {
                return emptyMap()
            }
        return if (ledger.layout == LEDGER_LAYOUT && ledger.account == account) {
            ledger.documents
        } else {
            emptyMap()
        }
    }

    /**
     * Stores [documents] as what [account] holds, in place of the ledger before. Written into a
     * second file, forced to disk, and given the real name in one step: a process that dies in
     * the middle leaves the old ledger or the new one.
     *
     * @throws IOException if the file cannot be written.
     */
    fun write(account: String, documents: Map<String, String>) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the folder of the cloud ledger, $directory")
        }
        val beingWritten = File(directory, LEDGER_FILE_BEING_WRITTEN)
        val text = Json.encodeToString(LedgerFile(LEDGER_LAYOUT, account, documents.toSortedMap()))
        FileOutputStream(beingWritten).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(
            beingWritten.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Forgets everything, for an account that was deleted. Nothing to forget is no failure. */
    fun clear() {
        file.delete()
    }
}

/** Builds the ledger. The folder is under `noBackupFilesDir`, out of every backup and export. */
fun buildCloudLedger(context: Context): CloudLedger =
    CloudLedger(File(context.noBackupFilesDir, CLOUD_FOLDER))
