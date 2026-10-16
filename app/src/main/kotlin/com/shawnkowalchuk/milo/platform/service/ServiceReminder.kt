package com.shawnkowalchuk.milo.platform.service

import com.shawnkowalchuk.milo.core.service.ServiceAlert
import com.shawnkowalchuk.milo.core.service.told
import com.shawnkowalchuk.milo.core.service.untold
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.changeServiceBook
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.serviceAlertsOf
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The service reminder (since 2026-10-16; Shawn chose "Notification + Home tile"): when a trip
 * has ended, it looks whether a service item has come soon or due that it has not told of, and
 * posts the notification ([notify], `ServiceNotification`). Never during a trip: the driver is
 * parked when it comes. Each due point is told once, until Done. It only watches: nothing here
 * can start, end or change a trip, and a failure is caught and logged.
 */
class ServiceReminder(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val notify: (alerts: List<ServiceAlert>, unit: DistanceUnit) -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    /** Starts watching [activity]: a look each time a trip that was open is no longer. */
    fun start(activity: Flow<TripActivity>, scope: CoroutineScope) {
        scope.launch {
            activity
                .map { it.trip?.tripId }
                .distinctUntilChanged()
                // What the process starts with is no trip ending.
                .drop(1)
                .filter { it == null }
                .collect { look() }
        }
    }

    /** Looks once: what has come soon or due and is untold, told now. */
    suspend fun look() {
        try {
            val stored = settings.current()
            val finished = trips.observeFinishedTrips().first()
            val alerts = serviceAlertsOf(stored, finished, clock(), zone())
            val untold = stored.serviceBook.untold(alerts)
            if (untold.isEmpty()) return
            notify(untold, stored.distanceUnit)
            settings.changeServiceBook { it.told(untold) }
            eventLog.add(
                clock(),
                EventCategory.TRIP,
                "A trip ended: told of service ${untold.joinToString {
                    "\"${it.item.name}\" (${it.due.state.name.lowercase()})"
                }}",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "The service reminder could not look at the service items",
                failure.stackTraceToString(),
            )
        }
    }
}
