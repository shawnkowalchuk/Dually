package com.shawnkowalchuk.milo.platform.trip

/**
 * Where a geofence is put around the parked truck: its place, in degrees. In the shared code
 * (phase 4b) because the trip ledger hands it out; Android puts the fence (ParkedFence.kt), and
 * the iPhone will watch the same place with a region (ADR-015).
 */
data class FencePlace(val latitude: Double, val longitude: Double)
