package com.shawnkowalchuk.milo.ios

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * Dually's own clock on the iPhone (2026-11-16, ADR-006): with nobody setting the date, its time
 * is the phone's, the clock that counts from the boot only goes forward, and a moment on that
 * clock is dated by it. The rule itself is tested in commonTest (`TrustedClock`).
 */
class IosClockTest {
    @Test
    fun `with the date untouched Dually's time is the phone's`() {
        val phone = (NSDate().timeIntervalSince1970 * 1000).toLong()
        assertTrue(abs(IosClock.now() - phone) < 2_000, "within a moment of the phone's clock")
    }

    @Test
    fun `a moment of the boot clock is dated by Dually's time`() {
        val before = IosClock.elapsedMs()
        val after = IosClock.elapsedMs()
        assertTrue(after >= before, "the boot clock only goes forward")
        val dated = IosClock.atElapsed(before - 5_000)
        assertTrue(
            abs(IosClock.now() - 5_000 - dated) < 100,
            "five seconds ago is five seconds ago",
        )
    }
}
