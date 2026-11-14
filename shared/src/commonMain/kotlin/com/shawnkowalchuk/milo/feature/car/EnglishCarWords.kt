package com.shawnkowalchuk.milo.feature.car

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText

// The car screen's words in English, for CarPlay (2026-11-14): Android Auto's screen takes them
// from its string resources. Word for word, with Android's escapes; EnglishReportWordsTest holds
// them to strings.xml.

/** The words' string resources, by their names in strings.xml, as the files hold them. */
val ENGLISH_CAR_STRINGS: Map<String, String> = mapOf(
    "app_name" to "Dually",
    "car_row_this_trip" to "This trip",
    "car_row_business" to "Business",
    "car_joined" to "%1\$s · %2\$s",
    "car_status_recording" to "Recording",
    "car_status_waiting_for_truck" to "Truck disconnected. Waiting for it to reconnect",
    "car_status_parked_waiting" to "Truck parked. A trip starts when it moves",
    "car_status_not_recording" to "Not recording",
    "car_trip_minutes" to "%1\$s · %2\$d min",
    "car_trip_hours_minutes" to "%1\$s · %2\$d h %3\$d min",
    "car_business_today" to "Today %1\$s",
    "car_business_month" to "%1\$s %2\$s",
    "car_business_month_dollars" to "%1\$s %2\$s, %3\$s",
    "car_business_unknown" to "Not available",
    "car_odometer" to "Odo %1\$s",
    "car_make_personal" to "Make Personal",
    "car_make_business" to "Make Business",
    "home_start_trip" to "Start trip",
    "home_end_trip" to "End trip",
    "trip_business" to "Business",
    "trip_personal" to "Personal",
    "distance_km" to "%1\$s km",
    "distance_mi" to "%1\$s mi",
)

/** The string resource [name], with Android's escapes undone. */
fun englishCarText(name: String): String = ENGLISH_CAR_STRINGS.getValue(name)
    .replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n")

/** The string resource [name] with [args] in its places. */
fun englishCarFormat(name: String, locale: Locale, vararg args: Any?): String =
    formatText(locale, englishCarText(name), *args)
