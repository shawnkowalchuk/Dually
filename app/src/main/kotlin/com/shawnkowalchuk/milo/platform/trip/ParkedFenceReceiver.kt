package com.shawnkowalchuk.milo.platform.trip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.shawnkowalchuk.milo.app.MiloApplication

/**
 * Hears Android's geofence around the parked truck report the phone leaving it ([ParkedFence]),
 * and tells the trip controller, which turns GPS on beside the truck. It starts no trip.
 *
 * Not exported. The report is sent through a `PendingIntent` MilO made itself, which Android
 * delivers as coming from MilO, so nothing outside MilO can reach this receiver.
 */
class ParkedFenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) {
            return
        }
        val container = (context.applicationContext as MiloApplication).container
        container.tripController.onLeftParkedPlace(container.clock())
    }
}
