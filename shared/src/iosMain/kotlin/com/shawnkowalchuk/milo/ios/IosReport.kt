package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.report.ENGLISH_REPORT_FILE_PREFIX
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.ReportFormat
import com.shawnkowalchuk.milo.core.report.ReportPage
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.TextMeasure
import com.shawnkowalchuk.milo.core.report.englishCsvWords
import com.shawnkowalchuk.milo.core.report.englishReportBody
import com.shawnkowalchuk.milo.core.report.englishReportSubject
import com.shawnkowalchuk.milo.core.report.englishReportWords
import com.shawnkowalchuk.milo.core.report.layoutReport
import com.shawnkowalchuk.milo.core.report.printedReport
import com.shawnkowalchuk.milo.core.report.reportCsv
import com.shawnkowalchuk.milo.core.report.reportFileName
import com.shawnkowalchuk.milo.core.report.span
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.YearMonth
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.addDays
import com.shawnkowalchuk.milo.core.time.dateOf
import com.shawnkowalchuk.milo.core.time.dateOfEpochDay
import com.shawnkowalchuk.milo.core.time.yearMonth
import com.shawnkowalchuk.milo.core.time.yearMonthOf
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.PointCleanup
import com.shawnkowalchuk.milo.data.point.offersCleanup
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.fromOtherPhone
import com.shawnkowalchuk.milo.data.report.printedTenths
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.keepPointsOf
import com.shawnkowalchuk.milo.data.settings.missingForPdf
import com.shawnkowalchuk.milo.data.settings.missingForSending
import com.shawnkowalchuk.milo.data.settings.setReportListsPersonal
import com.shawnkowalchuk.milo.data.settings.setReportShowsBusinessShare
import com.shawnkowalchuk.milo.data.settings.setReportShowsTripOdometer
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.drivenTrips
import com.shawnkowalchuk.milo.feature.report.mileageReport
import kotlinx.coroutines.flow.first

/**
 * The report for the accountant on the iPhone (phase 4g, second part): a month's Business trips
 * as Android's Report screen makes them (`mileageReport`, the layout, the CSV, the file's name,
 * the email's subject and first lines, all shared), in the words of Android's string resources
 * (EnglishReportWords.kt). Swift measures and draws the text, because the fonts are the app's.
 *
 * Not on the iPhone yet: a date range, the year-end summary, the odometer (the iPhone has no
 * readings until its odometer comes), and the report's switches (Personal trips listed, the
 * business share, each trip's odometer), which keep what the settings file holds.
 */
class IosReport internal constructor(
    private val trips: TripRepository,
    private val sent: SentReportRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
    private val clock: () -> Long,
    private val cleanup: PointCleanup? = null,
) {
    /**
     * What the Report screen shows for a period, before any file is made. A period from Swift is
     * [kind] 0, the month [month] (1 to 12) of [year]; 1, a date range from [firstDay] to
     * [lastDay] (epoch days); 2, the whole of [year], as Android's three choices (2026-11-15 on
     * the iPhone).
     */
    suspend fun month(
        kind: Int,
        year: Int,
        month: Int,
        firstDay: Long,
        lastDay: Long,
    ): ReportMonthText {
        val period = periodOf(kind, year, month, firstDay, lastDay)
        val report = build(period)
        val now = settings.current()
        val localeNow = locale()
        val options = now.reportOptions
        return ReportMonthText(
            title =
                when (period) {
                    is ReportPeriod.Month -> formatMonthAndYear(period.month, localeNow)

                    is ReportPeriod.Range ->
                        if (kind == 2) {
                            "$year"
                        } else {
                            formatShortDay(period.firstDay, localeNow) + " – " +
                                formatShortDay(period.lastDay, localeNow) + ", " +
                                period.lastDay.year
                        }
                },
            tripCount = report.tripCount,
            distance = formatTenths(report.totalTenths, localeNow),
            unit = unitWord(report.unit),
            personalCount = report.personal.tripCount,
            sentBefore = report.revision != null,
            missingForPdf = missingWords(now.missingForPdf()),
            missingForSending = missingWords(now.missingForSending()),
            accountant = now.accountantEmail,
            listPersonal = options.listPersonal,
            businessShare = options.businessShare,
            tripOdometer = options.tripOdometer,
        )
    }

    /** "List Personal trips too", as Android's Report options; kept for every report. */
    suspend fun setListPersonal(on: Boolean) = settings.setReportListsPersonal(on)

    /** "Show the Business use percentage". */
    suspend fun setBusinessShare(on: Boolean) = settings.setReportShowsBusinessShare(on)

    /** "Show each trip's odometer". */
    suspend fun setTripOdometer(on: Boolean) = settings.setReportShowsTripOdometer(on)

    /** Every report recorded as sent, newest first, as Android's "Sent reports" lists them. */
    suspend fun sentList(): List<SentReportText> {
        val localeNow = locale()
        return sent.currentSent().sortedByDescending { it.sentAtMs }.map { report ->
            SentReportText(
                id = report.id,
                period = periodText(report, localeNow),
                line =
                    "Sent on " + formatShortDay(localDateOf(report.sentAtMs, zone()), localeNow) +
                        " · " + report.tripCount + if (report.tripCount == 1) " trip" else " trips",
                distance =
                    formatTenths(report.printedTenths, localeNow) + " " +
                        unitWord(report.distanceUnit),
                revision = report.revision.takeIf { it > 1 }?.let { "Revision $it" },
                fromOtherPhone = report.fromOtherPhone,
            )
        }
    }

    /**
     * The offer to delete the GPS points of [month] of [year], as Android's tile on the Report
     * screen (2026-10-14): a sent month that has ended, whose points were not kept, and that
     * holds some. Null otherwise.
     */
    suspend fun monthPoints(year: Int, month: Int): MonthPointsText? {
        val cleanup = cleanup ?: return null
        val shown = yearMonthOf(year, month)
        val today = localDateOf(clock(), zone())
        if (!offersCleanup(
                shown,
                sent.currentSent(),
                settings.current().pointsKeptMonths,
                today,
            )
        ) {
            return null
        }
        val held = cleanup.heldIn(listOf(shown), zone())
        if (held.points <= 0) return null
        return MonthPointsText(
            title = "GPS points for " + formatMonthName(shown, locale()),
            monthName = formatMonthName(shown, locale()),
            tripCount = held.tripIds.size,
            bytes = held.bytes,
        )
    }

    /** Deletes [month]'s GPS points: how many bytes were freed, or null if they could not be. */
    suspend fun deleteMonthPoints(year: Int, month: Int): Long? {
        val cleanup = cleanup ?: return null
        val held = cleanup.heldIn(listOf(yearMonthOf(year, month)), zone())
        return if (cleanup.delete(held, "on the iPhone's Report screen")) held.bytes else null
    }

    /** Keeps [month]'s GPS points: the offer is not made again. */
    suspend fun keepMonthPoints(year: Int, month: Int) =
        settings.keepPointsOf(yearMonthOf(year, month))

    /**
     * Everything Swift needs to make and send the two files: the PDF's pages, laid out with
     * [measure] (the very fonts that draw them), the CSV's text, the files' names, and the email.
     */
    suspend fun files(
        kind: Int,
        year: Int,
        month: Int,
        firstDay: Long,
        lastDay: Long,
        measure: TextMeasure,
    ): ReportFiles {
        val report = build(periodOf(kind, year, month, firstDay, lastDay))
        val localeNow = locale()
        val format = ReportFormat(localeNow, !usesTwelveHourClock(localeNow))
        val words =
            englishReportWords(report.tripCount, report.personal.tripCount, report.unit, localeNow)
        val pages = layoutReport(printedReport(report, words, format), measure, localeNow)
        fun name(extension: String) = reportFileName(
            period = report.period,
            name = report.sender.name,
            revision = report.revision?.number ?: 0,
            prefix = ENGLISH_REPORT_FILE_PREFIX,
            extension = extension,
        )
        return ReportFiles(
            pages = pages,
            pdfName = name("pdf"),
            csvName = name("csv"),
            // The mark tells a spreadsheet the file is UTF-8, as on Android.
            csv = UTF8_MARK + reportCsv(report, englishCsvWords(report.unit)),
            subject = englishReportSubject(report, localeNow),
            body = englishReportBody(report, localeNow),
            accountant = settings.current().accountantEmail,
        )
    }

    /**
     * The report for [month] was sent: it is recorded as Android records it, so the month counts
     * as submitted and the next report of it is a revision.
     */
    suspend fun recordSent(kind: Int, year: Int, month: Int, firstDay: Long, lastDay: Long) {
        val report = build(periodOf(kind, year, month, firstDay, lastDay))
        val stored =
            sent.recordSent(
                period = report.period,
                sentAtMs = clock(),
                tripCount = report.tripCount,
                // As Android records it: the printed total, a whole number of tenths.
                distanceMetres = metresOfTenths(report.totalTenths, report.unit),
                unit = report.unit,
            )
        eventLog.add(
            clock(),
            EventCategory.REPORT,
            "The report for ${report.period.firstDay} to ${report.period.lastDay} was sent from " +
                "the iPhone (revision ${stored.revision})",
        )
    }

    private fun periodOf(
        kind: Int,
        year: Int,
        month: Int,
        firstDay: Long,
        lastDay: Long,
    ): ReportPeriod = when (kind) {
        1 -> ReportPeriod.Range(dateOfEpochDay(firstDay), dateOfEpochDay(lastDay))
        2 -> ReportPeriod.Range(dateOf(year, 1, 1), dateOf(year, 12, 31))
        else -> ReportPeriod.Month(yearMonthOf(year, month))
    }

    private fun periodText(report: SentReport, locale: Locale): String {
        val first = dateOfEpochDay(report.firstDay)
        val last = dateOfEpochDay(report.lastDay)
        val wholeMonth = first.day == 1 && first.month == last.month && first.year == last.year &&
            last.addDays(1).month != last.month
        return if (wholeMonth) {
            formatMonthAndYear(first.yearMonth(), locale)
        } else {
            formatShortDay(first, locale) + " – " + formatShortDay(last, locale) + ", " + last.year
        }
    }

    private suspend fun build(period: ReportPeriod): MileageReport {
        val zoneNow = zone()
        val span = period.span(zoneNow)
        val inPeriod = trips.observeTripsStartedBetween(span.fromMs, span.untilMs).first()
        val now = settings.current()
        return mileageReport(
            period = period,
            selection = selectForReport(inPeriod, period, zoneNow),
            name = now.reportName.orEmpty(),
            settings = now,
            sent = sent.currentSent(),
            today = localDateOf(clock(), zoneNow),
            zone = zoneNow,
            // The odometer at each trip's start and end, when that option is on (Android's
            // "Show each trip's odometer"): every finished trip, as the reading can lie outside
            // the period.
            truckTrips = drivenTrips(trips.observeFinishedTrips().first()),
        )
    }

    private fun missingWords(missing: List<MissingDetail>): String? = missing.takeIf {
        it.isNotEmpty()
    }
        ?.joinToString(" and ") {
            when (it) {
                MissingDetail.NAME -> "your name"
                MissingDetail.ACCOUNTANT_EMAIL -> "the accountant's email address"
            }
        }
}

/** The Report screen's month, worded. */
data class ReportMonthText(
    /** "October 2026". */
    val title: String,
    val tripCount: Int,
    /** The Business distance: "412.3". */
    val distance: String,
    val unit: String,
    val personalCount: Int,
    /** A report of this month was sent before: the next is a revision. */
    val sentBefore: Boolean,
    /** What a PDF cannot be made without ("your name"), or null. */
    val missingForPdf: String?,
    /** What a report cannot be sent without, or null. */
    val missingForSending: String?,
    val accountant: String?,
    /** The three Report options, as stored. */
    val listPersonal: Boolean,
    val businessShare: Boolean,
    val tripOdometer: Boolean,
)

/** One report recorded as sent: "October 2026", "Sent on Mon, Nov 3 · 12 trips", "412.3 km". */
data class SentReportText(
    val id: Long,
    val period: String,
    val line: String,
    val distance: String,
    /** "Revision 2", or null for the first. */
    val revision: String?,
    /** Recorded as sent on another phone of the online backup account (issue #164). */
    val fromOtherPhone: Boolean = false,
)

/** The offer to delete a sent month's GPS points: Swift writes [bytes] as the phone writes a size. */
data class MonthPointsText(
    val title: String,
    val monthName: String,
    val tripCount: Int,
    val bytes: Long,
)

/** The two files of a report, ready for Swift to write and hand to Mail. */
data class ReportFiles(
    val pages: List<ReportPage>,
    val pdfName: String,
    val csvName: String,
    val csv: String,
    val subject: String,
    val body: String,
    val accountant: String?,
)

/**
 * Tells a spreadsheet that the file is UTF-8, as Android's CSV starts: without it Excel reads
 * "Montréal" as "MontrÃ©al". Written as its code, so no editor can lose the invisible character.
 */
private val UTF8_MARK: String = Char(0xFEFF).toString()
