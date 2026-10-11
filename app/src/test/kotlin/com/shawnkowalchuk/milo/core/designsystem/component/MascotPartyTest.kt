package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The film's screen, which the party is as large on as Shawn was shown: 1080 by 2400 pixels. */
private const val FILM_WIDTH = 1080 / 2.625f
private const val FILM_HEIGHT = 2400 / 2.625f

/**
 * The party's course apart from its drawing: where the mascot stands on a screen and how large,
 * how strongly he is drawn at each moment, and which stage follows which.
 */
class MascotPartyTest {
    // ---- Where he stands, and how large ----------------------------------------------------------

    @Test
    fun `on the film's screen he is as large as in the film, and stands in its middle`() {
        val place = MascotPartyPlace(FILM_WIDTH, FILM_HEIGHT)

        assertEquals(1f, place.scale, 0.0001f)
        assertEquals(MascotPartyPicture.TALL, place.tall, 0.01f)
        assertEquals(600 / 2.625f, place.tall, 0.01f)
        // The frame is as wide as the screen.
        assertEquals(0f, place.left, 0.01f)
        assertEquals(FILM_WIDTH, place.width, 0.01f)
        // The middle of his height is the middle of the screen's.
        val soles = place.top + place.height - place.soles
        assertEquals(FILM_HEIGHT / 2, soles - place.tall / 2, 0.01f)
        // The film's picture began 180 pixels down its screen, and the top 100 are cut off here.
        assertEquals(280 / 2.625f, place.top, 0.01f)
    }

    @Test
    fun `on Shawn's phone he is a little smaller and fills the same part of the width`() {
        val place = MascotPartyPlace(screenWidth = 393f, screenHeight = 873f)

        assertEquals(393f / FILM_WIDTH, place.scale, 0.0001f)
        assertEquals(393f, place.width, 0.01f)
        assertEquals(0f, place.left, 0.01f)
        assertEquals(218.3f, place.tall, 0.1f)
    }

    @Test
    fun `on a wider screen he is no larger, and stands in the middle of its width`() {
        val place = MascotPartyPlace(screenWidth = 600f, screenHeight = 960f)

        assertEquals(1f, place.scale, 0f)
        assertEquals(300f, place.left + place.axis, 0.01f)
        // Room is left on both sides of the frame.
        assertEquals(place.left, 600f - (place.left + place.width), 0.01f)
    }

    @Test
    fun `he stands in the middle of every screen, and the confetti's room never tops it`() {
        for (width in 240..1_300 step 7) {
            for (height in 240..1_300 step 11) {
                val place = MascotPartyPlace(width.toFloat(), height.toFloat())
                val which = "$width by $height"
                assertTrue(which, place.scale > 0f && place.scale <= 1f)
                assertEquals(which, width / 2f, place.left + place.axis, 0.01f)
                val soles = place.top + place.height - place.soles
                assertEquals(which, height / 2f, soles - place.tall / 2, 0.01f)
                assertTrue("$which: the frame starts at ${place.top}", place.top >= -0.01f)
                assertTrue("$which: the frame is ${place.width} wide", place.width <= width + 0.01f)
                assertTrue(which, place.top + place.height <= height + 0.01f)
            }
        }
    }

    @Test
    fun `with the phone on its side he is about half the size`() {
        val place = MascotPartyPlace(screenWidth = 914f, screenHeight = 411f)

        assertEquals(0.586f, place.scale, 0.001f)
        assertEquals(134f, place.tall, 0.5f)
        // The room above him reaches exactly to the top of the screen.
        assertEquals(0f, place.top, 0.01f)
    }

    // ---- How strongly he is drawn ----------------------------------------------------------------

    @Test
    fun `he fades in over twelve frames from nothing, eased at both ends`() {
        assertEquals(0f, MascotPartyPicture.fadeIn(0), 0f)
        assertEquals(0.5f, MascotPartyPicture.fadeIn(252), 0.0001f)
        assertEquals(1f, MascotPartyPicture.fadeIn(504), 0f)
        assertEquals(1f, MascotPartyPicture.fadeIn(3_000), 0f)
        // The film's fade: 3t² - 2t³. A quarter of the way it is 0.156, three quarters 0.844.
        assertEquals(0.15625f, MascotPartyPicture.fadeIn(126), 0.0001f)
        assertEquals(0.84375f, MascotPartyPicture.fadeIn(378), 0.0001f)
        // He is wholly there when the app lets the first picture play, and he first moves.
        assertEquals(1f, MascotPartyPicture.fadeIn(MascotPartyPicture.STILL_MS.toLong()), 0f)
    }

    @Test
    fun `he never grows fainter while he fades in, nor stronger while he fades out`() {
        var before = 0f
        for (at in 0L..600L) {
            val now = MascotPartyPicture.fadeIn(at)
            assertTrue("fading in at $at ms", now >= before)
            before = now
        }
        before = 1f
        for (at in 0L..MascotPartyPicture.POP_MS + 100L) {
            val now = MascotPartyPicture.fadeOut(at)
            assertTrue("fading out at $at ms", now <= before)
            before = now
        }
    }

    @Test
    fun `from the pop he is wholly there until twelve frames before the end, then fades`() {
        val end = MascotPartyPicture.POP_MS.toLong()
        val fade = MascotPartyPicture.FADE_MS.toLong()

        assertEquals(1f, MascotPartyPicture.fadeOut(0), 0f)
        assertEquals(1f, MascotPartyPicture.fadeOut(end - fade), 0f)
        assertEquals(0.5f, MascotPartyPicture.fadeOut(end - fade / 2), 0.0001f)
        assertEquals(0f, MascotPartyPicture.fadeOut(end), 0f)
        assertEquals(0f, MascotPartyPicture.fadeOut(end + 500), 0f)
        // The frame of the pop, the bang and the first second of confetti are not faded.
        assertEquals(1f, MascotPartyPicture.fadeOut(1_000), 0f)
    }

    @Test
    fun `the party is five and a half seconds, the popper going off three seconds in`() {
        assertEquals(3_066, MascotPartyPicture.POP_AT_MS)
        assertEquals(5_460, MascotPartyPicture.POP_AT_MS + MascotPartyPicture.POP_MS)
        // The film: frame 73 of 24 a second, 3.04 seconds in.
        assertEquals(73, MascotPartyPicture.STILL_FRAMES + MascotPartyPicture.POPPER_FRAMES)
    }

    // ---- Stage after stage -----------------------------------------------------------------------

    private class Told {
        val stages = mutableListOf<PartyStage>()
        val ends = mutableListOf<Boolean>()
        val course = PartyCourse(onStage = { stages += it }, onDone = { ends += it })
    }

    @Test
    fun `a party goes from reading to the popper, to the pop, and ends once`() {
        val told = Told()
        assertEquals(PartyStage.READING, told.course.stage)

        told.course.on(PartyEvent.PICTURES_READ)
        told.course.on(PartyEvent.POP)
        told.course.on(PartyEvent.GONE)

        assertEquals(
            listOf(PartyStage.BEFORE_THE_POP, PartyStage.POPPED, PartyStage.OVER),
            told.stages,
        )
        // It ended with its popper gone off.
        assertEquals(listOf(true), told.ends)
    }

    @Test
    fun `it pops once, whatever arrives twice or late`() {
        val told = Told()
        told.course.on(PartyEvent.PICTURES_READ)

        told.course.on(PartyEvent.POP)
        told.course.on(PartyEvent.POP)
        told.course.on(PartyEvent.PICTURES_READ)

        assertEquals(1, told.stages.count { it == PartyStage.POPPED })
        assertEquals(PartyStage.POPPED, told.course.stage)
    }

    @Test
    fun `it cannot pop before he is drawn, or be gone before it popped`() {
        val told = Told()

        told.course.on(PartyEvent.POP)
        told.course.on(PartyEvent.GONE)
        assertEquals(PartyStage.READING, told.course.stage)

        told.course.on(PartyEvent.PICTURES_READ)
        told.course.on(PartyEvent.GONE)
        assertEquals(PartyStage.BEFORE_THE_POP, told.course.stage)
        assertTrue(told.ends.isEmpty())
    }

    @Test
    fun `cut short before the pop, it ends once and says the popper did not go off`() {
        for (before in listOf(emptyList(), listOf(PartyEvent.PICTURES_READ))) {
            val told = Told()
            before.forEach(told.course::on)

            told.course.on(PartyEvent.CUT_SHORT)
            told.course.on(PartyEvent.CUT_SHORT)
            told.course.on(PartyEvent.POP)
            told.course.on(PartyEvent.GONE)

            assertEquals(PartyStage.OVER, told.course.stage)
            assertEquals(listOf(false), told.ends)
            assertEquals(0, told.stages.count { it == PartyStage.POPPED })
        }
    }

    @Test
    fun `cut short after the pop, it ends once and says the popper went off`() {
        val told = Told()
        told.course.on(PartyEvent.PICTURES_READ)
        told.course.on(PartyEvent.POP)

        told.course.on(PartyEvent.CUT_SHORT)
        told.course.on(PartyEvent.GONE)
        told.course.on(PartyEvent.CUT_SHORT)

        assertEquals(listOf(true), told.ends)
    }

    @Test
    fun `nothing follows a party that is over`() {
        val told = Told()
        told.course.on(PartyEvent.PICTURES_READ)
        told.course.on(PartyEvent.POP)
        told.course.on(PartyEvent.GONE)
        val stagesThen = told.stages.toList()

        PartyEvent.entries.forEach(told.course::on)

        assertEquals(stagesThen, told.stages)
        assertEquals(1, told.ends.size)
    }
}
