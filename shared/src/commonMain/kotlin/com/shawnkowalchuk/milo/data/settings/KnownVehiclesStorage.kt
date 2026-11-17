package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// The vehicles every phone of an online backup account has (ADR-018, 2026-11-16, Shawn's choice
// "Match by name, ask once"): each phone's paired vehicles, and which of an iPhone's are the
// same vehicle as one of Android's. Android knows a vehicle by its Bluetooth address, which an
// iPhone cannot read: an iPhone's own vehicle has an id of its own ([IPHONE_VEHICLE_PREFIX]),
// and when Android pairs a vehicle of the same name it asks once whether it is the iPhone's;
// yes joins the two ids, no is remembered. No trip or reading is rewritten: a join is read
// wherever a vehicle's trips and readings are added up ([sameVehicle]).
//
// Kept in the settings file as one JSON value, as this phone last knew it from the backup
// (`shared/vehicles`), merged as a set: a vehicle, a join or a no that one phone has is never
// taken away by another.

/** How an iPhone's own vehicle's id begins: what tells it from a Bluetooth address. */
const val IPHONE_VEHICLE_PREFIX = "iphone:"

/** One vehicle of the account: its id (a Bluetooth address, or an iPhone's own), its name. */
@Serializable
data class KnownVehicle(val id: String, val name: String? = null) {
    /** Made on an iPhone, which knows no Bluetooth address. */
    val ofIphone: Boolean get() = id.startsWith(IPHONE_VEHICLE_PREFIX)
}

/** An iPhone's vehicle and an Android one that are the same vehicle, or were said not to be. */
@Serializable
data class VehicleJoin(val iphone: String, val android: String)

/** The account's vehicles, the joins, and the joins said no to. */
@Serializable
data class KnownVehicles(
    val vehicles: List<KnownVehicle> = emptyList(),
    val joins: List<VehicleJoin> = emptyList(),
    val declined: List<VehicleJoin> = emptyList(),
) {
    /**
     * Both, as one: every vehicle of either (a name of [this] kept where both have one), every
     * join and every no. Sorted, so that the same set always reads the same.
     */
    fun with(other: KnownVehicles): KnownVehicles {
        val byId = LinkedHashMap<String, KnownVehicle>()
        for (vehicle in other.vehicles + vehicles) {
            val before = byId[vehicle.id]
            byId[vehicle.id] = if (vehicle.name == null && before != null) before else vehicle
        }
        return KnownVehicles(
            vehicles = byId.values.sortedBy { it.id },
            joins = (joins + other.joins).distinct().sortedWith(
                compareBy({
                    it.iphone
                }, { it.android }),
            ),
            declined =
                (declined + other.declined).distinct()
                    .sortedWith(compareBy({ it.iphone }, { it.android })),
        )
    }
}

private val KNOWN_VEHICLES = stringPreferencesKey("known_vehicles")

private val KnownVehiclesJson = Json { ignoreUnknownKeys = true }

/** As stored; nothing where none is, or what is stored cannot be read. */
internal fun Preferences.readKnownVehicles(): KnownVehicles =
    this[KNOWN_VEHICLES]?.let(::knownVehiclesOf) ?: KnownVehicles()

/** [json] as the account's vehicles, or null if it is not that. */
fun knownVehiclesOf(json: String): KnownVehicles? = try {
    KnownVehiclesJson.decodeFromString(KnownVehicles.serializer(), json)
} catch (unreadable: SerializationException) {
    null
} catch (unreadable: IllegalArgumentException) {
    null
}

/** [vehicles] as JSON, the form the backup and the settings file keep. */
fun knownVehiclesJson(vehicles: KnownVehicles): String =
    KnownVehiclesJson.encodeToString(KnownVehicles.serializer(), vehicles)

/** Adds what another phone knows to what this one does. Returns whether anything was new. */
suspend fun SettingsStore.mergeKnownVehicles(other: KnownVehicles): Boolean {
    var changed = false
    dataStore.edit { stored ->
        val before = stored.readKnownVehicles()
        val after = before.with(other)
        if (after != before) {
            stored.writeKnownVehicles(after)
            changed = true
        }
    }
    return changed
}

/** Android's answer to "Is this the Ford from your iPhone?": [same] joins them, no is kept. */
suspend fun SettingsStore.answerJoin(iphone: String, android: String, same: Boolean) {
    val join = VehicleJoin(iphone, android)
    dataStore.edit { stored ->
        val before = stored.readKnownVehicles()
        stored.writeKnownVehicles(
            if (same) {
                before.copy(joins = before.joins + join)
            } else {
                before.copy(
                    declined =
                        before.declined + join,
                )
            },
        )
    }
}

internal fun MutablePreferences.writeKnownVehicles(vehicles: KnownVehicles) {
    this[KNOWN_VEHICLES] = knownVehiclesJson(vehicles.with(KnownVehicles()))
}

/**
 * Every id that is the same vehicle as [id]: [id] and the ids joined to it, in either
 * direction. A trip or a reading naming any of them counts for the one vehicle.
 */
fun MiloSettings.sameVehicle(id: String): Set<String> {
    val joined =
        knownVehicles.joins.flatMap { join ->
            when {
                join.iphone.equals(id, ignoreCase = true) -> listOf(join.android)
                join.android.equals(id, ignoreCase = true) -> listOf(join.iphone)
                else -> emptyList()
            }
        }
    return setOf(id) + joined
}

/**
 * The account's vehicles as this phone uploads them: its own paired vehicles, with their names,
 * and what it knows of the others'.
 */
fun MiloSettings.accountVehicles(): KnownVehicles = knownVehicles.with(
    KnownVehicles(
        vehicles = pairedVehicles().map {
            KnownVehicle(it.address, it.name)
        },
    ),
)
