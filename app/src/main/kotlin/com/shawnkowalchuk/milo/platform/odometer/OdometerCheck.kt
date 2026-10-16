package com.shawnkowalchuk.milo.platform.odometer

import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.pairedVehicles
import com.shawnkowalchuk.milo.data.settings.setLastTripVehicle
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.vehicleOdometers
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Whether a trip that starts in [vehicle] asks if that vehicle's odometer is still right
 * (Shawn, 2026-10-16: "if we have muttiple vehicles we should be prompted if the km in the
 * vehicle are accurate when switching vehicles. im guessing if you have mutliple vehicles that
 * maybe you have mutliple people using the vehicles"; he chose "When Bluetooth connects to a
 * different vehicle"). Only with several vehicles paired, only when the last trip was in another
 * one, and never for the very first trip, which has nothing to compare with.
 */
fun asksAboutOdometer(vehicle: String, lastVehicle: String?, pairedVehicles: Int): Boolean =
    pairedVehicles > 1 && lastVehicle != null && !vehicle.equals(lastVehicle, ignoreCase = true)

/**
 * Watches the trips that start and, when one starts in another vehicle than the last, posts
 * the question ([notify], `OdometerCheckNotification`) with that vehicle's odometer as Dually
 * has it. It only watches:
 * nothing here can start, end or change a trip, and a failure is caught and logged.
 */
class OdometerCheck(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val notify: (vehicle: String, figure: String, unit: DistanceUnit) -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    /** Starts watching [activity]: each trip once, with the vehicle it started in. */
    fun start(activity: Flow<TripActivity>, scope: CoroutineScope) {
        scope.launch {
            activity
                .mapNotNull { now ->
                    now.trip?.let { trip -> now.vehicle?.let { trip.tripId to it } }
                }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, vehicle) -> look(vehicle) }
        }
    }

    private suspend fun look(vehicle: String) {
        try {
            val stored = settings.current()
            val paired = stored.pairedVehicles()
            settings.setLastTripVehicle(vehicle)
            if (!asksAboutOdometer(vehicle, stored.lastTripVehicle, paired.size)) return
            val finished = trips.observeFinishedTrips().first()
            val odometer =
                vehicleOdometers(stored, finished, clock(), zone())
                    .firstOrNull { it.vehicle?.address.equals(vehicle, ignoreCase = true) }
                    ?: return
            val figure = odometer.figure ?: return
            val name = odometer.vehicle?.let { it.name ?: it.address } ?: return
            notify(name, formatOdometer(figure.value, locale()), figure.unit)
            eventLog.add(
                clock(),
                EventCategory.TRIP,
                "A trip started in another vehicle than the last one ($name): asked whether its " +
                    "odometer is still about ${figure.value} ${figure.unit.name.lowercase()}",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "The odometer check after a change of vehicle failed",
                failure.stackTraceToString(),
            )
        }
    }
}
