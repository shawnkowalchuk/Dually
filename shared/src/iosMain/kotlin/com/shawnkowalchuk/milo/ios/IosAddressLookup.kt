package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.platform.address.AddressLookup
import com.shawnkowalchuk.milo.platform.address.LookupAnswer
import com.shawnkowalchuk.milo.platform.address.PlaceParts
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * How long an answer is waited for, as on Android: a lookup that never answers must not hold up
 * the trips waiting behind it.
 */
private const val ANSWER_TIMEOUT_MS = 20_000L

/**
 * Apple's geocoder, as the Swift code asks it (AppleGeocoder.swift). In Swift because which of
 * Apple's two geocoders is used depends on the iOS version, and only Swift can check that. It
 * answers [PlaceReply] exactly once.
 */
interface ApplePlaceFinder {
    fun find(latitude: Double, longitude: Double, reply: PlaceReply)
}

/** Where Apple's answer goes: the places it offered, in its own order, or why there was none. */
class PlaceReply internal constructor(private val answer: (LookupAnswer) -> Unit) {
    fun places(candidates: List<PlaceParts>) = answer(LookupAnswer.Places(candidates))

    fun failed(reason: String) = answer(LookupAnswer.Failed(reason))
}

/**
 * The iPhone's [AddressLookup]: Apple's geocoder, through [finder]. The position of a trip's
 * start or end leaves the phone here, and nowhere else in Dually, as Android's goes to Google
 * only in its GeocoderAddressLookup.
 */
internal class IosAddressLookup(private val finder: ApplePlaceFinder) : AddressLookup {
    override suspend fun lookUp(latitude: Double, longitude: Double): LookupAnswer =
        withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // A late answer, after the wait has given up, is dropped.
                finder.find(
                    latitude,
                    longitude,
                    PlaceReply { if (continuation.isActive) continuation.resume(it) },
                )
            }
        } ?: LookupAnswer.Failed("no answer within ${ANSWER_TIMEOUT_MS / 1000} s")
}

/**
 * Whether the iPhone has a working internet connection, as Apple's Network framework last said.
 * Without one the address lookup asks nothing and counts no attempt, as on Android.
 */
internal class IosNetworkStatus {
    // Until Apple's first answer, a moment after start, the phone is taken to be online: at
    // worst one lookup fails and waits two minutes.
    @Volatile private var online = true

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            online = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    fun isOnline(): Boolean = online
}
