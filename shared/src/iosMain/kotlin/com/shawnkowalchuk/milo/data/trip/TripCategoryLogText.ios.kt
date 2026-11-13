package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripClassification
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.localDateTimeOf
import com.shawnkowalchuk.milo.core.time.twoDigits

private const val SHORT_DAY = 3

// The log's line about a classification, word for word as Android's (TripCategoryLogText.android.kt).

actual fun classificationText(
    classification: TripClassification,
    startedAtMs: Long,
    schedule: WorkSchedule,
    zone: ZoneId,
): String {
    val start = localDateTimeOf(startedAtMs, zone)
    val day = start.dayOfWeek.name.take(SHORT_DAY).lowercase().replaceFirstChar { it.uppercase() }
    val hours = schedule.on(start.dayOfWeek)
    val judgedBy =
        if (hours.tracked) {
            "that day's hours are ${hours.start} to ${hours.end}"
        } else {
            "$day is not a tracked day"
        }
    val time = twoDigits(start.hour) + ":" + twoDigits(start.minute) + ":" + twoDigits(start.second)
    val started = "it started on a $day at $time"
    return "${classification.inWords()}: $started; $judgedBy ($zone)"
}
