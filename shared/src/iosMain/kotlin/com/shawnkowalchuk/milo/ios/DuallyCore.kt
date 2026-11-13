package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.MiloDatabase
import com.shawnkowalchuk.milo.data.buildMiloDatabase
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import kotlinx.coroutines.flow.first
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

/**
 * The door the iPhone app's Swift code comes through to the shared code (ADR-014, phase 4a).
 * Swift sees Kotlin's `suspend` functions as `async` ones. What it is handed is text that is
 * ready to show, worked out by the same rules and formats as on Android, so the two phones
 * cannot differ in what a figure says.
 *
 * One is made when the app starts, and it opens the database.
 */
class DuallyCore private constructor(private val database: MiloDatabase) {
    /**
     * Today's and this month's Business kilometres, in the phone's time zone and language.
     * Kilometres until the iPhone has its Settings (phase 4g), which choose miles as Android's do.
     */
    suspend fun homeFigures(nowMs: Long): HomeFigures {
        val zone = systemZoneId()
        val locale = Locale(NSLocale.currentLocale.localeIdentifier)
        val unit = DistanceUnit.KILOMETRES
        val today = daySpan(localDateOf(nowMs, zone), zone)
        val month = monthOf(nowMs, zone)
        val monthSpan = monthSpan(month, zone)
        val trips = database.tripDao()
        val todayTotals =
            categoryTotals(trips.observeStartedBetween(today.fromMs, today.untilMs).first(), unit)
        val monthTotals =
            categoryTotals(
                trips.observeStartedBetween(monthSpan.fromMs, monthSpan.untilMs).first(),
                unit,
            )
        return HomeFigures(
            todayBusiness = formatTenths(todayTotals.business.tenths, locale),
            todayTrips = todayTotals.count,
            monthName = formatMonthName(month, locale),
            monthBusiness = formatTenths(monthTotals.business.tenths, locale),
        )
    }

    companion object {
        /** Opens the database; the app calls it once, when it starts. */
        fun open(): DuallyCore = DuallyCore(buildMiloDatabase())
    }
}

/**
 * Home's figures, as text: "22.0" Business kilometres today over [todayTrips] trips, and
 * "1192.1" in [monthName] ("November").
 */
data class HomeFigures(
    val todayBusiness: String,
    val todayTrips: Int,
    val monthName: String,
    val monthBusiness: String,
)
