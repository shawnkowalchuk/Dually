package com.shawnkowalchuk.milo.core.report

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report's options (2026-10-16): Personal trips listed beside the Business ones, and the
 * Business share of the period's driving. Listing Personal trips must change no total.
 */
class ReportOptionsTest {
    private val day = LocalDate.of(2026, 10, 5)

    private val business = trip(day, "08:14", metres = 12_340.0)
    private val lunch =
        trip(day, "12:05", metres = 4_960.0, from = "Shop", to = "Diner").copy(personal = true)
    private val laterBusiness = trip(day, "15:30", metres = 8_200.0)

    private fun withPersonal(listsPersonal: Boolean = true, share: Boolean = false) = report(
        listOf(business, lunch, laterBusiness),
        personal = PersonalDriving(tripCount = 1, tenths = 50),
    ).copy(listsPersonal = listsPersonal, showsBusinessShare = share)

    @Test
    fun `a listed Personal trip is in no subtotal, no total and no trip count`() {
        val report = withPersonal()

        assertEquals(3, report.days.single().trips.size)
        assertEquals(205L, report.days.single().tenths(report.unit))
        assertEquals(205L, report.totalTenths)
        assertEquals(2, report.tripCount)
    }

    @Test
    fun `it is printed in time order, marked Personal, and the note says what is listed`() {
        val words =
            WORDS.copy(personalListed = "Business and Personal trips.", personalTrip = "Personal")

        val printed = printedReport(withPersonal(), words, FORMAT)

        val rows = printed.days.single().rows
        assertEquals(listOf("08:14", "12:05", "15:30"), rows.map { it.start })
        assertEquals(listOf(false, true, false), rows.map { it.personal })
        assertEquals("Personal", rows[1].detail)
        assertEquals("20.5", printed.days.single().subtotalKm)
        assertTrue("Business and Personal trips." in printed.notes)
        assertFalse(WORDS.businessOnly in printed.notes)
    }

    @Test
    fun `with the switch off the report is as it was`() {
        val printed = printedReport(report(listOf(business, laterBusiness)), WORDS, FORMAT)

        assertEquals(WORDS.businessOnly, printed.notes.first())
        assertTrue(printed.days.single().rows.none { it.personal })
    }

    @Test
    fun `the Business share is the Business total over all the driving, in whole percent`() {
        // 20.5 km Business, 5.0 km Personal: 80.39 %.
        val report = withPersonal(listsPersonal = false, share = true)
        assertEquals(80, report.businessSharePercent)

        val words = WORDS.copy(businessShare = "Business use: %1\$d%%.")
        assertTrue("Business use: 80%." in printedReport(report, words, FORMAT).notes)
        // Off: not said, though it can be worked out.
        val off = printedReport(report.copy(showsBusinessShare = false), words, FORMAT)
        assertFalse(off.notes.any { it.startsWith("Business use") })
    }

    @Test
    fun `a period with no driving has no share to say`() {
        val empty = report(emptyList(), personal = PersonalDriving(0, 0))

        assertNull(empty.businessSharePercent)
    }

    @Test
    fun `the CSV gets a Category column only when Personal trips are listed`() {
        val words =
            CsvWords(
                "Date", "Start", "End", "From", "To", "Purpose", "Vehicle", "km", "By hand",
                "added", "edited",
                category = "Category",
                business = "Business",
                personal = "Personal",
            )

        val listed = reportCsv(withPersonal(), words).trim().lines()
        assertEquals("Date,Start,End,From,To,Purpose,Vehicle,Category,km,By hand", listed.first())
        // The addresses hold commas in quotation marks, so each row is looked for whole.
        val categories =
            listed.drop(1).map { row -> listOf("Business", "Personal").single { ",$it," in row } }
        assertEquals(listOf("Business", "Personal", "Business"), categories)

        val asBefore = reportCsv(withPersonal(listsPersonal = false), words).trim().lines()
        assertEquals("Date,Start,End,From,To,Purpose,Vehicle,km,By hand", asBefore.first())
    }
}
