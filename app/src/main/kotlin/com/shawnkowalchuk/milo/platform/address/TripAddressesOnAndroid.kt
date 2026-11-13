package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Android's [TripAddresses], whose rules are shared with the iPhone (since 2026-11-13). It follows
 * the trip controller's state, and a failure that the event log cannot take goes to a crash file,
 * which reaches the log at a later start.
 *
 * @param tripActivity the trip controller's state.
 * @param scope the application scope. The lookup's two coroutines run in it for the life of the
 * process.
 */
fun TripAddresses(
    trips: TripRepository,
    eventLog: EventLogRepository,
    crashFileStore: CrashFileStore,
    lookup: AddressLookup,
    isOnline: () -> Boolean,
    tripActivity: StateFlow<TripActivity>,
    clock: () -> Long,
    scope: CoroutineScope,
): TripAddresses = TripAddresses(
    trips = trips,
    eventLog = eventLog,
    lookup = lookup,
    isOnline = isOnline,
    tripUnderWay = tripActivity.map { it.trip?.underWay() },
    tripUnderWayNow = { tripActivity.value.trip?.underWay() },
    clock = clock,
    unlogged = { atMs, failure ->
        crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, failure))
    },
    scope = scope,
)

private fun CurrentTrip.underWay() = TripUnderWay(tripId, startLatitude, startLongitude)
