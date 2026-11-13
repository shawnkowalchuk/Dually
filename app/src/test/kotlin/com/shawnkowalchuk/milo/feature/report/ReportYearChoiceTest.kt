package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.wholeYear
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Report screen's choice of a whole year (2026-10-16). The year-end summary's own tests are
 * with the report's rules, in :shared (ReportYearSummaryTest).
 */
class ReportYearChoiceTest {
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
}
