package com.shawnkowalchuk.milo.core.service

import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How near an item's next service is. */
enum class DueState { OK, SOON, DUE }

/** "Soon" at most this far before a distance falls due, or a tenth of the interval if longer. */
private const val SOON_KM = 500L
private const val SOON_SHARE = 10

/** "Soon" this many days before a date falls due. */
private const val SOON_DAYS = 14L

/**
 * When [ServiceItem]'s next service is due, and how near it is.
 *
 * @param kmLeft how far until it is due by distance, or null where there is no distance to go by
 * (no interval, no odometer reading); below zero once past.
 * @param dueOn the day it is due by time, or null without a time interval.
 * @param daysLeft the days from today to [dueOn]; below zero once past.
 */
data class ServiceDue(
    val state: DueState,
    val kmLeft: Long?,
    val dueOn: LocalDate?,
    val daysLeft: Long?,
)

/**
 * Works out [item]'s next due point: by distance from its last service's odometer, by time from
 * its last service's day, whichever comes first. Due at or past either; soon within the larger of
 * 500 km and a tenth of the distance, or within two weeks.
 *
 * @param odometerKm the vehicle's odometer now, in whole kilometres, or null with no reading.
 */
fun serviceDue(item: ServiceItem, odometerKm: Long?, nowMs: Long, zone: ZoneId): ServiceDue {
    val kmLeft =
        if (item.everyKm != null && item.lastDoneKm != null && odometerKm != null) {
            item.lastDoneKm + item.everyKm - odometerKm
        } else {
            null
        }
    val dueOn = item.everyMonths?.let {
        localDateOf(item.lastDoneAtMs, zone).plusMonths(it.toLong())
    }
    val daysLeft = dueOn?.let { ChronoUnit.DAYS.between(localDateOf(nowMs, zone), it) }
    val soonKm = maxOf(SOON_KM, (item.everyKm ?: 0).toLong() / SOON_SHARE)
    val state =
        when {
            (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0) -> DueState.DUE

            (kmLeft != null && kmLeft <= soonKm) || (daysLeft != null && daysLeft <= SOON_DAYS) ->
                DueState.SOON

            else -> DueState.OK
        }
    return ServiceDue(state, kmLeft, dueOn, daysLeft)
}
