package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.report.ReportTextStyle
import com.shawnkowalchuk.milo.core.report.TextMeasure
import com.shawnkowalchuk.milo.core.time.testLocale
import com.shawnkowalchuk.milo.core.time.testZone
import com.shawnkowalchuk.milo.core.trip.driveNorth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The report for the accountant on the iPhone (phase 4g): the shared report of a month, its
 * files' names and words, and a sent report recorded as Android records it.
 */
class IosReportTest {
    private val world = RecorderWorld()
    private val settings = IosSettings(world.settings) { testLocale("en-CA") }
    private val report =
        IosReport(
            trips = world.trips,
            sent = com.shawnkowalchuk.milo.data.report.SentReportRepository(world.sentReportDao()),
            settings = world.settings,
            eventLog = world.log,
            zone = { testZone("America/Edmonton") },
            locale = { testLocale("en-CA") },
            clock = { world.nowMs },
        )

    /** A measure that takes every letter as 5 points wide: the layout only needs a width. */
    private val measure = TextMeasure { text: String, _: ReportTextStyle -> text.length * 5f }

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `a month without a name asks for it and one with a name makes both files`() = runTest {
        val recorder = world.recorder()
        recorder.start()
        world.drive(recorder, driveNorth(fixCount = 24, metresPerFix = 75.0))
        recorder.end()

        val asking = report.month(0, 2026, 10, 0, 0)
        assertEquals("your name and the accountant's email address", asking.missingForSending)

        settings.setReportText("name", "Test Driver")
        settings.setAccountantEmail("accounts@example.com")
        val month = report.month(0, 2026, 10, 0, 0)
        assertEquals(null, month.missingForSending)
        assertEquals("October 2026", month.title)

        val files = report.files(0, 2026, 10, 0, 0, measure)
        assertEquals("Mileage-2026-10-Test-Driver.pdf", files.pdfName)
        assertEquals("Mileage-2026-10-Test-Driver.csv", files.csvName)
        assertEquals(1, files.pages.size)
        assertEquals(0xFEFF, files.csv.first().code)
        assertEquals("Date,Start,End", files.csv.drop(1).take(14))
        assertEquals("Mileage - ", files.subject.take(10))
        assertEquals("accounts@example.com", files.accountant)
    }

    @Test
    fun `a report recorded as sent makes the next one a revision`() = runTest {
        settings.setReportText("name", "Test Driver")
        report.recordSent(0, 2026, 10, 0, 0)
        assertTrue(report.month(0, 2026, 10, 0, 0).sentBefore)
        assertTrue(report.files(0, 2026, 10, 0, 0, measure).pdfName.contains("rev"))
    }

    @Test
    fun `a whole year and a date range are reports too and a sent one is listed`() = runTest {
        assertEquals("2026", report.month(2, 2026, 1, 0, 0).title)
        assertTrue(report.sentList().isEmpty())

        report.recordSent(0, 2026, 10, 0, 0)
        val sent = report.sentList()
        assertEquals(1, sent.size)
        assertEquals("October 2026", sent.single().period)
        assertTrue(sent.single().line.startsWith("Sent on "))

        report.setListPersonal(true)
        assertTrue(report.month(0, 2026, 10, 0, 0).listPersonal)
    }
}
