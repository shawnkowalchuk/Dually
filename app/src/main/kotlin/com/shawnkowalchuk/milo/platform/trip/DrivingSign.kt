package com.shawnkowalchuk.milo.platform.trip

/**
 * What turns GPS on again beside the parked truck, once the wait's first hour is over and GPS is
 * off: each gives the fixes ten minutes at the fast rate (`GPS_AFTER_VEHICLE_REPORT_MS`), and
 * the fixes decide whether the truck drives off. None starts a trip by itself.
 *
 * Since 2026-10-19 there are three. The phone's Bluetooth had reported the truck connected for
 * three days after it had gone, so MilO waited beside a truck that was being driven, and the
 * phone's motion sensor, then the only sign, did not report the two drives of that day.
 */
internal enum class DrivingSign {
    /** The phone's motion sensor reported getting into a vehicle (since 2026-10-07). */
    VEHICLE_ENTERED,

    /** Android Auto connected, which it does in a vehicle only. */
    ANDROID_AUTO,

    /** The phone left the parked place, as Android's geofence around it reports (`ParkedFence`). */
    LEFT_PARKED_PLACE,
}
