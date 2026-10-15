package com.shawnkowalchuk.milo.data

import com.shawnkowalchuk.milo.data.report.REPORTS_FOLDER
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.data.settings.SETTINGS_FILE_NAME
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app was renamed on 2026-10-13 (ADR-010), and what it stores was not: a file, a table or a
 * key under a new name would be a new, empty one, and everything under the old name would be
 * lost without a word. So the names of what is stored are held here as they were on that day.
 * A new name may be added; one of these may not be changed or taken away without a migration
 * that carries what it held.
 */
class StoredNamesTest {
    // Gradle runs the unit tests from the module's folder, app/.
    private val sources = File("src/main/kotlin/com/shawnkowalchuk/milo")

    @Test
    fun `the two databases, the settings file and the report folder keep their names`() {
        assertEquals("milo.db", MILO_DATABASE_FILE)
        assertEquals("points.db", POINTS_DATABASE_FILE)
        assertEquals("settings", SETTINGS_FILE_NAME)
        assertEquals("reports", REPORTS_FOLDER)
    }

    @Test
    fun `the export file still calls itself what a file written before the rename does`() {
        assertEquals("milo-export", EXPORT_FORMAT)
    }

    @Test
    fun `the main database still holds the tables it held`() {
        val schema = File("schemas/com.shawnkowalchuk.milo.data.MiloDatabase/7.json").readText()
        val points = File("schemas/com.shawnkowalchuk.milo.data.PointsDatabase/1.json").readText()

        for (table in listOf("trips", "sent_reports", "event_log")) {
            assertTrue(table, schema.contains(""""tableName": "$table""""))
        }
        assertTrue(points.contains(""""tableName": "raw_points""""))
    }

    @Test
    fun `no key of the settings file was renamed or taken away`() {
        val kinds = "string|int|long|boolean|float|double|stringSet"
        val written = Regex("""\b(?:$kinds)PreferencesKey\(\s*"([^"]+)"""")
        val keys =
            sources
                .walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { file -> written.findAll(file.readText()).map { it.groupValues[1] } }
                .toSet()

        val missing = KEYS_ON_THE_DAY_OF_THE_RENAME.filterNot { it in keys }
        assertEquals("These settings would be lost on the next start", emptyList<String>(), missing)
    }

    @Test
    fun `the keys that are not written in one piece keep their names too`() {
        // The setup checklist's confirmations, the moments of Google Play's review card, and
        // the days of the work schedule, whose keys are put together from a day's name.
        assertEquals(
            listOf(
                "confirmed_xiaomi_autostart_at_ms",
                "confirmed_xiaomi_battery_saver_at_ms",
                "confirmed_xiaomi_other_permissions_at_ms",
                "confirmed_xiaomi_recents_lock_at_ms",
            ),
            ConfirmedStep.entries.map { it.key },
        )
        val review = File(sources, "data/settings/ReviewStorage.kt").readText()
        for (key in listOf("review_five_trips", "review_hundred_trips", "review_report_sent")) {
            assertTrue(key, review.contains(""""$key""""))
        }
        val schedule = File(sources, "data/settings/ScheduleStorage.kt").readText()
        for (day in listOf(
            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",
        )) {
            assertTrue(day, schedule.contains(""" to "$day""""))
        }
    }

    @Test
    fun `the folders under the files Android never backs up keep their names`() {
        val folders =
            listOf(
                "data/crash/CrashFileStore.kt" to """CRASH_FOLDER = "crashes"""",
                "data/clock/ClockAnchorStore.kt" to """CLOCK_FOLDER = "clock"""",
                "data/clock/ClockAnchorStore.kt" to """ANCHOR_FILE = "anchor"""",
                "data/sound/OwnSoundStore.kt" to """SOUND_FOLDER = "trip_sound"""",
                "data/sound/OwnSoundStore.kt" to """SOUND_FILE_PREFIX = "own_trip_start_sound_"""",
                "data/transfer/TransferFiles.kt" to """IMPORT_FOLDER = "import"""",
                "data/transfer/TransferFiles.kt" to """SAFETY_FOLDER = "safety_copies"""",
                "data/transfer/BackupNotes.kt" to """NOTES_FOLDER = "backup_notes"""",
            )
        for ((file, line) in folders) {
            assertTrue("$file: $line", File(sources, file).readText().contains(line))
        }
    }

    @Test
    fun `the notification channels keep their ids`() {
        // Android keeps what the user chose for a channel (silent, off) under its id. Under a
        // new id every channel would be a new one, switched back on. Their names, which the
        // user reads in Android's settings, are words and were renamed with the app.
        val ids =
            listOf(
                "platform/trip/TripNotifications.kt" to """TRIP_CHANNEL_ID = "trip_in_progress"""",
                "platform/trip/TripNotifications.kt" to """FAILURE_CHANNEL_ID = "trip_failures"""",
                "platform/trip/TripNotifications.kt" to """DRIVING_CHANNEL_ID = "driving_alert"""",
                "platform/reminder/ReminderNotification.kt" to
                    """REMINDER_CHANNEL_ID = "monthly_reminder"""",
                "platform/nothingrecorded/NothingRecordedNotification.kt" to
                    """NOTHING_RECORDED_CHANNEL_ID = "nothing_recorded"""",
            )
        for ((file, line) in ids) {
            assertTrue("$file: $line", File(sources, file).readText().contains(line))
        }
    }

    private companion object {
        /** Every key the settings file could hold on 2026-10-13, as the code writes it. */
        val KEYS_ON_THE_DAY_OF_THE_RENAME =
            listOf(
                "accountant_email",
                "auto_start_held_off_since_ms",
                "connect_built_in_sound",
                "custom_sound_name",
                "custom_sound_uri",
                "distance_unit",
                "driven_off_trip_id",
                "driving_alert_enabled",
                "driving_off_built_in_sound",
                "driving_off_sound_enabled",
                "driving_off_sound_name",
                "driving_off_sound_uri",
                "first_run_stage",
                "grace_period_seconds",
                "greetings_shown",
                "home_widget_cents_per_km",
                "home_widget_enabled",
                "ignore_trips_outside_schedule",
                "last_driving_alert_at_ms",
                "last_export_at_ms",
                "last_export_with_points",
                "last_process_exit_imported_at_ms",
                "minimum_trip_distance_metres",
                "more_vehicles",
                "nothing_recorded_enabled",
                "nothing_recorded_minute_of_day",
                "nothing_recorded_shown_on_day",
                "odometer_reading_trips",
                "odometer_readings",
                "own_sounds",
                "parked_limit_seconds",
                "parked_place_accuracy_metres",
                "parked_place_at_ms",
                "parked_place_latitude",
                "parked_place_longitude",
                "parked_since_ms",
                "party_held_on_epoch_day",
                "party_owed_since_ms",
                "points_kept_months",
                "reminder_day_of_month",
                "reminder_enabled",
                "reminder_shown_for_month_first_day",
                "reminder_shown_on_day",
                "report_company",
                "report_handed_over_at_ms",
                "report_handed_over_first_day",
                "report_handed_over_kind",
                "report_handed_over_last_day",
                "report_handed_over_tenths",
                "report_handed_over_trip_count",
                "report_handed_over_unit",
                "report_name",
                "report_vehicle",
                "review_last_attempt_at_ms",
                "review_last_launch_at_ms",
                "schedule_\${day.inKeys()}_end_minute",
                "schedule_\${day.inKeys()}_start_minute",
                "schedule_\${day.inKeys()}_tracked",
                "sound_enabled",
                "trip_vehicles_filled",
                "truck_address",
                "truck_association_id",
                "truck_name",
                "whats_new_seen_version",
            )
    }
}
