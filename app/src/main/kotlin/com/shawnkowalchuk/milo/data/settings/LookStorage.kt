package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.util.AppLook

// The look of the screens and the widget (since 2026-11-12): one value in the settings file.
// Nothing is stored until Shawn picks one, and Dark is read until then, so an update changes
// nothing that he sees.

private val LOOK = stringPreferencesKey("look")

// What is written to the file. Spelled here and never taken from a constant's own name.
private const val WORD_DARK = "dark"
private const val WORD_LIGHT = "light"

/** The word a look is stored under. */
internal val AppLook.storedWord: String
    get() = when (this) {
        AppLook.DARK -> WORD_DARK
        AppLook.LIGHT -> WORD_LIGHT
    }

/** The look [word] stands for, or null for a word Dually does not write. */
internal fun appLookOf(word: String?): AppLook? = when (word) {
    WORD_DARK -> AppLook.DARK
    WORD_LIGHT -> AppLook.LIGHT
    else -> null
}

/** The look picked in Settings, or Dark until one is, or for a word Dually could not have written. */
internal fun Preferences.readLook(): AppLook = appLookOf(this[LOOK]) ?: AppLook.DARK

/** Stores the look. The screens and the widget follow the settings, so it shows at once. */
suspend fun SettingsStore.setLook(look: AppLook) {
    dataStore.edit { it[LOOK] = look.storedWord }
}
