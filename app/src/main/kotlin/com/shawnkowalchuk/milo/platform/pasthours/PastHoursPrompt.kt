package com.shawnkowalchuk.milo.platform.pasthours

import com.shawnkowalchuk.milo.core.schedule.asksAboutPastHours
import com.shawnkowalchuk.milo.core.schedule.workHoursEndMs
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the prompt shows: `PastHoursNotification`, or a stand-in in a test. */
interface PastHoursNotices {
    fun duringTrip(tripId: Long, hoursEndMs: Long)

    fun afterTrip(tripId: Long, startedAtMs: Long, endedAtMs: Long)

    fun cancel()
}

/**
 * Asks about a trip that runs past the work hours (since 2026-11-03; Shawn: "when im driving
 * and for example it goes from personal hours to work hours and vise versa to get a notifcation
 * to say to change to the corresponding business or personal"; he chose Business trips that
 * run past the hours, a heads-up as they end and the question when the trip ends).
 *
 * It watches the trip being recorded. For one the hours make Business that Shawn has not chosen
 * for on Home, it waits for the hours to end and posts the silent heads-up. When the trip has
 * ended, it posts the question if the trip was saved Business, ran past the hours and is still
 * not chosen by hand ([asksAboutPastHours]); otherwise it takes the heads-up away. It only
 * watches: nothing here starts, ends or files a trip, and a failure is caught and logged.
 */
class PastHoursPrompt(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val notices: PastHoursNotices,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    /** Starts watching [activity]: each trip as it opens, and as it ends. */
    fun start(activity: Flow<TripActivity>, scope: CoroutineScope) {
        scope.launch {
            var recorded: Long? = null
            activity
                .map { now -> now.trip?.let { it.tripId to it.startedAtMs } }
                .distinctUntilChanged()
                .collectLatest { open ->
                    val ended = recorded
                    recorded = open?.first
                    guarded {
                        if (ended != null && ended != open?.first) afterTrip(ended)
                        if (open != null) duringTrip(open.first, open.second)
                    }
                }
        }
    }

    /** Waits for the hours to end while [tripId] is recorded, then posts the heads-up. */
    internal suspend fun duringTrip(tripId: Long, startedAtMs: Long) {
        val stored = settings.current()
        if (stored.runningTripCategory?.tripId == tripId) return
        val hoursEndMs = workHoursEndMs(startedAtMs, stored.schedule, zone()) ?: return
        val waitMs = hoursEndMs - clock()
        if (waitMs > 0) delay(waitMs)
        // Chosen on Home meanwhile: nothing to ask.
        if (settings.current().runningTripCategory?.tripId == tripId) return
        notices.duringTrip(tripId, hoursEndMs)
        eventLog.add(
            clock(),
            EventCategory.TRIP,
            "Trip $tripId: the work hours ended while it was recorded as Business. Asked " +
                "quietly whether to mark it Personal",
        )
    }

    /** [tripId] has ended: the question, if it is still to be asked, or nothing. */
    internal suspend fun afterTrip(tripId: Long) {
        val trip = trips.findTrip(tripId)
        val ended = trip?.endedAtMs
        val asks =
            trip != null && ended != null && trip.status == TripStatus.FINISHED &&
                asksAboutPastHours(trip.category, trip.ranPastSchedule, trip.categorySetByHand)
        if (!asks) {
            notices.cancel()
            return
        }
        notices.afterTrip(tripId, trip.startedAtMs, ended)
        eventLog.add(
            clock(),
            EventCategory.TRIP,
            "Trip $tripId ran past the work hours and was saved as Business: asked whether to " +
                "mark it Personal",
        )
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "The work-hours question could not look at the trip",
                failure.stackTraceToString(),
            )
        }
    }
}
