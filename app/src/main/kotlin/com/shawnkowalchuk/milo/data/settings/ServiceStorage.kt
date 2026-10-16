package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.service.ServiceBook
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// The service book (since 2026-10-16), stored whole as JSON under one key. A book that cannot be
// read is read as an empty one, so that the screen still opens; the stored text is left as it is
// until the next save. Not in an export file yet (FINDINGS_LOG, 2026-10-16, [DEBT]); Android's
// backup carries it with the rest of the settings file.

private val SERVICE_BOOK = stringPreferencesKey("service_book")

private val BookJson = Json { ignoreUnknownKeys = true }

internal fun Preferences.readServiceBook(): ServiceBook {
    val stored = this[SERVICE_BOOK] ?: return ServiceBook()
    return try {
        BookJson.decodeFromString(ServiceBook.serializer(), stored)
    } catch (unreadable: SerializationException) {
        ServiceBook()
    } catch (unreadable: IllegalArgumentException) {
        ServiceBook()
    }
}

/** Changes the stored book by [change], in one write: what it is given is the book as stored. */
suspend fun SettingsStore.changeServiceBook(change: (ServiceBook) -> ServiceBook) {
    dataStore.edit { stored ->
        stored[SERVICE_BOOK] =
            BookJson.encodeToString(ServiceBook.serializer(), change(stored.readServiceBook()))
    }
}
