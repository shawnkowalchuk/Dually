package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.feature.report.PeriodKind
import com.shawnkowalchuk.milo.feature.report.canStepForward
import com.shawnkowalchuk.milo.feature.report.openingChoice
import com.shawnkowalchuk.milo.feature.report.steppedMonth
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The year-end summary (2026-10-16): the period, its months, and its page. */
class ReportYearSummaryTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun `a whole year is 1 January to 31 December, and steps a year at a time`() {
        val year = openingChoice(YearMonth.of(2026, 10), today).copy(kind = PeriodKind.YEAR)

        assertEquals(
            ReportPeriod.Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            year.period,
        )
        assertEquals(2026, year.period.wholeYear)
        assertFalse(year.canStepForward(today))
        val last = year.steppedMonth(-1, today)
        assertEquals(PeriodKind.YEAR, last.kind)
        assertEquals(2025, last.period.wholeYear)
        assertTrue(last.canStepForward(today))
    }

    @Test
    fun `only a whole calendar year is one`() {
        assertNull(ReportPeriod.Month(YearMonth.of(2026, 1)).wholeYear)
        assertNull(
            ReportPeriod.Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 30)).wholeYear,
        )
        assertNull(
            ReportPeriod.Range(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 12, 31)).wholeYear,
        )
    }

    @Test
    fun `the summary prints each month, a dash for one not begun, and the sums of its figures`() {
        val months =
            (1..12).map { month ->
                val ym = YearMonth.of(2026, month)
                when {
                    month > 10 -> MonthTotal(ym, null, null)
                    else -> MonthTotal(ym, month * 100L, 15L)
                }
            }

        val printed =
            printedYearSummary(
                months,
                2026,
                "%1\$s, month by month",
                WORDS,
                Locale.CANADA,
                "–",
                "The year",
            )

        assertEquals("2026, month by month", printed.title)
        assertEquals(Triple("January", "10.0", "1.5"), printed.rows.first())
        assertEquals(Triple("December", "–", "–"), printed.rows.last())
        // 100 to 1,000 tenths over ten months: 5,500 tenths.
        assertEquals("550.0", printed.totalBusiness)
        assertEquals("15.0", printed.totalPersonal)
    }

    @Test
    fun `a whole year's report opens with the summary, and its trips start on the next page`() {
        val period = ReportPeriod.Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        val months = (1..12).map { MonthTotal(YearMonth.of(2026, it), 0L, 0L) }
        val withSummary =
            report(listOf(trip(LocalDate.of(2026, 3, 2), "08:00")), period = period)
                .copy(months = months)
        val words = WORDS.copy(
            yearSummaryTitle = "%1\$s, month by month",
            yearSummaryTotal = "The year",
            notYet = "–",
        )

        val pages = layoutReport(printedReport(withSummary, words, FORMAT), MEASURE, Locale.CANADA)

        assertEquals(2, pages.size)
        assertTrue(pages[0].texts.any { it.text == "2026, month by month" })
        assertFalse(pages[0].texts.any { it.text == "08:00" })
        assertTrue(pages[1].texts.any { it.text == "08:00" })
    }
}
