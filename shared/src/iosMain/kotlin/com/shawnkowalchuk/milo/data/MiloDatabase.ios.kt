package com.shawnkowalchuk.milo.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Builds the main database on the iPhone (phase 4a): the same tables, version and migrations as
 * Android's ([buildMiloDatabase] in androidMain), in the app's Application Support folder, which
 * iOS backs up and never shows the user. As on Android there is no destructive-migration
 * fallback: a missing migration fails loudly instead of deleting trips.
 */
@OptIn(ExperimentalForeignApi::class)
fun buildMiloDatabase(): MiloDatabase {
    val folder = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path =
        checkNotNull(folder?.path) { "The app's Application Support folder could not be made" }
    return Room
        .databaseBuilder<MiloDatabase>(name = path + "/" + MILO_DATABASE_FILE)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*MILO_MIGRATIONS)
        .build()
}
