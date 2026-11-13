package com.shawnkowalchuk.milo.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Builds the database of raw GPS points on the iPhone (phase 4b), beside the main one, as
 * Android's [buildPointsDatabase] does.
 */
fun buildPointsDatabase(): PointsDatabase = Room
    .databaseBuilder<PointsDatabase>(name = appFolderPath() + "/" + POINTS_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
