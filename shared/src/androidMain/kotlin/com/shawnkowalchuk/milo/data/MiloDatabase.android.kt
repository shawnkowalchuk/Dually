package com.shawnkowalchuk.milo.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Builds the main database. It is called once, by the `AppContainer`.
 *
 * There is deliberately no destructive-migration fallback: that option deletes every table when
 * a migration is missing, and these tables hold Shawn's real trips. A missing migration must
 * fail loudly instead. Every version the phone has ever held needs its step in [MILO_MIGRATIONS].
 *
 * That holds for a file Android's backup restored as well: one from an older build is brought
 * up to date by the same steps, and one from a newer build makes this build fail to open it,
 * with the file left as it is for the newer build to read.
 */
fun buildMiloDatabase(context: Context): MiloDatabase = Room
    .databaseBuilder<MiloDatabase>(context = context, name = MILO_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*MILO_MIGRATIONS)
    .build()
