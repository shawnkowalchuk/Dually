package com.shawnkowalchuk.milo.platform.pasthours

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.setRunningTripCategory
import com.shawnkowalchuk.milo.data.trip.TripCorrections
import java.io.IOException
import kotlinx.coroutines.launch

/**
 * The two buttons of the work-hours notification ([PastHoursNotification]): **Mark Personal**
 * and **Keep Business**. For the trip still being recorded the answer is its choice, as Home's
 * button sets it, and the trip is saved as it when it ends; for a trip that has ended it is a
 * marking by hand, as on Trips. Either way the trip is then set by hand and not asked about
 * again. Not exported: only MilO's own notification reaches it.
 */
class PastHoursReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val category =
            when (intent.action) {
                ACTION_MARK_PERSONAL -> TripCategory.PERSONAL
                ACTION_KEEP_BUSINESS -> TripCategory.BUSINESS
                else -> return
            }
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, 0L).takeIf { it > 0 } ?: return
        val container = (context.applicationContext as MiloApplication).container
        // Held open while storage is written, so that Android does not freeze the process first.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val trips = container.tripRepository
                val recording = trips.findOpenTrip()?.id == tripId
                if (recording) {
                    container.settingsStore.setRunningTripCategory(tripId, category)
                } else {
                    TripCorrections(trips, container.eventLogRepository, container.clock)
                        .mark(tripId, category)
                }
                container.checks.pastHoursNotification.cancel()
                val name = category.name.lowercase().replaceFirstChar { it.uppercase() }
                val what = if (recording) "for the trip being recorded" else "after it ended"
                container.eventLogRepository.add(
                    container.clock(),
                    EventCategory.TRIP,
                    "Trip $tripId: $name chosen from the work-hours notification, $what",
                )
            } catch (notStored: IOException) {
                container.eventLogRepository.add(
                    container.clock(),
                    EventCategory.ERROR,
                    "The work-hours notification's answer could not be stored",
                    notStored.stackTraceToString(),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
