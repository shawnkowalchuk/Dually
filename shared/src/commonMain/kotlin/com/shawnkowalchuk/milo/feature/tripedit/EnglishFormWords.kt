package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.util.DistanceUnit

// The trip form's words in English, for the iPhone (2026-11-13): Android takes them from its
// string resources (TripEditTexts in the app). Word for word, with Android's escapes; the test
// that holds the report's words to strings.xml (EnglishReportWordsTest) holds these too.

/** The form's string resources, by their names in strings.xml, as the file holds them. */
val ENGLISH_FORM_STRINGS: Map<String, String> = mapOf(
    "trip_edit_ends_next_day" to "Ended the next day",
    "trip_edit_problem_start_missing" to "Choose when the trip started.",
    "trip_edit_problem_end_missing" to "Choose when the trip ended.",
    "trip_edit_problem_end_not_after_start" to
        "The end must be later than the start. If the trip ran past midnight, switch on " +
        "\\\"%1\$s\\\". Nothing was saved.",
    "trip_edit_problem_future" to
        "A trip cannot start or end in the future. Check the date and the times. Nothing was " +
        "saved.",
    "trip_edit_problem_overlaps_recording" to
        "A trip is being recorded right now, and this one would run into it. Make it end " +
        "before the recording started, or end the recording first. Nothing was saved.",
    "trip_edit_problem_distance_missing" to "Type the distance in %1\$s.",
    "trip_edit_problem_distance_not_a_number" to
        "The distance must be a number, such as 12.4. Nothing was saved.",
    "trip_edit_problem_distance_negative" to
        "The distance cannot be less than zero. Nothing was saved.",
    "trip_edit_problem_distance_too_long" to
        "That is more than %1\$s for one trip. Check the distance. Nothing was saved.",
    "trip_edit_problem_distance_too_fast" to
        "That distance is too far for the time between the start and the end: more than " +
        "%1\$s on average. Check the distance and the times. Nothing was saved.",
    "speed_kmh" to "%1\$s km/h",
    "speed_mph" to "%1\$s mph",
    "trip_edit_title" to "Edit trip",
    "trip_add_title" to "Add a missed trip",
    "trip_edit_not_editable" to
        "This trip cannot be changed here. It was deleted, or it is not a finished trip. Go " +
        "back to Trips.",
    "trip_edit_added_intro" to
        "You added this trip by hand, so Dually has no recording of it. Change what is wrong, " +
        "then press Save.",
    "trip_add_intro" to
        "For a trip Dually did not record. It is saved as a finished trip, counted like any " +
        "other, and marked \\\"added by hand\\\".",
    "trip_edit_when_title" to "When",
    "trip_edit_start_label" to "Start",
    "trip_edit_end_label" to "End",
    "trip_edit_time_not_set" to "Choose",
    "trip_edit_ends_on" to "The end time is on %1\$s.",
    "trip_edit_where_title" to "Where",
    "trip_edit_from" to "From",
    "trip_edit_to" to "To",
    "trip_edit_distance_label" to "Distance, %1\$s",
    "trip_edit_saved_as" to "Saved as",
    "trip_business" to "Business",
    "trip_personal" to "Personal",
    "trip_edit_kind_by_you" to "You chose this. The work hours no longer decide it.",
    "trip_edit_kind_as_saved" to "Dually chose this from your work hours.",
    "trip_edit_kind_by_schedule" to "Dually chose this from your work hours and the start time.",
    "trip_edit_kind_needs_start" to
        "Choose the start time first. Dually then picks one from your work hours, and you can " +
        "change it.",
    "trip_edit_kind_not_known" to "Dually cannot tell which it is. Choose one yourself.",
    "trip_edit_save" to "Save",
    "trip_add_save" to "Add this trip",
    "trip_edit_save_failed" to "That could not be saved. The Log screen says why.",
    "trip_edit_recorded_title" to "What Dually recorded",
    "trip_edit_recorded_line" to "%1\$s · %2\$s",
    "trip_edit_recorded_line_with_day" to "%1\$s · %2\$s · %3\$s",
    "trip_edit_recorded_trip" to "%1\$s\\n%2\$s · %3\$s",
    "trip_edit_restore_title" to "Restore recorded values?",
    "trip_edit_restore_text" to
        "%1\$s\\n\\nThe times and the distance go back to these, and what you typed in their " +
        "place is dropped. Any address you typed is removed and looked up again. The Log screen " +
        "keeps a line that says what was changed.",
    "trip_edit_restore_confirm" to "Restore",
    "leave_unsaved_title" to "Leave without saving?",
    "leave_unsaved_text" to
        "What you changed on this screen has not been saved. If you leave now, it is lost and " +
        "nothing is stored.",
    "leave_unsaved_discard" to "Discard",
    "leave_unsaved_keep" to "Keep editing",
)

/** The string resource [name] as the screen shows it: Android's escapes read. */
fun englishFormText(name: String): String = ENGLISH_FORM_STRINGS.getValue(name)
    .replace("\\\"", "\"")
    .replace("\\'", "'")
    .replace("\\n", "\n")

private fun text(name: String): String = englishFormText(name)

/** The line under Business and Personal: who chose the one the form shows. */
fun englishKindWords(source: KindSource): String = englishFormText(
    when (source) {
        KindSource.BY_YOU -> "trip_edit_kind_by_you"
        KindSource.BY_SCHEDULE -> "trip_edit_kind_by_schedule"
        KindSource.AS_SAVED -> "trip_edit_kind_as_saved"
        KindSource.NEEDS_START -> "trip_edit_kind_needs_start"
        KindSource.NOT_KNOWN -> "trip_edit_kind_not_known"
    },
)

/** What a problem of the form says, as Android's trip edit screen says it. */
fun englishProblemWords(problem: FormProblem, unit: DistanceUnit, locale: Locale): String {
    val miles = unit == DistanceUnit.MILES
    fun format(name: String, vararg args: Any?) = formatText(locale, text(name), *args)
    return when (problem) {
        FormProblem.START_MISSING -> text("trip_edit_problem_start_missing")

        FormProblem.END_MISSING -> text("trip_edit_problem_end_missing")

        FormProblem.END_NOT_AFTER_START ->
            format("trip_edit_problem_end_not_after_start", text("trip_edit_ends_next_day"))

        FormProblem.IN_THE_FUTURE -> text("trip_edit_problem_future")

        FormProblem.OVERLAPS_RECORDING -> text("trip_edit_problem_overlaps_recording")

        FormProblem.DISTANCE_MISSING ->
            format("trip_edit_problem_distance_missing", if (miles) "miles" else "kilometres")

        FormProblem.DISTANCE_NOT_A_NUMBER -> text("trip_edit_problem_distance_not_a_number")

        FormProblem.DISTANCE_NEGATIVE -> text("trip_edit_problem_distance_negative")

        FormProblem.DISTANCE_TOO_LONG ->
            format(
                "trip_edit_problem_distance_too_long",
                "${maxTripDistanceShown(unit)} ${if (miles) "mi" else "km"}",
            )

        FormProblem.DISTANCE_TOO_FAST ->
            format(
                "trip_edit_problem_distance_too_fast",
                format(if (miles) "speed_mph" else "speed_kmh", maxSpeedShown(unit).toString()),
            )
    }
}
