package com.shawnkowalchuk.milo.feature.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.service.ServiceBook
import com.shawnkowalchuk.milo.core.service.ServiceDue
import com.shawnkowalchuk.milo.core.service.ServiceItem
import com.shawnkowalchuk.milo.core.service.ServiceRecord
import com.shawnkowalchuk.milo.core.service.serviceDue
import com.shawnkowalchuk.milo.core.service.vehicleOf
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.pairedVehicles
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.serviceOdometersKm
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/** One item as the screen shows it: the item, when it is due, and what was done. */
data class ServiceLine(val item: ServiceItem, val due: ServiceDue, val history: List<ServiceRecord>)

/**
 * One vehicle's items.
 *
 * @param name the vehicle's name, or null where only one vehicle is paired, or none.
 * @param address the vehicle's address, or null while none is paired: a new item is for it.
 * @param odometerKm its odometer now, in whole kilometres, or null with no reading.
 */
data class ServiceVehicle(
    val name: String?,
    val address: String?,
    val odometerKm: Long?,
    val lines: List<ServiceLine>,
)

/** What the Service screen shows; null vehicles while the settings are being read. */
data class ServiceUiState(val vehicles: List<ServiceVehicle>?, val unit: DistanceUnit)

/**
 * The Service screen's link to the service book in the settings file and to the stored trips,
 * from which each vehicle's odometer is worked out as Home works it out (since 2026-10-16).
 *
 * @param change writes the book: it is handed the book as stored and returns the new one.
 */
class ServiceViewModel(
    settings: Flow<MiloSettings>,
    finishedTrips: Flow<List<Trip>>,
    private val change: suspend ((ServiceBook) -> ServiceBook) -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    val state: StateFlow<ServiceUiState> =
        combine(
            settings.catch { if (it is IOException) emit(MiloSettings()) else throw it },
            finishedTrips,
        ) { stored, finished -> serviceState(stored, finished, clock(), zone()) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
                ServiceUiState(null, DistanceUnit.KILOMETRES),
            )

    fun onSave(item: ServiceItem) = write { it.saved(item) }

    fun onDelete(itemId: Long) = write { it.deleted(itemId) }

    /** "Done" for [itemId], now, at [odometerKm], with what was typed beside it. */
    fun onDone(itemId: Long, odometerKm: Long?, note: String?, costCents: Long?) = write {
        it.done(ServiceRecord(itemId, clock(), odometerKm, note?.ifBlank { null }, costCents))
    }

    private fun write(update: (ServiceBook) -> ServiceBook) {
        viewModelScope.launch {
            try {
                change(update)
            } catch (notStored: IOException) {
                // The screen follows storage, which kept the book as it was.
                return@launch
            }
        }
    }
}

/**
 * The screen's vehicles: each paired vehicle with its items, in the order they were paired; one
 * unnamed vehicle while none or one is paired. An item of a vehicle that is no longer paired is
 * shown under the first, so that nothing is lost from sight.
 */
internal fun serviceState(
    settings: MiloSettings,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
): ServiceUiState {
    val odometerKmOf = serviceOdometersKm(settings, finished, nowMs, zone)
    val paired = settings.pairedVehicles()
    val addresses = paired.map { it.address }
    val book = settings.serviceBook
    fun lines(items: List<ServiceItem>) = items.sortedBy { it.name.lowercase() }.map {
        ServiceLine(it, serviceDue(it, odometerKmOf(it), nowMs, zone), book.historyOf(it.id))
    }

    // The odometer of a group is the one its items go by: an item of its vehicle shows it.
    fun odometerKm(address: String?) =
        odometerKmOf(ServiceItem(0, address, "", null, null, 0, null))
    if (paired.size <= 1) {
        val address = paired.firstOrNull()?.address
        return ServiceUiState(
            listOf(ServiceVehicle(null, address, odometerKm(address), lines(book.items))),
            settings.distanceUnit,
        )
    }
    return ServiceUiState(
        paired.map { vehicle ->
            val items = book.items.filter { vehicleOf(it, addresses) == vehicle.address }
            ServiceVehicle(
                vehicle.name ?: vehicle.address,
                vehicle.address,
                odometerKm(vehicle.address),
                lines(items),
            )
        },
        settings.distanceUnit,
    )
}
