package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shawnkowalchuk.milo.data.appFolderPath
import okio.Path.Companion.toPath

/**
 * Opens the settings file on the iPhone (phase 4b): the same keys and values as Android's, in a
 * file of the same name in [folder], the app's unless a test gives its own.
 */
fun buildSettingsStore(folder: String = appFolderPath()): SettingsStore = SettingsStore(
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { (folder + "/" + SETTINGS_FILE_NAME + ".preferences_pb").toPath() },
    ),
)
