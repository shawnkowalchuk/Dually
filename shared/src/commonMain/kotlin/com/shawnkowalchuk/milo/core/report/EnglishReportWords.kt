package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatTenths

// The report's words in English, for the iPhone (phase 4g): Android takes the same words from its
// string resources (ReportTexts in the app). The texts below are those resources, word for word
// and with Android's escapes, and a test on Android's side (EnglishReportWordsTest) fails the
// build the day one of them differs from app/src/main/res/values/. The report is English only on
// both phones.

/** The report's string resources, by their names in strings.xml, as the file holds them. */
val ENGLISH_REPORT_STRINGS: Map<String, String> = mapOf(
    "app_name" to "Dually",
    "distance_km" to "%1\$s km",
    "distance_mi" to "%1\$s mi",
    "unit_km" to "km",
    "unit_mi" to "mi",
    "unit_km_name" to "kilometres",
    "unit_mi_name" to "miles",
    "report_csv_added" to "added",
    "report_csv_by_hand" to "By hand",
    "report_csv_category" to "Category",
    "report_csv_date" to "Date",
    "report_csv_edited" to "edited",
    "report_csv_label" to "Purpose",
    "report_csv_odometer_end" to "Odometer end",
    "report_csv_odometer_start" to "Odometer start",
    "report_csv_vehicle" to "Vehicle",
    "report_email_subject" to "Mileage - %1\$s - %2\$s",
    "report_email_subject_revision" to "%1\$s - Revision %2\$d",
    "report_file_prefix" to "Mileage",
    "report_pdf_business" to "Business",
    "report_pdf_business_only" to "Business trips only. Distances are in %1\$s.",
    "report_pdf_business_share" to
        "Business use: %1\$d%% of the distance driven in this period, Business and Personal " +
        "together.",
    "report_pdf_column_end" to "End",
    "report_pdf_column_from" to "From",
    "report_pdf_column_start" to "Start",
    "report_pdf_column_to" to "To",
    "report_pdf_company" to "Company",
    "report_pdf_day_continued" to "%1\$s (continued)",
    "report_pdf_footer" to "Dually Trip Tracker · %1\$s · %2\$s",
    "report_pdf_generated_on" to "Generated %1\$s",
    "report_pdf_legend" to
        "* This trip was added by hand, or changed by hand after it was recorded.",
    "report_pdf_name" to "Name",
    "report_pdf_no_address" to "No address recorded",
    "report_pdf_no_trips" to "No business trips in this period.",
    "report_pdf_not_yet" to "–",
    "report_pdf_odometer_estimated" to "%1\$s km est.",
    "report_pdf_odometer_estimated_mi" to "%1\$s mi est.",
    "report_pdf_odometer_note" to
        "est.: worked out by Dually from a reading of the dashboard and the truck trips " +
        "recorded since, not read off the dashboard on that day.",
    "report_pdf_odometer_of_vehicle" to "%1\$s · %2\$s",
    "report_pdf_odometer_on" to "Odometer, %1\$s",
    "report_pdf_page" to "Page %1\$d of %2\$d",
    "report_pdf_personal" to "Personal",
    "report_pdf_personal_listed" to
        "Business and Personal trips. Personal trips are in grey and in no subtotal or total. " +
        "Distances are in %1\$s.",
    "report_pdf_personal_trip" to "Personal",
    "report_pdf_revision" to "Revision %1\$d. It replaces the report sent on %2\$s.",
    "report_pdf_signature" to "Signature",
    "report_pdf_signature_date" to "Date",
    "report_pdf_subtotal" to "Subtotal",
    "report_pdf_title" to "Mileage report",
    "report_pdf_total" to "Total business %2\$s for %1\$s",
    "report_pdf_trip_odometer" to "Odometer %1\$s → %2\$s %3\$s",
    "report_pdf_trip_odometer_estimated" to "Odometer %1\$s → %2\$s %3\$s est.",
    "report_pdf_vehicle" to "Vehicle",
    "report_pdf_year_title" to "%1\$s, month by month",
    "report_pdf_year_total" to "The year",
    "report_period_range_words" to "%1\$s to %2\$s",
)

/** The report's plurals, by name, in English: the "one" and the "other" form. */
val ENGLISH_REPORT_PLURALS: Map<String, Pair<String, String>> = mapOf(
    "report_email_body" to
        Pair(
            "Attached is my mileage report for %1\$s: %2\$s in %3\$d business trip.\\n\\n%4\$s",
            "Attached is my mileage report for %1\$s: %2\$s in %3\$d business trips.\\n\\n%4\$s",
        ),
    "report_pdf_personal_trip_count" to Pair("%d personal trip", "%d personal trips"),
    "report_pdf_trip_count" to Pair("%d business trip", "%d business trips"),
)

/** A string as Android's getString(id) gives it: the file's text, with its escapes read. */
private fun text(name: String): String = unescape(ENGLISH_REPORT_STRINGS.getValue(name))

private fun plural(name: String, count: Int): String {
    val (one, other) = ENGLISH_REPORT_PLURALS.getValue(name)
    return unescape(if (count == 1) one else other)
}

private fun unescape(raw: String): String =
    raw.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")

private fun unitName(unit: DistanceUnit) =
    text(if (unit == DistanceUnit.MILES) "unit_mi_name" else "unit_km_name")

private fun unitShort(unit: DistanceUnit) = text(
    if (unit ==
        DistanceUnit.MILES
    ) {
        "unit_mi"
    } else {
        "unit_km"
    },
)

private fun distanceWords(unit: DistanceUnit) =
    text(if (unit == DistanceUnit.MILES) "distance_mi" else "distance_km")

/** The place a format keeps for the period, or for a trip's two odometer figures. */
private const val FIRST_PLACE = "%1\$s"
private const val SECOND_PLACE = "%2\$s"

/** The words of the PDF, as Android's ReportTexts.words builds them. */
fun englishReportWords(
    tripCount: Int,
    personalTripCount: Int,
    unit: DistanceUnit,
    locale: Locale,
): ReportWords {
    fun format(name: String, vararg args: Any?) = formatText(locale, text(name), *args)
    return ReportWords(
        appName = text("app_name"),
        title = text("report_pdf_title"),
        name = text("report_pdf_name"),
        company = text("report_pdf_company"),
        vehicle = text("report_pdf_vehicle"),
        generatedOn = text("report_pdf_generated_on"),
        businessOnly = format("report_pdf_business_only", unitName(unit)),
        revisionNote = text("report_pdf_revision"),
        periodRange = text("report_period_range_words"),
        business = text("report_pdf_business"),
        personal = text("report_pdf_personal"),
        columnStart = text("report_pdf_column_start"),
        columnEnd = text("report_pdf_column_end"),
        columnFrom = text("report_pdf_column_from"),
        columnTo = text("report_pdf_column_to"),
        columnKm = unitShort(unit),
        dayContinued = text("report_pdf_day_continued"),
        subtotal = text("report_pdf_subtotal"),
        total = format("report_pdf_total", FIRST_PLACE, unitName(unit)),
        tripCount = formatText(locale, plural("report_pdf_trip_count", tripCount), tripCount),
        personalTripCount =
            formatText(
                locale,
                plural("report_pdf_personal_trip_count", personalTripCount),
                personalTripCount,
            ),
        noTrips = text("report_pdf_no_trips"),
        noAddress = text("report_pdf_no_address"),
        legend = text("report_pdf_legend"),
        signature = text("report_pdf_signature"),
        signatureDate = text("report_pdf_signature_date"),
        odometerOn = text("report_pdf_odometer_on"),
        odometerKm = distanceWords(unit),
        odometerEstimated =
            text(
                if (unit == DistanceUnit.MILES) {
                    "report_pdf_odometer_estimated_mi"
                } else {
                    "report_pdf_odometer_estimated"
                },
            ),
        odometerNote = text("report_pdf_odometer_note"),
        odometerOfVehicle = text("report_pdf_odometer_of_vehicle"),
        footer = text("report_pdf_footer"),
        page = text("report_pdf_page"),
        personalListed = format("report_pdf_personal_listed", unitName(unit)),
        personalTrip = text("report_pdf_personal_trip"),
        businessShare = text("report_pdf_business_share"),
        yearSummaryTitle = text("report_pdf_year_title"),
        yearSummaryTotal = text("report_pdf_year_total"),
        notYet = text("report_pdf_not_yet"),
        tripOdometer = format(
            "report_pdf_trip_odometer",
            FIRST_PLACE,
            SECOND_PLACE,
            unitShort(unit),
        ),
        tripOdometerEstimated =
            format(
                "report_pdf_trip_odometer_estimated",
                FIRST_PLACE,
                SECOND_PLACE,
                unitShort(unit),
            ),
    )
}

/** The CSV's column titles and words, as Android's ReportTexts.csvWords. */
fun englishCsvWords(unit: DistanceUnit): CsvWords = CsvWords(
    date = text("report_csv_date"),
    start = text("report_pdf_column_start"),
    end = text("report_pdf_column_end"),
    from = text("report_pdf_column_from"),
    to = text("report_pdf_column_to"),
    label = text("report_csv_label"),
    vehicle = text("report_csv_vehicle"),
    km = unitShort(unit),
    byHand = text("report_csv_by_hand"),
    category = text("report_csv_category"),
    business = text("report_pdf_business"),
    personal = text("report_pdf_personal"),
    odometerStart = text("report_csv_odometer_start"),
    odometerEnd = text("report_csv_odometer_end"),
    added = text("report_csv_added"),
    edited = text("report_csv_edited"),
)

/** The first word of a report file's name: "Mileage". */
val ENGLISH_REPORT_FILE_PREFIX: String get() = text("report_file_prefix")

/** The email's subject, as Android's ReportTexts.subject. */
fun englishReportSubject(report: MileageReport, locale: Locale): String = reportSubject(
    period = report.period,
    name = report.sender.name,
    revision = report.revision?.number ?: 0,
    words =
        SubjectWords(
            subject = text("report_email_subject"),
            revision = text("report_email_subject_revision"),
            periodRange = text("report_period_range_words"),
        ),
    locale = locale,
)

/** The few lines the email starts with, as Android's ReportTexts.body. */
fun englishReportBody(report: MileageReport, locale: Locale): String = formatText(
    locale,
    plural("report_email_body", report.tripCount),
    periodInWords(report.period, locale, text("report_period_range_words")),
    formatText(locale, distanceWords(report.unit), formatTenths(report.totalTenths, locale)),
    report.tripCount,
    report.sender.name,
)
