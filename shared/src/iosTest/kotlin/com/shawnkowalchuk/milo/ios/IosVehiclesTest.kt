package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.data.settings.IPHONE_VEHICLE_PREFIX
import com.shawnkowalchuk.milo.data.settings.KnownVehicle
import com.shawnkowalchuk.milo.data.settings.KnownVehicles
import com.shawnkowalchuk.milo.data.settings.mergeKnownVehicles
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The iPhone's vehicles (ADR-018): one the Android phone paired is picked under its address, any
 * other added by name under an id of the iPhone's own.
 */
class IosVehiclesTest {
    private val world = RecorderWorld()
    private val vehicles = IosVehicles(world.settings) { 1_000L }

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `Android's vehicle is picked under its address and is no longer offered`() = runTest {
        val ford = "AA:BB:CC:DD:EE:01"
        world.settings.mergeKnownVehicles(KnownVehicles(listOf(KnownVehicle(ford, "Ford"))))
        assertEquals(listOf(IosVehicle(ford, "Ford")), vehicles.offered())

        assertTrue(vehicles.pick(ford))

        assertEquals(listOf(IosVehicle(ford, "Ford")), vehicles.list())
        assertEquals(emptyList(), vehicles.offered())
    }

    @Test
    fun `a vehicle added by name has the iPhone's own id and a blank name is refused`() = runTest {
        assertFalse(vehicles.add("  "))
        assertTrue(vehicles.add(" Chevy "))

        val added = vehicles.list().single()
        assertEquals("Chevy", added.name)
        assertTrue(added.id.startsWith(IPHONE_VEHICLE_PREFIX))

        vehicles.remove(added.id)
        assertEquals(emptyList(), vehicles.list())
    }
}
