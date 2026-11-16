package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The iPhone's Log screen (2026-11-15): Android's chips, its paging, its tags and its file. */
class IosLogTest {
    private val world = RecorderWorld()
    private val log = IosLog(world.log)

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `the newest lines come first a page at a time and a chip narrows them`() = runTest {
        world.log.add(1_000, EventCategory.TRIP, "Trip 1 started")
        world.log.add(
            2_000,
            EventCategory.TRIGGER,
            "The Shortcuts automation said the truck connected",
        )
        world.log.add(3_000, EventCategory.ERROR, "Something failed", "the details")
        world.log.add(4_000, EventCategory.CLOUD, "Backed up")

        val two = log.page(group = null, limit = 2)
        assertTrue(two.more)
        val lines = two.days.flatMap { it.lines }
        assertEquals(listOf("Backed up", "Something failed"), lines.map { it.message })
        assertNull(lines[0].group, "a cloud line is under All only")
        assertEquals("the details", lines[1].detail)

        val everything = log.page(group = null, limit = 10)
        assertFalse(everything.more)
        assertEquals(4, everything.days.sumOf { it.lines.size })

        val truck = log.page(group = "BLUETOOTH", limit = 10).days.flatMap { it.lines }
        assertEquals(
            listOf("TRUCK"),
            truck.map {
                it.tag
            },
            "a trigger line is the truck's on the iPhone",
        )

        val trips = log.page(group = "TRIPS", limit = 10).days.flatMap { it.lines }
        assertEquals(listOf("TRIP"), trips.map { it.tag })

        assertTrue(log.page(group = "ERRORS", limit = 10).days.isNotEmpty())
    }

    @Test
    fun `an empty chip says so and the file holds every line oldest first`() = runTest {
        assertEquals(
            "The log has no error and no crash.",
            log.page(group = "ERRORS", limit = 10).empty,
        )
        world.log.add(1_000, EventCategory.TRIP, "First")
        world.log.add(2_000, EventCategory.ERROR, "Second", "line one\nline two")

        val text = log.fileText(nowMs = 3_000)
        assertTrue(text.startsWith("Dually event log\n"))
        assertTrue(text.contains("2 lines, oldest first."))
        assertTrue(text.indexOf("TRIP  First") < text.indexOf("ERROR  Second"))
        assertTrue(text.contains("    line one\n    line two\n"))
        assertTrue(log.fileName(3_000).startsWith("Dually-log-"))
    }
}
