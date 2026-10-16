package com.shawnkowalchuk.milo.feature.tripgraph

import com.shawnkowalchuk.milo.core.designsystem.component.chartStep
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The graph's months: what each adds up to, and where the lines stop. */
class MonthFigureTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val october = YearMonth.of(2026, 10)

    private fun trip(
        start: String,
        metres: Double,
        category: TripCategory,
        status: TripStatus = TripStatus.FINISHED,
    ): Trip {
        val at = LocalDateTime.parse(start).atZone(zone).toInstant().toEpochMilli()
        return Trip(
            id = at,
            startedAtMs = at,
            endedAtMs = at + 1_200_000,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
            category = category,
        )
    }

    @Test
    fun `each month adds up its counted Business and Personal trips by their start`() {
        val trips =
            listOf(
                trip("2026-03-02T08:00", 12_340.0, TripCategory.BUSINESS),
                trip("2026-03-02T18:00", 4_960.0, TripCategory.PERSONAL),
                trip("2026-03-31T23:50", 1_000.0, TripCategory.BUSINESS),
                trip("2026-03-05T08:00", 50_000.0, TripCategory.BUSINESS, TripStatus.DELETED),
                trip("2026-04-01T09:00", 2_000.0, TripCategory.PERSONAL),
            )

        val months = yearFigures(trips, 2026, october, zone, DistanceUnit.KILOMETRES)

        assertEquals(12, months.size)
        assertEquals(MonthFigure(YearMonth.of(2026, 3), 133L, 50L), months[2])
        assertEquals(MonthFigure(YearMonth.of(2026, 4), 0L, 20L), months[3])
        assertEquals(MonthFigure(YearMonth.of(2026, 1), 0L, 0L), months[0])
    }

    @Test
    fun `the months after the running one have no figures, so the lines stop`() {
        val months = yearFigures(emptyList(), 2026, october, zone, DistanceUnit.KILOMETRES)

        assertEquals(0L, months[9].businessTenths)
        assertNull(months[10].businessTenths)
        assertNull(months[11].personalTenths)
        // A past year is whole.
        val lastYear = yearFigures(emptyList(), 2025, october, zone, DistanceUnit.KILOMETRES)
        assertEquals(0L, lastYear[11].businessTenths)
    }

    @Test
    fun `the scale steps in whole, round figures that reach the highest point`() {
        assertEquals(1.0, chartStep(0.0), 0.0)
        assertEquals(150.0, chartStep(412.3), 0.0)
        assertEquals(300.0, chartStep(1_192.1), 0.0)
        assertEquals(25.0, chartStep(100.0), 0.0)
        assertEquals(2.0, chartStep(6.2), 0.0)
        assertEquals(3.0, chartStep(9.5), 0.0)
    }
}
