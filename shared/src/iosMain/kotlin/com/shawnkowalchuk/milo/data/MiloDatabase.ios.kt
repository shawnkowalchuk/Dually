package com.shawnkowalchuk.milo.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Builds the main database on the iPhone (phase 4a): the same tables, version and migrations as
 * Android's ([buildMiloDatabase] in androidMain), in [folder], the app's ([appFolderPath]). As on
 * Android there is no destructive-migration fallback: a missing migration fails loudly instead
 * of deleting trips.
 */
fun buildMiloDatabase(folder: String = appFolderPath()): MiloDatabase = Room
    .databaseBuilder<MiloDatabase>(name = folder + "/" + MILO_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*MILO_MIGRATIONS)
    .build()
