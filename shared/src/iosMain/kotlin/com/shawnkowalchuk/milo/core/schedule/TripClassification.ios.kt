package com.shawnkowalchuk.milo.core.schedule

import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.epochMsOf
import com.shawnkowalchuk.milo.core.time.localDateTimeOf
import kotlinx.datetime.LocalDateTime

actual fun workHoursEndMs(startedAtMs: Long, schedule: WorkSchedule, zone: ZoneId): Long? {
    val start = localDateTimeOf(startedAtMs, zone)
    val hours = schedule.on(start.dayOfWeek)
    val timeOfDay = start.time
    val insideHours = hours.tracked && timeOfDay >= hours.start && timeOfDay < hours.end
    if (!insideHours) return null
    // As on Android: an end time the clocks jump over moves on by the jump; an end time that
    // happens twice is the first.
    return epochMsOf(LocalDateTime(start.date, hours.end), zone)
}
