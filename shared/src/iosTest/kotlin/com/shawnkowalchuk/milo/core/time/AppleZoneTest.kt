package com.shawnkowalchuk.milo.core.time

import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

/**
 * The iPhone's zones follow Apple's rules, the ones the iPhone's clock shows, and not
 * kotlinx-datetime's own time zone files, which lagged behind Apple's for Alberta in 2026
 * (FINDINGS_LOG, 2026-11-13).
 */
class AppleZoneTest {
    private val edmonton = zoneIdOf("America/Edmonton")
    private val toronto = zoneIdOf("America/Toronto")

    @Test
    fun `the date of a moment is the one Apple's calendar gives every six hours of 2026`() {
        val calendar = NSCalendar(NSCalendarIdentifierGregorian)
        calendar.timeZone = NSTimeZone.timeZoneWithName("America/Edmonton")!!
        var ms = JAN_1_2026_UTC
        while (ms < JAN_1_2027_UTC) {
            val parts =
                calendar.components(
                    NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
                    NSDate.dateWithTimeIntervalSince1970(ms / 1000.0),
                )
            val apple = dateOf(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
            assertEquals(apple, localDateOf(ms, edmonton), "at $ms")
            ms += SIX_HOURS_MS
        }
    }

    @Test
    fun `Edmonton stays at UTC-6 after 1 November 2026 as Apple's time zone data has it`() {
        // 2026-11-14 06:30 UTC: 00:30 on the 14th at UTC-6, and 23:30 on the 13th at UTC-7,
        // which kotlinx-datetime's own files gave.
        assertEquals(dateOf(2026, 11, 14), localDateOf(1_794_637_800_000L, edmonton))
        assertEquals(
            "2026-11-14T00:30-06:00",
            formatOffsetMoment(1_794_637_800_000L, edmonton),
        )
        // The 13th starts at 06:00 UTC.
        assertEquals(1_794_549_600_000L, daySpan(dateOf(2026, 11, 13), edmonton).fromMs)
    }

    @Test
    fun `a time the clocks jump over moves on by the jump as java_time's`() {
        // Toronto, 8 March 2026: 02:00 EST becomes 03:00 EDT. 02:30 is read as 03:30 EDT.
        assertEquals(
            moment("2026-03-08T07:30:00Z"),
            epochMsOf(LocalDateTime(2026, 3, 8, 2, 30), toronto),
        )
    }

    @Test
    fun `a time that happens twice is the first of the two as java_time's`() {
        // Toronto, 1 November 2026: 02:00 EDT becomes 01:00 EST. 01:30 is first at UTC-4.
        assertEquals(
            moment("2026-11-01T05:30:00Z"),
            epochMsOf(LocalDateTime(2026, 11, 1, 1, 30), toronto),
        )
    }

    private fun moment(iso: String): Long = kotlin.time.Instant.parse(iso).toEpochMilliseconds()

    private companion object {
        const val JAN_1_2026_UTC = 1_767_225_600_000L
        const val JAN_1_2027_UTC = 1_798_761_600_000L
        const val SIX_HOURS_MS = 6 * 3_600_000L
    }
}
