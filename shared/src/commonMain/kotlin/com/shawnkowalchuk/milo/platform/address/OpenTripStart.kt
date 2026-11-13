package com.shawnkowalchuk.milo.platform.address

/**
 * Where the trip in progress started, as far as it is known. Held in memory only: the trip's
 * row has no position until the trip closes.
 *
 * @param latitude and [longitude] are the position the address belongs to. The trip rules can
 * still replace a trip's first fix in its first seconds, and the address is then asked for
 * again.
 */
data class OpenTripStart(
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val place: TripPlace,
)
