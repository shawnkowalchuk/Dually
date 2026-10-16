package com.shawnkowalchuk.milo.feature.tripgraph

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId

/**
 * One month of the graph: its Business and its Personal kilometres, in tenths of [unit], added
 * up as the Trips screen and the report add them up (`categoryTotals`), so that a month's point
 * is the figure its tile on Trips shows. Null for a month that has not begun.
 */
data class MonthFigure(val month: YearMonth, val businessTenths: Long?, val personalTenths: Long?)

/**
 * The twelve months of [year], each with what its counted trips add up to by what they are
 * saved as. A trip belongs to the month it **started** in, in [zone], as on Trips. Months after
 * [current] have no figures: the lines stop at the month that is running.
 *
 * @param trips the year's stored trips in any order, whatever their status: only counted ones
 * are added up.
 */
fun yearFigures(
    trips: List<Trip>,
    year: Int,
    current: YearMonth,
    zone: ZoneId,
    unit: DistanceUnit,
): List<MonthFigure> {
    val byMonth = trips.groupBy { YearMonth.from(localDateOf(it.startedAtMs, zone)) }
    return Month.entries.map { month ->
        val yearMonth = YearMonth.of(year, month)
        if (yearMonth > current) {
            MonthFigure(yearMonth, null, null)
        } else {
            val totals = categoryTotals(byMonth[yearMonth].orEmpty(), unit)
            MonthFigure(yearMonth, totals.business.tenths, totals.personal.tenths)
        }
    }
}
