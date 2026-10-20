package com.shawnkowalchuk.milo.platform.trip

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.shawnkowalchuk.milo.core.trip.WAITING_LIMIT_MS

/**
 * How far the phone has to go from the parked truck's place for Android's geofence to report it
 * ([ParkedFence]). Google advises at least 100 to 150 m: a smaller circle misses exits or
 * reports ones that did not happen. A walk about a site can cross it; that costs ten minutes of
 * GPS and starts no trip, which needs 15 km/h.
 */
internal const val PARKED_FENCE_RADIUS_METRES = 150f

/** The geofence's name. There is only ever one: a new place replaces it. */
private const val FENCE_ID = "parked_truck"

/** Tells this broadcast from MilO's others that Android delivers to its receivers. */
private const val FENCE_REQUEST = 7

/**
 * Where a geofence is put around the parked truck: its place, in degrees.
 */
data class FencePlace(val latitude: Double, val longitude: Double)

/**
 * The second safety net beside a parked, connected truck (since 2026-10-19; Shawn: "yes go ahead
 * with both safety nets"). While MilO waits beside the truck, Android watches a circle of
 * [PARKED_FENCE_RADIUS_METRES] around its place, at little cost to the battery, and reports the
 * phone leaving it to [ParkedFenceReceiver]. That turns GPS on ([DrivingSign.LEFT_PARKED_PLACE]),
 * and the fixes decide whether the truck has driven off: the trip then starts at the parked
 * place, so the stretch driven before the report is counted.
 *
 * Why: on 2026-10-19 the phone's Bluetooth had gone on reporting the truck connected since the
 * Friday before, after it had gone. MilO waited beside it, GPS was off after the wait's first
 * hour, and the phone's motion sensor reported neither drive of that day.
 *
 * The geofence lives as long as the wait, and at most [WAITING_LIMIT_MS], after which Android
 * drops it by itself. Android reports an exit within a few minutes, sometimes later while the
 * phone sleeps. A failure (the location permission withdrawn, Google's location service off) is
 * written to the event log; the wait goes on without the net.
 *
 * TODO(debt): a geofence that fails to go up is not tried again until the next wait. See
 *  docs/FINDINGS_LOG.md, 2026-10-19.
 *
 * @param log writes a line to the event log, in order with the controller's.
 */
internal class ParkedFence(
    private val context: Context,
    private val log: (String, String?) -> Unit,
) {
    private val client = LocationServices.getGeofencingClient(context)

    /** The place the geofence is around, or null when there is none. */
    private var around: FencePlace? = null

    /** Puts the geofence around [place], or takes it away if null. Nothing if it is there. */
    fun watch(place: FencePlace?) {
        if (place == around) return
        if (place == null) {
            clear()
            return
        }
        around = place
        add(place)
    }

    /** Takes the geofence away: a trip has started, or the wait is over. */
    fun clear() {
        if (around == null) return
        around = null
        client.removeGeofences(listOf(FENCE_ID))
    }

    // The permission is MilO's precondition for every trip (the Setup checklist); without it the
    // request fails, and the failure is logged below.
    @SuppressLint("MissingPermission")
    private fun add(place: FencePlace) {
        val fence =
            Geofence.Builder()
                .setRequestId(FENCE_ID)
                .setCircularRegion(place.latitude, place.longitude, PARKED_FENCE_RADIUS_METRES)
                .setExpirationDuration(WAITING_LIMIT_MS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        // A phone already outside when it goes up is reported at once: put up after a restart
        // in the middle of a drive, the geofence would otherwise never see the phone leave.
        // Inside, as when a wait begins beside the truck, nothing is reported until it leaves.
        val request =
            GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .addGeofence(fence)
                .build()
        try {
            client
                .addGeofences(request, broadcast())
                .addOnSuccessListener {
                    log(
                        "Geofence of ${PARKED_FENCE_RADIUS_METRES.toInt()} m put around the parked truck",
                        null,
                    )
                }
                .addOnFailureListener {
                    around = null
                    log("The geofence around the parked truck could not be put up", it.toString())
                }
        } catch (refused: SecurityException) {
            around = null
            log("The geofence around the parked truck could not be put up", refused.toString())
        }
    }

    private fun broadcast(): PendingIntent = PendingIntent.getBroadcast(
        context,
        FENCE_REQUEST,
        Intent(context, ParkedFenceReceiver::class.java),
        // Mutable: Android fills in which geofence and which way it was crossed.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
