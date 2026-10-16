package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.service.ServiceAlert
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.serviceAlerts
import com.shawnkowalchuk.milo.core.service.vehicleOf
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.pairedVehicles
import java.time.ZoneId

/**
 * The odometer, in whole kilometres, each service item goes by now ([vehicleOf]), worked out as
 * Home and Settings work out each vehicle's ([vehicleOdometers]); null with no reading. For the
 * Service screen, Home's tile and the reminder, so that the three cannot disagree.
 */
fun serviceOdometersKm(
    settings: MiloSettings,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
): (ServiceItem) -> Long? {
    val inKm = settings.copy(distanceUnit = DistanceUnit.KILOMETRES)
    val odometers = vehicleOdometers(inKm, finished, nowMs, zone)
    val paired = settings.pairedVehicles().map { it.address }
    return { item ->
        val vehicle = vehicleOf(item, paired)
        val odometer =
            if (vehicle == null) {
                odometers.firstOrNull()
            } else {
                odometers.firstOrNull { it.vehicle?.address.equals(vehicle, ignoreCase = true) }
            }
        odometer?.figure?.value
    }
}

/** The service items that are soon or due now ([serviceAlerts]). */
fun serviceAlertsOf(
    settings: MiloSettings,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
): List<ServiceAlert> = serviceAlerts(
    settings.serviceBook,
    serviceOdometersKm(settings, finished, nowMs, zone),
    nowMs,
    zone,
)
