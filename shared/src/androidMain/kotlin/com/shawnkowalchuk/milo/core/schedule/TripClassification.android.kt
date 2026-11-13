package com.shawnkowalchuk.milo.core.schedule

import java.time.Instant
import java.time.ZoneId

actual fun workHoursEndMs(startedAtMs: Long, schedule: WorkSchedule, zone: ZoneId): Long? {
    val start = Instant.ofEpochMilli(startedAtMs).atZone(zone)
    val hours = schedule.on(start.dayOfWeek)
    val timeOfDay = start.toLocalTime()
    val insideHours = hours.tracked && timeOfDay >= hours.start && timeOfDay < hours.end
    if (!insideHours) return null
    // If the clocks jump over the end time itself, java.time moves it on by the length of the
    // jump; if the end time happens twice, it takes the first.
    return start.toLocalDate().atTime(hours.end).atZone(zone).toInstant().toEpochMilli()
}
