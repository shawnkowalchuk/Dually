package com.shawnkowalchuk.milo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

actual fun monthSpan(month: YearMonth, zone: ZoneId): TimeSpan = TimeSpan(
    fromMs = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    untilMs = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

actual fun daySpan(day: LocalDate, zone: ZoneId): TimeSpan = TimeSpan(
    fromMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
    untilMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

actual fun daysSpan(first: LocalDate, last: LocalDate, zone: ZoneId): TimeSpan {
    require(!last.isBefore(first)) { "The days end ($last) before they start ($first)" }
    return TimeSpan(
        fromMs = first.atStartOfDay(zone).toInstant().toEpochMilli(),
        untilMs = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

actual fun localDateOf(epochMs: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

actual fun monthOf(epochMs: Long, zone: ZoneId): YearMonth =
    YearMonth.from(localDateOf(epochMs, zone))
