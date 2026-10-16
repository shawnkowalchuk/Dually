package com.shawnkowalchuk.milo.core.service

import java.time.ZoneId

// The service reminders (since 2026-10-16; Shawn chose "Notification + Home tile"): which items
// want his eye, and which of them the notification has not told him of yet.

/** An item that is soon or due, with when. */
data class ServiceAlert(val item: ServiceItem, val due: ServiceDue)

/**
 * The paired vehicle whose odometer [item] goes by: its own while that is paired, or else the
 * first paired one, under which the Service screen shows it; null while none is paired.
 *
 * @param paired the paired vehicles' addresses, in the order they were paired.
 */
fun vehicleOf(item: ServiceItem, paired: List<String>): String? =
    paired.firstOrNull { it.equals(item.vehicle, ignoreCase = true) } ?: paired.firstOrNull()

/**
 * The items that are soon or due, the due ones first, each group by name.
 *
 * @param odometerKmOf the odometer, in kilometres, that an item goes by now, or null.
 */
fun serviceAlerts(
    book: ServiceBook,
    odometerKmOf: (ServiceItem) -> Long?,
    nowMs: Long,
    zone: ZoneId,
): List<ServiceAlert> = book.items
    .map { ServiceAlert(it, serviceDue(it, odometerKmOf(it), nowMs, zone)) }
    .filter { it.due.state != DueState.OK }
    .sortedWith(compareBy({ it.due.state != DueState.DUE }, { it.item.name.lowercase() }))

/** Those of [alerts] whose due point has not been told yet: told once each, until Done. */
fun ServiceBook.untold(alerts: List<ServiceAlert>): List<ServiceAlert> =
    alerts.filter { remindedFor[it.item.id] != it.item.lastDoneAtMs }

/** The book with [alerts] marked as told, for their present due point. */
fun ServiceBook.told(alerts: List<ServiceAlert>): ServiceBook =
    copy(remindedFor = remindedFor + alerts.associate { it.item.id to it.item.lastDoneAtMs })
