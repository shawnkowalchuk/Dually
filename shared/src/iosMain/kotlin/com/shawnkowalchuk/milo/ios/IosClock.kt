package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.clock.ClockAnchor
import com.shawnkowalchuk.milo.core.clock.TrustedClock
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.clock.NO_BOOT_NUMBER
import com.shawnkowalchuk.milo.platform.clock.clockNewsText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.darwin.sysctlbyname
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np
import platform.posix.size_tVar

// Dually's own clock on the iPhone (2026-11-16), Android's (ADR-006): Dually keeps its own time,
// anchored on the clock that counts from the boot and checked against the phone's at every
// reading, so a date set a day ahead for a few seconds (Shawn's Candy Crush lives) ends no trip,
// dates none tomorrow and brings no reminder early. The rule is the shared `TrustedClock`; this
// is the iPhone's side: its two clocks, the number of this boot, and where the anchor is kept.
//
// THE ONE PLACE IN THE IPHONE'S SHARED CODE THAT READS THE PHONE'S WALL CLOCK: everything else
// is handed `IosClock.now`, as on Android (`PhoneClock.kt`).
@OptIn(ExperimentalForeignApi::class)
object IosClock {
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val MILLIS_PER_SECOND = 1000.0
    private const val ANCHOR_BOOT = "clock_anchor_boot"
    private const val ANCHOR_WALL = "clock_anchor_wall_ms"
    private const val ANCHOR_ELAPSED = "clock_anchor_elapsed_ms"
    private const val ANCHOR_PROBATION = "clock_anchor_probation_since_ms"

    private val defaults = NSUserDefaults.standardUserDefaults
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val clock: TrustedClock by lazy {
        TrustedClock(
            phoneNow = { (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong() },
            elapsedNow = ::elapsedMs,
            bootCount = bootNumber(),
            stored = storedAnchor(),
            save = ::store,
        )
    }

    /** Dually's time now, in epoch milliseconds: the phone's, unless its clock has jumped. */
    fun now(): Long = clock.now()

    /**
     * What the clock that counts from the boot reads now, in milliseconds. It goes on while the
     * iPhone sleeps (Darwin's `CLOCK_MONOTONIC`), as Android's `elapsedRealtime` does, and no
     * one can set it. A GPS fix's moment on it gives the fix its time by Dually's clock.
     */
    fun elapsedMs(): Long =
        (clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()).toLong()) / NANOS_PER_MILLI

    /** Dually's time at a moment of the clock that counts from the boot. */
    fun atElapsed(elapsedAtMs: Long): Long = now() - (elapsedMs() - elapsedAtMs)

    /**
     * The clock's lines go to the event log from now on, in Android's words; a start that has no
     * boot number says so, as on Android.
     */
    fun logTo(eventLog: EventLogRepository) {
        clock.tellNewsTo { news ->
            val line = clockNewsText(news) ?: return@tellNewsTo
            scope.launch { eventLog.add(now(), EventCategory.PROCESS, line) }
        }
        if (!clock.keptAcrossStarts) {
            scope.launch {
                eventLog.add(now(), EventCategory.ERROR, NO_BOOT_NUMBER.replace("phone", "iPhone"))
            }
        }
    }

    /**
     * This boot's number: iOS gives every boot its own identifier (`kern.bootsessionuuid`), and
     * its hash stands for Android's boot count. Null where it cannot be read: the anchor is then
     * not kept from one start to the next, as on Android.
     */
    private fun bootNumber(): Int? = memScoped {
        val size = alloc<size_tVar>()
        if (sysctlbyname("kern.bootsessionuuid", null, size.ptr, null, 0u) != 0) return null
        val buffer = allocArray<kotlinx.cinterop.ByteVar>(size.value.toInt())
        if (sysctlbyname("kern.bootsessionuuid", buffer, size.ptr, null, 0u) != 0) return null
        buffer.toKString().takeIf { it.isNotBlank() }?.hashCode()
    }

    private fun storedAnchor(): ClockAnchor? {
        if (defaults.objectForKey(ANCHOR_BOOT) == null) return null
        return ClockAnchor(
            bootCount = defaults.integerForKey(ANCHOR_BOOT).toInt(),
            wallMs = defaults.doubleForKey(ANCHOR_WALL).toLong(),
            elapsedMs = defaults.doubleForKey(ANCHOR_ELAPSED).toLong(),
            onProbationSinceMs =
                defaults.objectForKey(ANCHOR_PROBATION)?.let {
                    defaults.doubleForKey(ANCHOR_PROBATION).toLong()
                },
        )
    }

    private fun store(anchor: ClockAnchor) {
        defaults.setInteger(anchor.bootCount.toLong(), ANCHOR_BOOT)
        defaults.setDouble(anchor.wallMs.toDouble(), ANCHOR_WALL)
        defaults.setDouble(anchor.elapsedMs.toDouble(), ANCHOR_ELAPSED)
        val probation = anchor.onProbationSinceMs
        if (probation == null) {
            defaults.removeObjectForKey(ANCHOR_PROBATION)
        } else {
            defaults.setDouble(probation.toDouble(), ANCHOR_PROBATION)
        }
    }
}
