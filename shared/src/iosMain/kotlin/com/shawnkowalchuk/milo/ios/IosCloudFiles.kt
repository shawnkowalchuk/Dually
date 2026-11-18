package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.io.IOException
import com.shawnkowalchuk.milo.data.appFolderPath
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudLedgerStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID

private const val ACCOUNT_FILE = "account.json"
private const val LEDGER_FILE = "uploaded.json"
private const val INSTALL_FILE = "install.txt"

/** The layout of the ledger's file, as Android's (`CloudLedger`). */
private const val LEDGER_LAYOUT = 1

@Serializable
private data class LedgerFile(
    val layout: Int,
    val account: String,
    val documents: Map<String, String>,
)

private val cloudJson = Json { ignoreUnknownKeys = true }

/**
 * The online backup's own files on the iPhone (phase 4h, 2026-11-14), Android's three in
 * `no_backup/cloud/`: the account signed in (`CloudAccountFile`), what was uploaded to it
 * (`CloudLedger`) and this install's own number (`CloudInstall`), with the same contents.
 *
 * In a folder of their own that the iPhone's iCloud backup leaves out, as Android's backup
 * leaves out `no_backup/`: a new iPhone restored from iCloud is a new install, signs in again,
 * and does not pass for the old one in the backup's owner document. Each file is written whole
 * into a second file and given the real name in one step.
 *
 * @param folder where the files go; the app's own `cloud` folder unless a test says otherwise.
 */
internal class IosCloudFiles(private val folder: String = appFolderPath() + "/cloud") :
    CloudLedgerStore {
    private val files = FileSystem.SYSTEM

    /** The account signed in, or null: none, or a file that cannot be read. */
    fun account(): CloudAccount? = readText(ACCOUNT_FILE)?.let { text ->
        try {
            cloudJson.decodeFromString(CloudAccount.serializer(), text)
        } catch (damaged: SerializationException) {
            null
        } catch (damaged: IllegalArgumentException) {
            null
        }
    }

    /**
     * Keeps [account] as the one signed in.
     *
     * @throws IOException if the file cannot be written.
     */
    fun keep(account: CloudAccount) =
        writeText(ACCOUNT_FILE, cloudJson.encodeToString(CloudAccount.serializer(), account))

    /** Forgets the account, at a sign-out or a deletion. */
    fun forgetAccount() = delete(ACCOUNT_FILE)

    override fun read(account: String): Map<String, String> {
        val text = readText(LEDGER_FILE) ?: return emptyMap()
        val ledger =
            try {
                cloudJson.decodeFromString(LedgerFile.serializer(), text)
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

    override fun write(account: String, documents: Map<String, String>) = writeText(
        LEDGER_FILE,
        cloudJson.encodeToString(
            LedgerFile.serializer(),
            LedgerFile(LEDGER_LAYOUT, account, documents.toList().sortedBy { it.first }.toMap()),
        ),
    )

    /** Forgets what was uploaded, for an account that was deleted. */
    fun forgetLedger() = delete(LEDGER_FILE)

    /**
     * This install's own number, for the backup's owner document: made once, at random.
     *
     * @throws IOException if it cannot be kept.
     */
    fun install(): String {
        readText(INSTALL_FILE)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val made = NSUUID().UUIDString().lowercase()
        writeText(INSTALL_FILE, made)
        return made
    }

    private fun path(name: String): Path = "$folder/$name".toPath()

    private fun readText(name: String): String? = try {
        files.read(path(name)) { readUtf8() }
    } catch (absent: okio.IOException) {
        null
    }

    private fun writeText(name: String, text: String) {
        try {
            makeFolder()
            val beingWritten = path("$name.new")
            files.write(beingWritten) { writeUtf8(text) }
            files.atomicMove(beingWritten, path(name))
        } catch (failed: okio.IOException) {
            throw IOException("The backup's file $name could not be written: ${failed.message}")
        }
    }

    private fun delete(name: String) {
        try {
            files.delete(path(name))
        } catch (failed: okio.IOException) {
            // Nothing there, or nothing to be done: the file is not read as anyone's.
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun makeFolder() {
        if (files.exists(folder.toPath())) return
        files.createDirectories(folder.toPath())
        NSURL.fileURLWithPath(folder).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    }
}
