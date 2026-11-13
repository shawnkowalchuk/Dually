package com.shawnkowalchuk.milo.data.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile

/**
 * Builds the settings store on its DataStore file. Called once, by the `AppContainer`.
 *
 * There is deliberately no corruption handler: the usual one replaces an unreadable file with
 * empty settings, which would drop the truck pairing without a trace. An unreadable file makes
 * every read throw instead, and each reader decides what to do about it.
 */
fun buildSettingsStore(context: Context): SettingsStore = SettingsStore(
    PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE_NAME) },
    ),
)
