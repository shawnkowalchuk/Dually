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
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.settings.ShownUnit
import com.shawnkowalchuk.milo.data.settings.buildSettingsStore
import com.shawnkowalchuk.milo.data.settings.buildShownUnit
import com.shawnkowalchuk.milo.data.trip.TripCorrections
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.platform.address.TripAddresses
import com.shawnkowalchuk.milo.platform.trip.TripLedger
import com.shawnkowalchuk.milo.platform.trip.TripRuleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    /** The Trips screen's months and changes (phase 4c). */
    val trips: IosTrips,
    private val addresses: TripAddresses,
    private val automations: TruckBelief,
    /** The driving alert (phase 4d, third part). */
    val drivingAlert: IosDrivingAlert,
    /** The Settings screen (phase 4g). */
    val settings: IosSettings,
    /** The report for the accountant (phase 4g). */
    val report: IosReport,
    private val shownUnit: ShownUnit,
) {
    /** "km" or "mi", as Settings has it now: every distance the iPhone writes follows it. */
    fun unitWord(): String = unitWord(shownUnit.unit.value)

    /**
     * The Setup screen (phase 4d, second part) for what iOS reports, with the check that each
     * automation has run.
     */
    fun setup(
        location: LocationAccess,
        precise: Boolean,
        alwaysAsked: Boolean,
        vehicle: VehicleLink?,
        startMade: Boolean,
        endMade: Boolean,
        motion: Permission,
        notifications: Permission,
    ): SetupScreen = setupScreen(
        location = location,
        precise = precise,
        alwaysAsked = alwaysAsked,
        vehicle = vehicle,
        connectHeardAtMs = automations.connectHeardAtMs,
        disconnectHeardAtMs = automations.disconnectHeardAtMs,
        startMade = startMade,
        endMade = endMade,
        motion = motion,
        notifications = notifications,
        nowMs = nowMs(),
        zone = systemZoneId(),
        locale = phoneLocale(),
    )

    /**
     * The app has started: picks up a trip left open when it last stopped, then looks up the
     * addresses that are due, as Android does at process start once its trip controller has
     * dealt with what the last process left behind.
     */
    suspend fun appStarted() {
        recorder.now()
        addresses.catchUp("the app started")
    }

    /**
     * Looks up the addresses that are due, as Android does when its Trips screen comes to the
     * front. Returns at once.
     *
     * @param reason what prompted it, in words, for the event log.
     */
    fun lookUpAddresses(reason: String) = addresses.catchUp(reason)

    /**
     * Today's and this month's Business kilometres, in the phone's time zone and language.
     * Kilometres until the iPhone has its Settings (phase 4g), which choose miles as Android's do.
     */
    suspend fun homeFigures(nowMs: Long): HomeFigures {
        val zone = systemZoneId()
        val locale = phoneLocale()
        val unit = shownUnit.unit.value
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
        formatDistance(metres, shownUnit.unit.value, phoneLocale())

    companion object {
        /**
         * Opens the databases and the settings file, as Android's AppContainer does; the app
         * calls it once, when it starts.
         *
         * @param placeFinder Apple's geocoder, from Swift, for the addresses of trips.
         */
        fun open(placeFinder: ApplePlaceFinder): DuallyCore {
            val database = buildMiloDatabase()
            val points = buildPointsDatabase()
            val settings = buildSettingsStore()
            val eventLog = EventLogRepository(database.eventLogDao())
            val clock = ::nowMs
            val tripRepository = TripRepository(database.tripDao())
            val ledger =
                TripLedger(
                    tripRepository,
                    RawPointRepository(points.rawPointDao()),
                    settings,
                    zone = ::systemZoneId,
                )
            val truck = StoredTruckBelief()
            val recorder =
                IosTripRecorder(
                    ledger,
                    eventLog,
                    TripRuleSettings(settings, eventLog, clock),
                    truck,
                    clock,
                )
            // The app's scope: what the shared code follows for as long as the app runs.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val shownUnit = buildShownUnit(settings, { eventLog }, scope, clock).also { it.start() }
            val network = IosNetworkStatus()
            val addresses =
                TripAddresses(
                    trips = tripRepository,
                    eventLog = eventLog,
                    lookup = IosAddressLookup(placeFinder),
                    isOnline = network::isOnline,
                    tripUnderWay = recorder.underWay,
                    tripUnderWayNow = { recorder.underWay.value },
                    clock = clock,
                    // TODO(debt): the iPhone has no crash file yet, so a failed lookup that the
                    //  event log cannot take either (its database unreadable) is lost. See
                    //  docs/FINDINGS_LOG.md (2026-11-13).
                    unlogged = { _, _ -> },
                    scope = scope,
                )
            val trips =
                IosTrips(
                    tripRepository,
                    TripCorrections(tripRepository, eventLog, clock),
                    eventLog,
                    zone = ::systemZoneId,
                    locale = ::phoneLocale,
                    clock = clock,
                    openTripStart = { addresses.openTripStart.value },
                    unit = { shownUnit.unit.value },
                )
            val drivingAlert =
                IosDrivingAlert(
                    recorder = recorder,
                    trips = tripRepository,
                    settings = settings,
                    eventLog = eventLog,
                    memory = StoredMotionMemory(),
                    zone = ::systemZoneId,
                    clock = clock,
                )
            return DuallyCore(
                database,
                recorder,
                trips,
                addresses,
                truck,
                drivingAlert,
                IosSettings(settings, ::phoneLocale),
                IosReport(
                    trips = tripRepository,
                    sent = SentReportRepository(database.sentReportDao()),
                    settings = settings,
                    eventLog = eventLog,
                    zone = ::systemZoneId,
                    locale = ::phoneLocale,
                    clock = clock,
                ),
                shownUnit,
            )
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
