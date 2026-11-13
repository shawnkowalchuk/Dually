package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatMonthName
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.MiloDatabase
import com.shawnkowalchuk.milo.data.buildMiloDatabase
import com.shawnkowalchuk.milo.data.buildPointsDatabase
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.buildSettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.platform.trip.TripLedger
import com.shawnkowalchuk.milo.platform.trip.TripRuleSettings
import kotlinx.coroutines.flow.first
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.timeIntervalSince1970

/**
 * The door the iPhone app's Swift code comes through to the shared code (ADR-014, phase 4a).
 * Swift sees Kotlin's `suspend` functions as `async` ones. What it is handed is text that is
 * ready to show, worked out by the same rules and formats as on Android, so the two phones
 * cannot differ in what a figure says.
 *
 * One is made when the app starts, and it opens the database.
 */
class DuallyCore private constructor(
    private val database: MiloDatabase,
    /** Start, End and the GPS fixes of a trip recorded on the iPhone (phase 4b). */
    val recorder: IosTripRecorder,
) {
    /**
     * Today's and this month's Business kilometres, in the phone's time zone and language.
     * Kilometres until the iPhone has its Settings (phase 4g), which choose miles as Android's do.
     */
    suspend fun homeFigures(nowMs: Long): HomeFigures {
        val zone = systemZoneId()
        val locale = phoneLocale()
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

    /** [metres] as Home writes a distance: "12.3", in the phone's language. */
    fun distanceText(metres: Double): String =
        formatDistance(metres, DistanceUnit.KILOMETRES, phoneLocale())

    companion object {
        /**
         * Opens the databases and the settings file, as Android's AppContainer does; the app
         * calls it once, when it starts.
         */
        fun open(): DuallyCore {
            val database = buildMiloDatabase()
            val points = buildPointsDatabase()
            val settings = buildSettingsStore()
            val eventLog = EventLogRepository(database.eventLogDao())
            val clock = ::nowMs
            val ledger =
                TripLedger(
                    TripRepository(database.tripDao()),
                    RawPointRepository(points.rawPointDao()),
                    settings,
                    zone = ::systemZoneId,
                )
            val recorder =
                IosTripRecorder(
                    ledger,
                    eventLog,
                    TripRuleSettings(settings, eventLog, clock),
                    clock,
                )
            return DuallyCore(database, recorder)
        }
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

/** The phone's language, as the shared code takes it. */
private fun phoneLocale(): Locale = Locale(NSLocale.currentLocale.localeIdentifier)

/** The time now, as stored times are: milliseconds since 1970. */
private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

private const val MILLIS_PER_SECOND = 1000.0
