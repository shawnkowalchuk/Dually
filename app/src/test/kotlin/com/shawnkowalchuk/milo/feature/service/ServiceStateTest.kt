package com.shawnkowalchuk.milo.feature.service

import com.shawnkowalchuk.milo.core.service.DueState
import com.shawnkowalchuk.milo.core.service.ServiceBook
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceStateTest {
    @Test
    fun `with no vehicle paired the items stand in one unnamed group, by name`() {
        val book =
            ServiceBook()
                .saved(ServiceItem(0, null, "Tire rotation", 10_000, null, 0L, null))
                .saved(ServiceItem(0, null, "inspection", null, 12, 0L, null))
        val settings = MiloSettings(serviceBook = book, distanceUnit = DistanceUnit.MILES)

        val state = serviceState(settings, emptyList(), nowMs = 1_000L, zone = ZoneOffset.UTC)

        val vehicle = state.vehicles!!.single()
        assertNull(vehicle.name)
        assertNull(vehicle.odometerKm)
        assertEquals(listOf("inspection", "Tire rotation"), vehicle.lines.map { it.item.name })
        assertEquals(DueState.OK, vehicle.lines[0].due.state)
        assertEquals(DistanceUnit.MILES, state.unit)
    }
}
