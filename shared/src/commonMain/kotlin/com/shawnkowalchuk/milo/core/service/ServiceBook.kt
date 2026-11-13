package com.shawnkowalchuk.milo.core.service

import kotlinx.serialization.Serializable

// The service book (since 2026-10-16; Shawn: "a button ... to track and set reminders for
// service, oil change, maintenance"). His answers: called Service; due by distance or time,
// whichever comes first; reminders and a history; a notification and a tile on Home. Pure values,
// stored whole in the settings file as JSON (`ServiceStorage.kt`). Distances are kilometres,
// whatever unit is shown, as the odometer's readings are kept in the unit they were typed in and
// worked out in either.

/**
 * One thing to look after.
 *
 * @param id its own number, unique in the book.
 * @param vehicle the paired vehicle's address, or null while none is paired.
 * @param everyKm how far between two services, or null; [everyMonths] how long, or null. At
 * least one is set.
 * @param lastDoneAtMs and [lastDoneKm] the last service: when, and at what odometer (km). The
 * next is due from them.
 */
@Serializable
data class ServiceItem(
    val id: Long,
    val vehicle: String?,
    val name: String,
    val everyKm: Int?,
    val everyMonths: Int?,
    val lastDoneAtMs: Long,
    val lastDoneKm: Long?,
)

/** One service that was done: when, at what odometer (km), and what Shawn noted of it. */
@Serializable
data class ServiceRecord(
    val itemId: Long,
    val doneAtMs: Long,
    val odometerKm: Long?,
    val note: String? = null,
    val costCents: Long? = null,
)

/**
 * The whole book.
 *
 * @param remindedFor the items whose notification has gone out for their present due point,
 * by id and the moment they were last done, so that each due point is told once.
 */
@Serializable
data class ServiceBook(
    val items: List<ServiceItem> = emptyList(),
    val records: List<ServiceRecord> = emptyList(),
    val remindedFor: Map<Long, Long> = emptyMap(),
) {
    /** The book with [item] in it, replacing the one with its id. A new one gets the next id. */
    fun saved(item: ServiceItem): ServiceBook {
        val nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        val stored = if (item.id == 0L) item.copy(id = nextId) else item
        return copy(items = items.filterNot { it.id == stored.id } + stored)
    }

    /** The book without [itemId], and without its records. */
    fun deleted(itemId: Long): ServiceBook = copy(
        items = items.filterNot { it.id == itemId },
        records = records.filterNot { it.itemId == itemId },
        remindedFor = remindedFor - itemId,
    )

    /** The book after [record]: kept in the history, and the item due again from it. */
    fun done(record: ServiceRecord): ServiceBook = copy(
        items =
            items.map {
                if (it.id == record.itemId) {
                    it.copy(
                        lastDoneAtMs = record.doneAtMs,
                        lastDoneKm = record.odometerKm ?: it.lastDoneKm,
                    )
                } else {
                    it
                }
            },
        records = records + record,
        remindedFor = remindedFor - record.itemId,
    )

    /** [itemId]'s history, newest first. */
    fun historyOf(itemId: Long): List<ServiceRecord> =
        records.filter { it.itemId == itemId }.sortedByDescending { it.doneAtMs }
}
