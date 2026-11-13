package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.util.formatTenths
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// The year-end summary (since 2026-10-16, step 3 of Shawn's report options): a report for a
// whole calendar year opens with a page of its months, Business and Personal, before its trips.
// "A whole year" on the Report screen is the date range 1 January to 31 December, which the
// rest of the app already knows: its name, its subject, the list of sent reports, and that it
// marks no month as submitted. Pure Kotlin.

/** Where a summary's figures stand, left of the right edge of the table: Business, Personal. */
private const val BUSINESS_FROM_RIGHT = 110f

/** Room above and below a row's words. */
private const val SUMMARY_ROW_PADDING = 3f

/** The space after the summary's last row. */
private const val SUMMARY_GAP = 12f

/** The table's inset from the content edges, as the days' tables have it. */
private const val SUMMARY_INSET = 8f

/**
 * The calendar year [this] covers whole, 1 January to 31 December, or null for any other
 * period. Only such a report opens with the summary.
 */
val ReportPeriod.wholeYear: Int?
    get() {
        val year = firstDay.year
        val whole =
            this is ReportPeriod.Range &&
                firstDay == java.time.LocalDate.of(year, Month.JANUARY, 1) &&
                lastDay == java.time.LocalDate.of(year, Month.DECEMBER, 31)
        return year.takeIf { whole }
    }

/**
 * One month of the summary, in tenths of the report's unit, added up as the report adds up a
 * day (`sumOfTenths`). Null for a month that had not begun when the report was made.
 */
data class MonthTotal(val month: YearMonth, val businessTenths: Long?, val personalTenths: Long?)

/** The summary as it is printed. */
data class PrintedYearSummary(
    val title: String,
    val businessTitle: String,
    val personalTitle: String,
    val rows: List<Triple<String, String, String>>,
    val totalLabel: String,
    val totalBusiness: String,
    val totalPersonal: String,
)

/**
 * The summary's words and figures: each month's name and its two figures ("–" for a month that
 * had not begun), and the year's totals, which are the sums of the printed figures.
 *
 * @param title a format with one place, for the year: "%1$s, month by month".
 */
fun printedYearSummary(
    months: List<MonthTotal>,
    year: Int,
    title: String,
    words: ReportWords,
    locale: Locale,
    notYet: String,
    totalLabel: String,
): PrintedYearSummary {
    fun figure(tenths: Long?) = tenths?.let { formatTenths(it, locale) } ?: notYet
    return PrintedYearSummary(
        title = String.format(locale, title, year),
        businessTitle = words.business,
        personalTitle = words.personal,
        rows =
            months.map {
                Triple(
                    it.month.month.getDisplayName(TextStyle.FULL, locale),
                    figure(it.businessTenths),
                    figure(it.personalTenths),
                )
            },
        totalLabel = totalLabel,
        totalBusiness = formatTenths(months.sumOf { it.businessTenths ?: 0 }, locale),
        totalPersonal = formatTenths(months.sumOf { it.personalTenths ?: 0 }, locale),
    )
}

/**
 * The summary as a block of the first page: the day bar's look for its title, then a row for
 * each month under a fine rule, its two figures at the right, and the year's total under a rule.
 */
internal fun yearSummaryBlock(summary: PrintedYearSummary): Block = block {
    val cell = ReportTextStyle.CELL
    val figure = ReportTextStyle.CELL_FIGURE
    val left = CONTENT_LEFT + SUMMARY_INSET
    val personalRight = CONTENT_RIGHT - SUMMARY_INSET
    val businessRight = personalRight - BUSINESS_FROM_RIGHT
    val quiet = ReportInk.QUIET
    text(
        summary.businessTitle,
        businessRight,
        ReportTextStyle.COLUMN,
        rightAligned = true,
        ink = quiet,
    )
    text(
        summary.personalTitle,
        personalRight,
        ReportTextStyle.COLUMN,
        rightAligned = true,
        ink = quiet,
    )
    down(ReportTextStyle.COLUMN.leading + SUMMARY_ROW_PADDING)
    for ((month, business, personal) in summary.rows) {
        rule(THIN_RULE, left, personalRight, ReportInk.HAIRLINE)
        down(SUMMARY_ROW_PADDING)
        text(month, left, cell)
        text(business, businessRight, figure, rightAligned = true)
        text(personal, personalRight, figure, rightAligned = true, ink = quiet)
        down(cell.leading + SUMMARY_ROW_PADDING)
    }
    rule(RULE, left, personalRight)
    down(SUMMARY_ROW_PADDING)
    text(summary.totalLabel, left, ReportTextStyle.SUM_LABEL)
    text(summary.totalBusiness, businessRight, ReportTextStyle.SUM, rightAligned = true)
    text(
        summary.totalPersonal,
        personalRight,
        ReportTextStyle.SUM,
        rightAligned = true,
        ink = quiet,
    )
    down(ReportTextStyle.SUM.leading + SUMMARY_GAP)
}
