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

/** The file of the account, beside the ledger's (`CloudLedger`). */
private const val ACCOUNT_FILE = "account.json"

private const val ACCOUNT_FILE_BEING_WRITTEN = "account.json.new"

/**
 * The account the cloud backup is signed in to (ADR-012).
 *
 * @param id Firebase's id of the account: the name of its folder in the cloud, and what the
 * ledger is kept for. It never changes for one Google account.
 * @param email what the Settings tile shows, so the user sees which Google account it is.
 */
@Serializable
data class CloudAccount(val id: String, val email: String)

/**
 * Which account the backup is signed in to, kept by Dually itself so that the app can tell
 * without starting Firebase: Firebase is started only when there is an account here, or when
 * the user signs in (ADR-012, "Firebase stays silent until backup is on").
 *
 * Under the no-backup folder, like the ledger: neither Android's backup nor an export carries a
 * sign-in to another phone. A new phone signs in again.
 *
 * Plain `java.io`, so it is tested on the JVM.
 *
 * @param directory where the file goes: see [buildCloudAccountFile].
 */
class CloudAccountFile(private val directory: File) {
    private val file = File(directory, ACCOUNT_FILE)

    /** The account, or null: none signed in, or a file that cannot be read. */
    fun read(): CloudAccount? {
        val text =
            try {
                file.readText()
            } catch (absent: IOException) {
                return null
            }
        return try {
            Json.decodeFromString<CloudAccount>(text)
        } catch (damaged: SerializationException) {
            null
        } catch (damaged: IllegalArgumentException) {
            null
        }
    }

    /**
     * Keeps [account] as the one signed in. Written whole into a second file and given the real
     * name in one step, as the ledger is.
     *
     * @throws IOException if the file cannot be written.
     */
    fun write(account: CloudAccount) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the folder of the cloud account, $directory")
        }
        val beingWritten = File(directory, ACCOUNT_FILE_BEING_WRITTEN)
        FileOutputStream(beingWritten).use { output ->
            output.write(Json.encodeToString(account).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(
            beingWritten.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Forgets the account, at a sign-out. Nothing to forget is no failure. */
    fun clear() {
        file.delete()
    }
}

/** Builds the account's file, in the folder of the ledger, under `noBackupFilesDir`. */
fun buildCloudAccountFile(context: Context): CloudAccountFile =
    CloudAccountFile(File(context.noBackupFilesDir, CLOUD_FOLDER))
