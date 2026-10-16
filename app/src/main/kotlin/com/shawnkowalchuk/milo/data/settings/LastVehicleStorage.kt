package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

// The vehicle the last trip started in (since 2026-10-16), by its Bluetooth address: what the
// odometer check compares a new trip's vehicle with. Not a setting, and not in an export file.

private val LAST_TRIP_VEHICLE = stringPreferencesKey("last_trip_vehicle")

internal fun Preferences.readLastTripVehicle(): String? = this[LAST_TRIP_VEHICLE]

/** Stores [address] as the vehicle the last trip started in. */
suspend fun SettingsStore.setLastTripVehicle(address: String) {
    dataStore.edit { it[LAST_TRIP_VEHICLE] = address }
}
