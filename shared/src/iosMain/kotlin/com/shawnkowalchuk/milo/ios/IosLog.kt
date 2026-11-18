package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.data.eventlog.EVENT_LOG_KEEP_DAYS
import com.shawnkowalchuk.milo.data.eventlog.EVENT_LOG_KEEP_NEWEST
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.feature.eventlog.LogGroup
import com.shawnkowalchuk.milo.feature.eventlog.categories
import com.shawnkowalchuk.milo.feature.eventlog.logGroup
import com.shawnkowalchuk.milo.feature.eventlog.tagWord
import kotlinx.coroutines.flow.first
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

/**
 * The iPhone's Log screen (2026-11-15), as Android's: the event log's newest lines, a tile for
 * each day, each line with its time to the second, its tag and what it says; narrowed by the
 * same chips (`logGroup`, shared since that day); and the whole log as Android's text file, for
 * the share sheet. Times are in the iPhone's own time zone, in the log's fixed forms
 * ("08:14:03", "Sun 2026-11-15"), whatever the phone's language: the log is evidence.
 */
class IosLog internal constructor(private val log: EventLogRepository) {
    /**
     * The newest [limit] lines, of [group] only ("TRIPS", "BLUETOOTH", "ERRORS"), or of every
     * kind for null. [LogPage.more] says whether older lines are left.
     */
    suspend fun page(group: String?, limit: Int): LogPage {
        val chosen = group?.let { name -> LogGroup.entries.firstOrNull { it.name == name } }
        // One more than shown, to know whether "Show older entries" has anything to show.
        val read = log.observeNewest(limit + 1, chosen?.categories()).first()
        val shown = read.take(limit)
        val days =
            shown.groupBy { formatted(it.atMs, DAY) }.map { (day, lines) ->
                LogDayText(day, lines.map(::lineText))
            }
        return LogPage(days = days, more = read.size > limit, empty = emptyWords(chosen))
    }

    /** The file's name, "Dually-log-2026-11-15.txt", as Android names it. */
    fun fileName(nowMs: Long): String = "Dually-log-" + formatted(nowMs, FILE_DAY) + ".txt"

    /** The whole log, oldest first, under Android's header: the text of Android's shared file. */
    suspend fun fileText(nowMs: Long): String {
        val lines = StringBuilder()
        var count = 0
        log.readAll { page ->
            page.forEach { entry ->
                count++
                lines.append(formatted(entry.atMs, FILE_TIME))
                lines.append("  ").append(entry.category.name)
                lines.append("  ").append(entry.message).append('\n')
                entry.detail?.lines()?.forEach {
                    lines.append(DETAIL_INDENT).append(it).append('\n')
                }
            }
        }
        val zone = NSTimeZone.localTimeZone.name
        return "Dually event log\n" +
            "Written ${formatted(nowMs, FILE_TIME)}. Every time is in $zone.\n" +
            "$count lines, oldest first. Dually removes lines older than $EVENT_LOG_KEEP_DAYS " +
            "days, except the newest $EVENT_LOG_KEEP_NEWEST.\n\n" +
            lines
    }

    private fun lineText(entry: EventLogEntry) = LogLineText(
        id = entry.id,
        time = formatted(entry.atMs, CLOCK),
        tag = tagOnIphone(entry.category),
        group = entry.category.logGroup()?.name,
        message = entry.message,
        detail = entry.detail,
    )

    // A trigger line on the iPhone is the Shortcuts automation's report of the truck, over
    // Bluetooth or CarPlay, so its tag says TRUCK where Android's says BT.
    private fun tagOnIphone(category: EventCategory): String =
        if (category == EventCategory.TRIGGER) "TRUCK" else category.tagWord()

    private fun emptyWords(group: LogGroup?): String = when (group) {
        null -> "The event log is empty."
        LogGroup.TRIPS -> "The log has no line about a trip."
        LogGroup.BLUETOOTH -> "The log has no line about the truck's connection."
        LogGroup.ANDROID_AUTO -> "The log has no line about CarPlay."
        LogGroup.ERRORS -> "The log has no error and no crash."
    }

    private fun formatted(atMs: Long, pattern: String): String {
        val formatter = NSDateFormatter()
        formatter.locale = NSLocale(localeIdentifier = "en_US_POSIX")
        formatter.timeZone = NSTimeZone.localTimeZone
        formatter.dateFormat = pattern
        return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(atMs / 1000.0))
    }

    private companion object {
        const val CLOCK = "HH:mm:ss"
        const val DAY = "EEE yyyy-MM-dd"
        const val FILE_DAY = "yyyy-MM-dd"
        const val FILE_TIME = "yyyy-MM-dd HH:mm:ss"
        const val DETAIL_INDENT = "    "
    }
}

/** A page of the Log screen: a tile for each day, newest first. */
data class LogPage(
    val days: List<LogDayText>,
    /** Older lines are left: "Show older entries". */
    val more: Boolean,
    /** What the screen says when [days] is empty, for the chip chosen. */
    val empty: String,
)

/** One day's lines: "Sun 2026-11-15", newest first. */
data class LogDayText(val title: String, val lines: List<LogLineText>)

/**
 * One line: "08:14:03", its tag ("TRIP", "TRUCK", "ERROR"), the chip it is found under
 * ("TRIPS", "BLUETOOTH", "ERRORS", or null for "All" only, which colours the tag), what it says,
 * and anything longer, shown when the line is pressed.
 */
data class LogLineText(
    val id: Long,
    val time: String,
    val tag: String,
    val group: String?,
    val message: String,
    val detail: String?,
)
