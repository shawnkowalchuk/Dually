package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.odometerOverTrip
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each trip's odometer on the report (2026-10-16): how it is worked out, printed and exported. */
class ReportTripOdometerTest {
    private val reading =
        OdometerReading(edmonton("2026-10-01T07:00"), 120_000, DistanceUnit.KILOMETRES)
    private val first = DrivenTrip(edmonton("2026-10-01T08:00"), 12_000.0)
    private val second = DrivenTrip(edmonton("2026-10-01T10:00"), 8_400.0)

    @Test
    fun `a trip's start holds the trips before it, and its end the trip itself`() {
        val span =
            odometerOverTrip(
                second.startedAtMs,
                second.startedAtMs + 1_200_000,
                EDMONTON,
                listOf(reading),
                listOf(first, second),
                DistanceUnit.KILOMETRES,
            )!!

        assertEquals(120_012L, span.start.value)
        assertEquals(120_020L, span.end.value)
        assertTrue(span.start.estimated && span.end.estimated)
    }

    @Test
    fun `no reading, no odometer`() {
        assertNull(
            odometerOverTrip(
                first.startedAtMs,
                first.startedAtMs + 60_000,
                EDMONTON,
                emptyList(),
                listOf(first),
                DistanceUnit.KILOMETRES,
            ),
        )
    }

    @Test
    fun `it is printed on the trip's detail line, and in two CSV columns`() {
        val span =
            odometerOverTrip(
                first.startedAtMs,
                first.startedAtMs + 1_200_000,
                EDMONTON,
                listOf(reading),
                listOf(first),
                DistanceUnit.KILOMETRES,
            )!!
        val day = LocalDate.of(2026, 10, 1)
        val report =
            report(listOf(trip(day, "08:00").copy(odometer = span)))
                .copy(showsTripOdometer = true)
        val words =
            WORDS.copy(
                tripOdometer = "Odometer %1\$s → %2\$s km",
                tripOdometerEstimated = "Odometer %1\$s → %2\$s km est.",
            )

        val row = printedReport(report, words, FORMAT).days.single().rows.single()
        assertEquals("Odometer 120,000 → 120,012 km est.", row.detail)

        val csvWords =
            CsvWords(
                "Date", "Start", "End", "From", "To", "Purpose", "Vehicle", "km", "By hand",
                "added", "edited",
                odometerStart = "Odometer start",
                odometerEnd = "Odometer end",
            )
        val lines = reportCsv(report, csvWords).trim().lines()
        val titles = "Date,Start,End,From,To,Purpose,Vehicle,km,Odometer start,Odometer end,By hand"
        assertEquals(titles, lines.first())
        assertTrue(lines[1].endsWith(",120000,120012,"))
    }
}
