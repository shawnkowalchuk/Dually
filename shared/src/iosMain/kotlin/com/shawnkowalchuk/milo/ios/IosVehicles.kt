package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.data.settings.IPHONE_VEHICLE_PREFIX
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.pairedVehicles
import com.shawnkowalchuk.milo.data.settings.removeVehicle
import com.shawnkowalchuk.milo.data.settings.storeVehicle
import platform.Foundation.NSUUID

// The iPhone's vehicles (ADR-018, 2026-11-17; Shawn's choice "Match by name, ask once"). The
// iPhone cannot read a Bluetooth address, so a vehicle is named, not paired: one the account's
// Android phone paired is picked from the backup's list and kept under Android's address (the
// same vehicle on both phones, nothing to join), and any other is added by name under an id of
// the iPhone's own (`iphone:` and a random identifier), which Android joins by name, asking
// once. They are stored as Android stores its paired vehicles (`storeVehicle`), so the
// odometers, the report and the backup count them as Android's: the first is the one a trip
// that names none counts for.

class IosVehicles internal constructor(
    private val settings: SettingsStore,
    private val clock: () -> Long,
) {
    /** The vehicles, the first first, in the order they were added. */
    suspend fun list(): List<IosVehicle> =
        settings.current().pairedVehicles().map { IosVehicle(it.address, it.name ?: it.address) }

    /**
     * The account's other vehicles, as the backup brought them from its other phones, that this
     * iPhone does not have yet: what Add offers to pick. Each has a name; one without is not
     * offered, since it could not be told apart.
     */
    suspend fun offered(): List<IosVehicle> {
        val stored = settings.current()
        val mine = stored.pairedVehicles().map { it.address.uppercase() }.toSet()
        return stored.knownVehicles.vehicles
            .filter { it.id.uppercase() !in mine }
            .mapNotNull { vehicle -> vehicle.name?.let { IosVehicle(vehicle.id, it) } }
            .sortedBy { it.name.lowercase() }
    }

    /** A vehicle by its name only, with an id of the iPhone's own. False for a blank name. */
    suspend fun add(name: String): Boolean {
        val named = name.trim().takeIf { it.isNotEmpty() } ?: return false
        val id = IPHONE_VEHICLE_PREFIX + NSUUID().UUIDString.lowercase()
        settings.storeVehicle(id, named, associationId = null, pairedAtMs = clock())
        return true
    }

    /** One of [offered]: kept under the other phone's id, with its name. */
    suspend fun pick(id: String): Boolean {
        val vehicle = offered().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: return false
        settings.storeVehicle(vehicle.id, vehicle.name, associationId = null, pairedAtMs = clock())
        return true
    }

    /** Forgets a vehicle: its trips keep naming it, as on Android. */
    suspend fun remove(id: String) {
        settings.removeVehicle(id)
    }

    /** The vehicle's name, or null if it is not one of [list]. */
    suspend fun named(id: String): String? =
        list().firstOrNull { it.id.equals(id, ignoreCase = true) }?.name
}

/** A vehicle as the iPhone's screens and Shortcuts actions show it. */
data class IosVehicle(val id: String, val name: String)
