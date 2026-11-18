package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.number.asDouble
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.TripAtReading
import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.odometer.parseOdometer
import com.shawnkowalchuk.milo.core.service.DueState
import com.shawnkowalchuk.milo.core.service.DueWording
import com.shawnkowalchuk.milo.core.service.ServiceBook
import com.shawnkowalchuk.milo.core.service.ServiceDue
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.core.service.dueLine
import com.shawnkowalchuk.milo.core.service.serviceDue
import com.shawnkowalchuk.milo.core.service.told
import com.shawnkowalchuk.milo.core.service.untold
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.dateOf
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.addOdometerReading
import com.shawnkowalchuk.milo.data.settings.changeServiceBook
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.VehicleOdometer
import com.shawnkowalchuk.milo.data.trip.markServiceDone
import com.shawnkowalchuk.milo.data.trip.serviceAlertsOf
import com.shawnkowalchuk.milo.data.trip.serviceOdometersKm
import com.shawnkowalchuk.milo.data.trip.vehicleOdometers
import com.shawnkowalchuk.milo.feature.service.englishServiceCount
import com.shawnkowalchuk.milo.feature.service.englishServiceFormat
import com.shawnkowalchuk.milo.feature.service.englishServiceText
import com.shawnkowalchuk.milo.feature.service.parseCost
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first
import kotlinx.datetime.number

// The iPhone's Service screen and the odometer it counts by (2026-11-14; Shawn: "also missing the
// service button at the bottom"), as Android's Service screen (feature/service/), Home's service
// tile and Settings' odometer tile. The book, when an item is due, the alerts and the odometer
// are the shared code's; this words them in English and checks what is typed by Android's rules
// (ServiceDialogs.kt). An item goes by the vehicle it names, or the first; the iPhone's form
// names none yet, so a new item goes by the first vehicle's odometer.
// TODO(debt): no vehicle to pick on the iPhone's service form (FINDINGS_LOG, 2026-11-17).

/** The longest interval in months: ten years, as Android's form. */
private const val MAX_MONTHS = 120

private const val METRES_PER_KM = 1000.0
private const val CENTS_PER_DOLLAR = 100.0

class IosService internal constructor(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val recorder: IosTripRecorder,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    /** The book as the screen shows it, with Home's alerts. */
    suspend fun read(): ServiceText {
        val stored = settings.current()
        val finished = trips.observeFinishedTrips().first()
        val nowMs = clock()
        val zone = zone()
        val locale = locale()
        val unit = stored.distanceUnit
        val odometerOf = serviceOdometersKm(stored, finished, nowMs, zone)
        val book = stored.serviceBook
        val items = book.items.sortedBy { it.name.lowercase() }.map { item ->
            val odometerKm = odometerOf(item)
            val due = serviceDue(item, odometerKm, nowMs, zone)
            itemText(item, due, book.historyOf(item.id), odometerKm, unit, locale, zone)
        }
        val alerts = serviceAlertsOf(stored, finished, nowMs, zone).map {
            AlertText(
                id = it.item.id,
                name = it.item.name,
                line = dueText(it.item, it.due, unit, locale),
                doneTitle = englishServiceFormat("home_service_done_title", locale, it.item.name),
            )
        }
        val today = localDateOf(nowMs, zone)
        val odometerNowKm = odometerOf(ServiceItem(0, null, "", null, null, 0, null))
        return ServiceText(
            items = items,
            alerts = alerts,
            presets = PRESETS.map { it.text(unit) },
            everyDistanceLabel = englishServiceFormat(
                "service_item_every_distance",
                locale,
                unitName(unit),
            ),
            lastOdometerLabel = englishServiceFormat(
                "service_item_last_odometer",
                locale,
                unitName(unit),
            ),
            doneOdometerLabel = englishServiceFormat(
                "service_done_odometer",
                locale,
                unitName(unit),
            ),
            todayYear = today.year,
            todayMonth = today.month.number,
            todayDay = today.day,
            newOdometer = odometerNowKm?.let { kmToShown(it, unit).toString() }.orEmpty(),
        )
    }

    /** Whether the item's form can be saved: Android's rule for its Save button. */
    fun canSave(name: String, every: String, months: String, lastOdometer: String): Boolean {
        val everyShown = everyOf(every)
        val monthsTyped = monthsOf(months)
        return name.isNotBlank() &&
            (everyShown != null || monthsTyped != null) &&
            (every.isBlank() || everyShown != null) &&
            (months.isBlank() || monthsTyped != null) &&
            (lastOdometer.isBlank() || parseOdometer(lastOdometer) != null)
    }

    /**
     * Saves an item: a new one with [id] 0. Last done on the day picked: now for today, the
     * start of any other day, and the moment it had if the day is the one it had.
     *
     * @return false, and nothing stored, if the form cannot be saved or storage failed.
     */
    suspend fun save(
        id: Long,
        name: String,
        every: String,
        months: String,
        year: Int,
        month: Int,
        day: Int,
        lastOdometer: String,
    ): Boolean {
        if (!canSave(name, every, months, lastOdometer)) return false
        val stored = settings.current()
        val unit = stored.distanceUnit
        val nowMs = clock()
        val zone = zone()
        val picked = dateOf(year, month, day)
        val old = stored.serviceBook.items.firstOrNull { it.id == id }
        val lastDoneAtMs = when {
            old != null && localDateOf(old.lastDoneAtMs, zone) == picked -> old.lastDoneAtMs
            picked == localDateOf(nowMs, zone) -> nowMs
            else -> daySpan(picked, zone).fromMs
        }
        val item = ServiceItem(
            id = old?.id ?: 0,
            vehicle = old?.vehicle,
            name = name.trim(),
            everyKm = everyOf(every)?.let { shownToKm(it, unit).toInt() },
            everyMonths = monthsOf(months),
            lastDoneAtMs = lastDoneAtMs,
            lastDoneKm = parseOdometer(lastOdometer)?.let { shownToKm(it, unit) },
        )
        return write("save a service item") { it.saved(item) }
    }

    suspend fun delete(id: Long): Boolean = write("delete a service item") { it.deleted(id) }

    /** What is wrong with Done's fields, under each: null where nothing is. */
    fun doneProblems(odometer: String, cost: String): DoneProblems = DoneProblems(
        odometer = englishServiceText("service_odometer_wrong")
            .takeIf { odometer.isNotBlank() && parseOdometer(odometer) == null },
        cost = englishServiceText("service_cost_wrong")
            .takeIf { cost.isNotBlank() && parseCost(cost) == null },
    )

    /** "Done" for an item, now, at the odometer typed, with a cost and a note if typed. */
    suspend fun done(id: Long, odometer: String, cost: String, note: String): Boolean {
        val problems = doneProblems(odometer, cost)
        if (problems.odometer != null || problems.cost != null) return false
        val unit = settings.current().distanceUnit
        val record = ServiceRecord(
            itemId = id,
            doneAtMs = clock(),
            odometerKm = parseOdometer(odometer)?.let { shownToKm(it, unit) },
            note = note.trim().ifBlank { null },
            costCents = parseCost(cost),
        )
        return write("record a service item done") { it.done(record) }
    }

    /** Home's Done: now, at the odometer as Dually has it, with no cost or note. */
    suspend fun doneNow(id: Long): Boolean = guarded("record a service item done from Home") {
        markServiceDone(settings, trips, id, clock(), zone())
    }

    /**
     * The reminder to post when a trip has ended, as Android's: the items soon or due that have
     * not been told yet, which are marked told. Null when there is nothing to tell.
     */
    suspend fun reminder(): ReminderText? {
        val stored = settings.current()
        val finished = trips.observeFinishedTrips().first()
        val nowMs = clock()
        val locale = locale()
        val untold = stored.serviceBook.untold(serviceAlertsOf(stored, finished, nowMs, zone()))
        if (untold.isEmpty()) return null
        if (!write("mark service items told") { it.told(untold) }) return null
        val lines = untold.map {
            englishServiceFormat(
                "notification_service_line",
                locale,
                it.item.name,
                dueText(it.item, it.due, stored.distanceUnit, locale),
            )
        }
        val what = "Service reminder: " + lines.joinToString("; ")
        eventLog.add(nowMs, EventCategory.TRIP, what)
        return if (lines.size == 1) {
            ReminderText(lines.single(), englishServiceText("notification_service_text"))
        } else {
            ReminderText(englishServiceCount(lines.size, locale), lines.joinToString("\n"))
        }
    }

    /** Settings' odometer tile: the first vehicle's, or the one odometer with none. */
    suspend fun odometer(): OdometerText = odometers().first()

    /**
     * Settings' odometer tiles: one for each vehicle (ADR-018, 2026-11-17), the first first, or
     * the one odometer with no vehicle.
     */
    suspend fun odometers(): List<OdometerText> {
        val stored = settings.current()
        val finished = trips.observeFinishedTrips().first()
        val zone = zone()
        val locale = locale()
        return vehicleOdometers(stored, finished, clock(), zone).map {
            odometerText(it, stored.distanceUnit, zone, locale)
        }
    }

    private fun odometerText(
        odometer: VehicleOdometer,
        unit: DistanceUnit,
        zone: ZoneId,
        locale: Locale,
    ): OdometerText {
        val figure = odometer.figure
        return OdometerText(
            vehicle = odometer.vehicle?.address,
            name = odometer.shownName,
            title = englishServiceText("settings_odometer_title"),
            figure = figure?.let { "${formatOdometer(it.value, locale)} ${unitWord(it.unit)}" },
            madeOf = figure?.let { madeOf(it, unit, zone, locale) }
                ?: englishServiceText(
                    if (unit == DistanceUnit.MILES) {
                        "settings_odometer_none_miles"
                    } else {
                        "settings_odometer_none"
                    },
                ),
            button = englishServiceText(
                if (figure == null) "settings_odometer_enter" else "settings_odometer_adjust",
            ),
            field = englishServiceText(
                if (unit == DistanceUnit.MILES) {
                    "settings_odometer_field_miles"
                } else {
                    "settings_odometer_field"
                },
            ),
            example = englishServiceText("settings_odometer_example"),
            refused = englishServiceFormat("settings_odometer_refused", locale, unitName(unit)),
            save = englishServiceText("settings_odometer_save"),
        )
    }

    /** Whether [typed] is an odometer reading, by Android's rule ([parseOdometer]). */
    fun isReading(typed: String): Boolean = parseOdometer(typed) != null

    /**
     * Stores [typed] as the reading on the dashboard now, in the unit chosen. With a trip being
     * recorded, the reading keeps which trip and how far it had gone, as on Android, so only
     * what the trip drives after it is added.
     *
     * @param vehicle the vehicle whose odometer it is (its tile's [OdometerText.vehicle]), or
     * null for the one odometer with no vehicle.
     * @return false, and nothing stored, if it is not a reading or storage failed.
     */
    suspend fun saveReading(typed: String, vehicle: String? = null): Boolean {
        val value = parseOdometer(typed) ?: return false
        val unit = settings.current().distanceUnit
        val open = recorder.now()?.let { TripAtReading(it.tripId, it.metres) }
        val reading = OdometerReading(clock(), value, unit, vehicle, open)
        return guarded("store an odometer reading") { settings.addOdometerReading(reading) }
    }

    private fun itemText(
        item: ServiceItem,
        due: ServiceDue,
        history: List<ServiceRecord>,
        odometerKm: Long?,
        unit: DistanceUnit,
        locale: Locale,
        zone: ZoneId,
    ): ServiceItemText {
        val lastDay = localDateOf(item.lastDoneAtMs, zone)
        val dayWords = formatDate(item.lastDoneAtMs, zone, locale)
        return ServiceItemText(
            id = item.id,
            name = item.name,
            state = due.state.name,
            due = dueText(item, due, unit, locale),
            lastDone = item.lastDoneKm?.let {
                englishServiceFormat(
                    "service_last_done_at",
                    locale,
                    dayWords,
                    kmWords(it, unit, locale),
                )
            } ?: englishServiceFormat("service_last_done", locale, dayWords),
            history = history.map { recordWords(it, unit, locale, zone) },
            every = item.everyKm?.let { kmToShown(it.toLong(), unit).toString() }.orEmpty(),
            months = item.everyMonths?.toString().orEmpty(),
            lastYear = lastDay.year,
            lastMonth = lastDay.month.number,
            lastDay = lastDay.day,
            lastOdometer = item.lastDoneKm?.let { kmToShown(it, unit).toString() }.orEmpty(),
            odometerNow = odometerKm?.let { kmToShown(it, unit).toString() }.orEmpty(),
            doneTitle = englishServiceFormat("service_done_title", locale, item.name),
        )
    }

    private fun recordWords(
        record: ServiceRecord,
        unit: DistanceUnit,
        locale: Locale,
        zone: ZoneId,
    ): String = listOfNotNull(
        formatDate(record.doneAtMs, zone, locale),
        record.odometerKm?.let { kmWords(it, unit, locale) },
        record.costCents?.let { "$" + formatText(locale, "%,.2f", it / CENTS_PER_DOLLAR) },
        record.note,
    ).joinToString(" · ")

    private fun madeOf(
        figure: OdometerFigure,
        unit: DistanceUnit,
        zone: ZoneId,
        locale: Locale,
    ): String {
        val reading = figure.reading
        val typed = "${formatOdometer(reading.value, locale)} ${unitWord(reading.unit)}"
        val day = formatMediumDay(localDateOf(reading.atMs, zone), locale)
        return when {
            !figure.estimated -> englishServiceFormat(
                "settings_odometer_typed_today",
                locale,
                typed,
            )

            figure.drivenTenths == 0L ->
                englishServiceFormat("settings_odometer_no_trips_since", locale, typed, day)

            else -> englishServiceFormat(
                "settings_odometer_trips_since",
                locale,
                typed,
                day,
                "${formatTenths(figure.drivenTenths, locale)} ${unitWord(unit)}",
            )
        }
    }

    /** Changes the book; false, with a line in the Log, if it could not be stored. */
    private suspend fun write(what: String, change: (ServiceBook) -> ServiceBook): Boolean =
        guarded(what) { settings.changeServiceBook(change) }

    private suspend fun guarded(what: String, work: suspend () -> Unit): Boolean = try {
        work()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        // Storage failed, whatever it threw: the screen keeps what is stored and says so.
        eventLog.add(clock(), EventCategory.ERROR, "Could not $what", failed.stackTraceToString())
        false
    }
}

/** When [item] is due, in English: the sentence [dueLine] chooses. */
private fun dueText(
    item: ServiceItem,
    due: ServiceDue,
    unit: DistanceUnit,
    locale: Locale,
): String {
    val line = dueLine(item, due)
    val km = line.km?.let { kmWords(it, unit, locale) }
    val day = line.day?.let { formatMediumDay(it, locale) }
    val args = when (line.wording) {
        DueWording.DUE_IN_OR_ON -> arrayOf(km, day)
        DueWording.OVERDUE_SINCE, DueWording.DUE_ON -> arrayOf(day)
        DueWording.OVERDUE_BY, DueWording.DUE_IN, DueWording.NO_READING -> arrayOf(km)
    }
    return englishServiceFormat(line.wording.resourceName, locale, *args)
}

/** A figure in kilometres, written in [unit]: "123,456 km". */
private fun kmWords(km: Long, unit: DistanceUnit, locale: Locale): String =
    "${formatOdometer(kmToShown(km, unit), locale)} ${unitWord(unit)}"

/** [km] in [unit], whole, as Android's kmToShown rounds it. */
private fun kmToShown(km: Long, unit: DistanceUnit): Long =
    (km * METRES_PER_KM / unit.metresPerUnit.asDouble()).roundToLong()

/** A whole figure typed in [unit], in kilometres: what the book keeps. */
private fun shownToKm(value: Long, unit: DistanceUnit): Long =
    (value * unit.metresPerUnit.asDouble() / METRES_PER_KM).roundToLong()

private fun unitName(unit: DistanceUnit) = if (unit == DistanceUnit.MILES) "miles" else "kilometres"

private fun everyOf(typed: String): Long? = typed.trim().toLongOrNull()?.takeIf { it > 0 }

private fun monthsOf(typed: String): Int? = typed.trim().toIntOrNull()?.takeIf {
    it in 1..MAX_MONTHS
}

/** A ready-made item, as Android's: its name, and its usual interval in each unit. */
private class Preset(
    val name: String,
    val everyKm: Long?,
    val everyMiles: Long?,
    val months: Int?,
) {
    fun text(unit: DistanceUnit) = PresetText(
        name = englishServiceText(name),
        every = (if (unit == DistanceUnit.MILES) everyMiles else everyKm)?.toString().orEmpty(),
        months = months?.toString().orEmpty(),
    )
}

@Suppress("MagicNumber") // Android's ready-made intervals, word for word.
private val PRESETS = listOf(
    Preset("service_preset_oil", 8_000, 5_000, 6),
    Preset("service_preset_tires", 10_000, 6_000, null),
    Preset("service_preset_inspection", null, null, 12),
    Preset("service_preset_brakes", 40_000, 25_000, null),
    Preset("service_preset_air_filter", 25_000, 15_000, 24),
)

/**
 * The Service screen, in English.
 *
 * @param alerts Home's tiles: the items soon or due, the due ones first.
 * @param todayYear today, the last day "Last done" offers.
 * @param newOdometer what a new item's odometer field opens with: the odometer now, or empty.
 */
data class ServiceText(
    val items: List<ServiceItemText>,
    val alerts: List<AlertText>,
    val presets: List<PresetText>,
    val everyDistanceLabel: String,
    val lastOdometerLabel: String,
    val doneOdometerLabel: String,
    val todayYear: Int,
    val todayMonth: Int,
    val todayDay: Int,
    val newOdometer: String,
)

/**
 * One item.
 *
 * @param state OK, SOON or DUE ([DueState]).
 * @param every, [months], [lastOdometer] what the form opens with, in the unit chosen.
 * @param odometerNow what Done's field opens with: the odometer now, or empty with no reading.
 */
data class ServiceItemText(
    val id: Long,
    val name: String,
    val state: String,
    val due: String,
    val lastDone: String,
    val history: List<String>,
    val every: String,
    val months: String,
    val lastYear: Int,
    val lastMonth: Int,
    val lastDay: Int,
    val lastOdometer: String,
    val odometerNow: String,
    val doneTitle: String,
)

/** One of Home's tiles, with the question its Done asks first ("Oil change done?"). */
data class AlertText(val id: Long, val name: String, val line: String, val doneTitle: String)

data class PresetText(val name: String, val every: String, val months: String)

data class DoneProblems(val odometer: String?, val cost: String?)

data class ReminderText(val title: String, val text: String)

/**
 * Settings' odometer tile.
 *
 * @param figure the odometer now, or null before the first reading.
 * @param madeOf what it is made of, or the sentence that asks for the first reading.
 */
data class OdometerText(
    /** The vehicle it is the odometer of, for its reading; null with no vehicle. */
    val vehicle: String?,
    /** Its name, with several vehicles; null with one or none. */
    val name: String?,
    val title: String,
    val figure: String?,
    val madeOf: String,
    val button: String,
    val field: String,
    val example: String,
    val refused: String,
    val save: String,
)
