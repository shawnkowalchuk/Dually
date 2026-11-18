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
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.yearMonthOf
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.missingForPdf
import com.shawnkowalchuk.milo.data.settings.missingForSending
import com.shawnkowalchuk.milo.data.trip.TripRepository
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
) {
    /** What the Report screen shows for [month] (1 to 12) of [year], before any file is made. */
    suspend fun month(year: Int, month: Int): ReportMonthText {
        val report = build(year, month)
        val now = settings.current()
        val localeNow = locale()
        return ReportMonthText(
            title = formatMonthAndYear(yearMonthOf(year, month), localeNow),
            tripCount = report.tripCount,
            distance = formatTenths(report.totalTenths, localeNow),
            unit = unitWord(report.unit),
            personalCount = report.personal.tripCount,
            sentBefore = report.revision != null,
            missingForPdf = missingWords(now.missingForPdf()),
            missingForSending = missingWords(now.missingForSending()),
            accountant = now.accountantEmail,
        )
    }

    /**
     * Everything Swift needs to make and send the two files: the PDF's pages, laid out with
     * [measure] (the very fonts that draw them), the CSV's text, the files' names, and the email.
     */
    suspend fun files(year: Int, month: Int, measure: TextMeasure): ReportFiles {
        val report = build(year, month)
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
    suspend fun recordSent(year: Int, month: Int) {
        val report = build(year, month)
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

    private suspend fun build(year: Int, month: Int): MileageReport {
        val zoneNow = zone()
        val period = ReportPeriod.Month(yearMonthOf(year, month))
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
