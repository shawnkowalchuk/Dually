package com.shawnkowalchuk.milo.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Builds the raw points database. Called once, by the `AppContainer`. No destructive fallback. */
fun buildPointsDatabase(context: Context): PointsDatabase = Room
    .databaseBuilder<PointsDatabase>(context = context, name = POINTS_DATABASE_FILE)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
