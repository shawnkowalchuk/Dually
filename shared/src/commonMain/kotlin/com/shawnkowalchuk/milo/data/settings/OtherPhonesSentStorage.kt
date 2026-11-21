package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.transfer.ExportedSentReport
import com.shawnkowalchuk.milo.data.transfer.toExported
import com.shawnkowalchuk.milo.data.transfer.toSentReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// The reports the account's other phones recorded as sent (issue #164, 2026-11-20): ADR-017's
// "locked once sent, on every phone", and the monthly reminder, need to know a month was sent
// on another phone. Each phone uploads its own list into its folder
// (`phones/{install}/backup/sentReports`); at each sync a phone reads the others' lists
// (`readOtherPhones`) and keeps them here, replaced whole, as the backup holds them.
//
// They are kept apart from this phone's own table on purpose: never uploaded into this phone's
// folder, never exported, and never removed from here. A report is removed on the phone that
// sent it, and the next sync takes the removal to every other phone. Kept in the settings file
// rather than the database, so the database keeps its version (an App Store or Play build of the
// same version with the older database could then not open it).

private val OTHER_PHONES_SENT = stringPreferencesKey("other_phones_sent")

private val OtherPhonesSentJson = Json { ignoreUnknownKeys = true }

private val OtherPhonesSentList = ListSerializer(ExportedSentReport.serializer())

/**
 * The reports the other phones recorded as sent, as this phone last read them, and again each
 * time a sync changes them. Each has a negative id ([SentReport.fromOtherPhone]).
 */
fun SettingsStore.otherPhonesSent(): Flow<List<SentReport>> =
    dataStore.data.map { it.readOtherPhonesSent() }

/**
 * Keeps [reports], the other phones' sent reports as the backup holds them now, in place of
 * those kept before. Each is given a negative id here, in a fixed order, so that it can never
 * be taken for one of this phone's own rows and keeps its id while nothing changes.
 *
 * @return whether anything changed.
 */
suspend fun SettingsStore.keepOtherPhonesSent(reports: List<SentReport>): Boolean {
    val numbered =
        reports.sortedWith(
            compareBy<SentReport>({ it.sentAtMs }, { it.kind }, { it.firstDay }, { it.lastDay })
                .thenBy { it.revision }
                .thenBy { it.id },
        ).mapIndexed { index, report -> report.copy(id = -(index + 1L)) }
    var changed = false
    dataStore.edit { stored ->
        if (stored.readOtherPhonesSent() != numbered) {
            changed = true
            if (numbered.isEmpty()) {
                stored.remove(OTHER_PHONES_SENT)
            } else {
                stored[OTHER_PHONES_SENT] =
                    OtherPhonesSentJson.encodeToString(
                        OtherPhonesSentList,
                        numbered.map { it.toExported() },
                    )
            }
        }
    }
    return changed
}

/** As stored; none where none is, or what is stored cannot be read. */
internal fun Preferences.readOtherPhonesSent(): List<SentReport> {
    val json = this[OTHER_PHONES_SENT] ?: return emptyList()
    return try {
        OtherPhonesSentJson.decodeFromString(OtherPhonesSentList, json).map { it.toSentReport() }
    } catch (unreadable: SerializationException) {
        emptyList()
    } catch (unreadable: IllegalArgumentException) {
        emptyList()
    } catch (unreadable: IllegalStateException) {
        emptyList()
    }
}
