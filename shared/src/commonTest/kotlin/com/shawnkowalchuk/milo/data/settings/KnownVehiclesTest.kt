package com.shawnkowalchuk.milo.data.settings

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip
import com.shawnkowalchuk.milo.core.odometer.drivenIn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account's vehicles (ADR-018): merged as a set, and an iPhone's vehicle joined to Android's
 * counting as the one vehicle, in either direction, without a trip being rewritten.
 */
class KnownVehiclesTest {
    private val ford = "AA:BB:CC:DD:EE:01"
    private val iphoneFord = "${IPHONE_VEHICLE_PREFIX}7f3a"

    @Test
    fun `two phones' lists merge as a set and keep a name`() {
        val android = KnownVehicles(listOf(KnownVehicle(ford, "Ford")))
        val iphone =
            KnownVehicles(
                vehicles = listOf(KnownVehicle(iphoneFord, "Ford"), KnownVehicle(ford, null)),
                declined = listOf(VehicleJoin(iphoneFord, "AA:BB:CC:DD:EE:02")),
            )

        val both = android.with(iphone)

        assertEquals(setOf(ford, iphoneFord), both.vehicles.map { it.id }.toSet())
        assertEquals("Ford", both.vehicles.first { it.id == ford }.name, "a name is not lost")
        assertEquals(1, both.declined.size)
        assertEquals(both, both.with(iphone), "merging again changes nothing")
    }

    @Test
    fun `a joined iPhone vehicle's trips count for the Android vehicle and back`() {
        val settings =
            MiloSettings(
                knownVehicles = KnownVehicles(joins = listOf(VehicleJoin(iphoneFord, ford))),
            )
        val trips =
            listOf(
                DrivenTrip(startedAtMs = 1, metres = 1000.0, vehicle = ford),
                DrivenTrip(startedAtMs = 2, metres = 2000.0, vehicle = iphoneFord),
                DrivenTrip(startedAtMs = 3, metres = 4000.0, vehicle = "AA:BB:CC:DD:EE:02"),
            )

        val inFord = trips.drivenIn(ford, isFirst = false, alsoAs = settings.sameVehicle(ford))
        val inIphoneFord =
            trips.drivenIn(iphoneFord, isFirst = false, alsoAs = settings.sameVehicle(iphoneFord))

        assertEquals(listOf(1L, 2L), inFord.map { it.startedAtMs })
        assertEquals(listOf(1L, 2L), inIphoneFord.map { it.startedAtMs })
    }
}
