package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * What's new and the first start on the iPhone (2026-11-16): the welcome page on a fresh
 * install only, What's new once after an update, and the iPhone's own list readable.
 */
@OptIn(ExperimentalForeignApi::class)
class IosWhatsNewTest {
    private val world = RecorderWorld()
    private val whatsNew =
        IosWhatsNew(world.settings, world.trips, world.log, { Locale("en_CA") }, { 1_000L })

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun `a fresh install gets the welcome page and not the list of its own version`() = runTest {
        assertEquals("INTRO", whatsNew.stage("1.0.1"))
        // Its own start writes to the log; closed on the welcome page, it shows it again.
        world.log.add(500L, EventCategory.TRIGGER, "The iPhone app picked up the stored state")
        assertEquals("INTRO", whatsNew.stage("1.0.1"))

        whatsNew.welcomeDone()
        assertEquals("SETUP", whatsNew.stage("1.0.1"))
        whatsNew.setupDone()

        assertEquals("DONE", whatsNew.stage("1.0.1"))
        assertFalse(whatsNew.due("1.0.1"), "the welcome page said what Dually does")
        assertTrue(whatsNew.due("1.0.2"), "the next update's list is shown once")
        whatsNew.shown("1.0.2")
        assertFalse(whatsNew.due("1.0.2"))
    }

    @Test
    fun `an iPhone that used 1_0 skips the welcome page and sees the update's list`() = runTest {
        world.log.add(500L, EventCategory.TRIP, "Trip started")

        assertEquals("DONE", whatsNew.stage("1.0.1"))
        assertTrue(whatsNew.due("1.0.1"))
    }

    @Test
    fun `the iPhone's own list is read and its version marked`() = runTest {
        val text = NSString.stringWithContentsOfFile(IPHONE_CHANGELOG, NSUTF8StringEncoding, null)
        kotlin.test.assertNotNull(text, "no list at $IPHONE_CHANGELOG")

        val list = whatsNew.list(text, installed = "1.0")

        assertFalse(list.unreadable)
        assertTrue(list.releases.first().heading.contains("not released yet"))
        assertTrue(
            list.releases.any {
                it.heading.startsWith("1.0 · ") &&
                    it.heading.endsWith("on this phone")
            },
        )
    }

    @Test
    fun `a broken list says so and logs why`() = runTest {
        val list = whatsNew.list("""{ "releases": [ { "version": "1.0" } ] }""", installed = "1.0")

        assertTrue(list.unreadable)
        assertTrue(world.logLines().any { it.category == EventCategory.ERROR })
    }

    private companion object {
        /** The repository, as Gradle tells the simulator (`shared/build.gradle.kts`). */
        val IPHONE_CHANGELOG =
            (NSProcessInfo.processInfo.environment["DUALLY_REPOSITORY"] as? String) +
                "/ios/Dually/changelog.json"
    }
}
