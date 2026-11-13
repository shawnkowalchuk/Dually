package com.shawnkowalchuk.milo.data.cloud

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

private const val INSTALL_FILE = "install.txt"

/**
 * This install's own number, for the backup's owner document (slice 1e, 2026-11-12): made once,
 * at random, and kept in the folder Android never backs up, so that a phone restored from
 * Android's backup is a new install and does not pass for the old one.
 */
class CloudInstall(private val directory: File) {
    private val file = File(directory, INSTALL_FILE)

    /** The number, made and kept the first time it is asked for. */
    @Synchronized
    fun id(): String {
        val kept =
            try {
                file.readText().trim()
            } catch (absent: IOException) {
                ""
            }
        if (kept.isNotEmpty()) return kept
        val made = UUID.randomUUID().toString()
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the folder of the cloud backup, $directory")
        }
        file.writeText(made)
        return made
    }
}

fun buildCloudInstall(context: Context): CloudInstall =
    CloudInstall(File(context.noBackupFilesDir, CLOUD_FOLDER))
