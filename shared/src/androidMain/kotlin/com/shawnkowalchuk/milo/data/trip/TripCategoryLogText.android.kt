package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripClassification
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// How the event log words what the work schedule made of a trip. Plain functions, so that what
// is logged can be read (and tested) in one place. Like the rest of the event log the lines are
// English only. Each one names the day, the time and the hours the trip was judged by, because
// the schedule can be changed afterwards and the log must still say why a trip is what it is.

// To the second: a trip that starts forty seconds after the end time must not read as having
// started on it.
private val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)

actual fun classificationText(
    classification: TripClassification,
    startedAtMs: Long,
    schedule: WorkSchedule,
    zone: ZoneId,
): String {
    val start = Instant.ofEpochMilli(startedAtMs).atZone(zone)
    val day = start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val hours = schedule.on(start.dayOfWeek)
    val judgedBy =
        if (hours.tracked) {
            "that day's hours are ${hours.start} to ${hours.end}"
        } else {
            "$day is not a tracked day"
        }
    val started = "it started on a $day at ${TIME_OF_DAY.format(start)}"
    return "${classification.inWords()}: $started; $judgedBy ($zone)"
}
