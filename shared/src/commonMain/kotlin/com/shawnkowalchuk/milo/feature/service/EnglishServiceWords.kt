package com.shawnkowalchuk.milo.feature.service

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText

// The Service screen's words in English, for the iPhone (2026-11-14), with the odometer tile's
// that Service counts by: Android takes them from its string resources. Word for word, with
// Android's escapes; EnglishReportWordsTest holds them to strings.xml.

/** The words' string resources, by their names in strings.xml, as the files hold them. */
val ENGLISH_SERVICE_STRINGS: Map<String, String> = mapOf(
    "nav_service" to "Service",
    "service_title" to "Service",
    "service_reading" to "Reading your service items…",
    "service_none" to
        "Nothing to look after yet. Add an oil change, a tire rotation or anything else, and " +
        "Dually shows when each is due.",
    "service_add" to "Add an item",
    "service_done" to "Done",
    "service_edit" to "Edit",
    "service_delete" to "Delete",
    "service_delete_title" to "Delete this item?",
    "service_delete_text" to "The item and its history are removed. This cannot be undone.",
    "service_history" to "Done before",
    "service_due_in" to "Due in %1\$s",
    "service_due_in_or_on" to "Due in %1\$s or on %2\$s, whichever comes first",
    "service_due_on" to "Due on %1\$s",
    "service_overdue_by" to "Overdue by %1\$s",
    "service_overdue_since" to "Overdue since %1\$s",
    "service_due_no_reading" to
        "Every %1\$s. Type the odometer in Settings so Dually can count it.",
    "service_last_done" to "Last done %1\$s",
    "service_last_done_at" to "Last done %1\$s at %2\$s",
    "service_item_new" to "New item",
    "service_item_edit" to "Edit item",
    "service_item_name" to "Name",
    "service_item_either" to "Fill in one or both. It is due at whichever comes first.",
    "service_item_every_distance" to "Every how many %1\$s",
    "service_item_every_months" to "Every how many months",
    "service_item_last_done" to "Last done",
    "service_item_last_odometer" to "Odometer when last done, in %1\$s",
    "service_save" to "Save",
    "service_preset_oil" to "Oil change",
    "service_preset_tires" to "Tire rotation",
    "service_preset_inspection" to "Inspection",
    "service_preset_brakes" to "Brakes",
    "service_preset_air_filter" to "Air filter",
    "service_done_title" to "%1\$s done",
    "service_done_text" to "Recorded for today. The next one is counted from here.",
    "service_done_odometer" to "Odometer now, in %1\$s",
    "service_done_cost" to "Cost (optional)",
    "service_done_note" to "Note (optional)",
    "service_odometer_wrong" to
        "That is not an odometer reading. Type the number on the dashboard.",
    "service_cost_wrong" to "Type the cost in dollars, such as 59.99.",
    "home_service_done" to "Done",
    "home_service_done_title" to "%1\$s done?",
    "home_service_done_text" to
        "It is recorded for today, at the odometer as Dually has it, and the next one is counted " +
        "from here. To add a cost or a note, press Done on the Service screen instead.",
    "notification_service_line" to "%1\$s: %2\$s",
    "notification_service_text" to "Tap to see it in Service, and press Done once it is done.",
    "settings_odometer_title" to "Odometer",
    "settings_odometer_none" to
        "Type the number on the truck\\'s dashboard once. Dually adds every truck trip to it " +
        "from then on.",
    "settings_odometer_none_miles" to
        "Type the miles on the truck\\'s dashboard once. Dually adds every truck trip to it from " +
        "then on.",
    "settings_odometer_typed_today" to "Your reading of %1\$s, typed today",
    "settings_odometer_no_trips_since" to "Your reading of %1\$s on %2\$s. No truck trip since.",
    "settings_odometer_trips_since" to
        "Your reading of %1\$s on %2\$s, plus %3\$s of truck trips since",
    "settings_odometer_enter" to "Enter reading",
    "settings_odometer_adjust" to "Adjust",
    "settings_odometer_field" to "Reading on the dashboard now",
    "settings_odometer_field_miles" to "Reading on the dashboard now, in miles",
    "settings_odometer_example" to "123456",
    "settings_odometer_refused" to "Type the %1\$s the dashboard shows, such as 123456.",
    "settings_odometer_save" to "Save reading",
    "action_cancel" to "Cancel",
    "settings_could_not_save" to "That change could not be saved. The Log screen says why.",
)

/** The notification's title for several items, as strings.xml's plural holds it. */
val ENGLISH_SERVICE_PLURALS: Map<String, Pair<String, String>> = mapOf(
    "notification_service_title_many" to
        ("%1\$d service item needs you" to "%1\$d service items need you"),
)

/** The string resource [name], with Android's escapes undone. */
fun englishServiceText(name: String): String = unescape(ENGLISH_SERVICE_STRINGS.getValue(name))
    .replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n")

/** The string resource [name] with [args] in its places. */
fun englishServiceFormat(name: String, locale: Locale, vararg args: Any?): String =
    formatText(locale, englishServiceText(name), *args)

/** The notification's title for [count] items. */
fun englishServiceCount(count: Int, locale: Locale): String {
    val (one, other) = ENGLISH_SERVICE_PLURALS.getValue("notification_service_title_many")
    return formatText(locale, unescape(if (count == 1) one else other), count)
}

/**
 * Android's escapes taken out, so that "truck\\'s" reads "truck's" (on the iPhone until
 * 2026-11-14 it did not).
 */
private fun unescape(raw: String): String =
    raw.replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n")
