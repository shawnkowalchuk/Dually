package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The moonwalk's two glides across the screen: where the mascot is at each moment of his way in
 * and of his way out, which frame of the glide is drawn there, that the foot on its toe cannot
 * slide, when the middle part begins and when he is gone. And what the two greetings are made
 * of, side by side.
 */
class MascotMoonwalkTest {
    /** A thousandth of a dp: what the arithmetic may be off by, and nothing on a screen. */
    private val exact = 0.001f

    /** Every width from the narrowest window to a tablet on its side, in dp. */
    private val widths = (274..1280).map { it.toFloat() }

    /** The widths the greeting is looked at: narrow, Shawn's phone, the emulator's, wide. */
    private val looked = listOf(274f, 393f, 411f, 610f)

    private val cycle = MoonwalkPicture.GLIDE_FRAMES

    /** How far one frame of the glide carries the mascot. */
    private val perFrame = MoonwalkPicture.STRIDE / cycle

    // ------------------------------------------------------------ the glide in

    @Test
    fun `at the start nothing of the mascot is on the screen, and he is just beyond the edge`() {
        for (width in widths) {
            val glide = MascotGlide(width)
            val leftmost = glide.coming.poseIn(0).axis - MoonwalkPicture.REACH_AHEAD
            assertTrue("$width dp: he begins at $leftmost", leftmost >= width - exact)
            // No time is spent out of sight: one frame on, a part of him is in.
            assertTrue("$width dp: he begins at $leftmost", leftmost - width < perFrame)
        }
    }

    @Test
    fun `the glide in ends on the last frame of a cycle, so the middle part follows it`() {
        for (width in widths) {
            val glide = MascotGlide(width)
            val last = glide.coming.poseIn(glide.framesIn - 1)
            assertEquals("$width dp", cycle - 1, last.sheetFrame)
            // One step short of the middle: the middle part's first frame is the next.
            assertEquals("$width dp", width / 2 + perFrame, last.axis, exact)
        }
    }

    @Test
    fun `it begins partway into a cycle, wherever that leaves him just beyond the edge`() {
        // 44 frames on Shawn's phone: three whole cycles and two frames, so it begins on frame 12.
        val glide = MascotGlide(393f)
        assertEquals(44, glide.framesIn)
        assertEquals(12, glide.coming.poseIn(0).sheetFrame)
        assertEquals(13, glide.coming.poseIn(1).sheetFrame)
        assertEquals(0, glide.coming.poseIn(2).sheetFrame)
    }

    @Test
    fun `when the glide in is over he is in the middle, and the animated picture plays`() {
        for (width in widths) {
            val glide = MascotGlide(width)
            val arrived = glide.coming.poseIn(glide.coming.frames)
            assertEquals(glide.framesIn, glide.coming.frames)
            assertNull("$width dp", arrived.sheetFrame)
            assertEquals(width / 2, arrived.axis, exact)
            assertEquals(width / 2, glide.middle, exact)
            // And stays so, whatever is asked afterwards.
            assertEquals(arrived, glide.coming.poseIn(glide.coming.frames + 50))
        }
    }

    @Test
    fun `gliding in, the foot on its toe does not slide`() {
        for (width in looked) {
            val glide = MascotGlide(width)
            for (frame in 0 until glide.framesIn - 1) {
                val here = glide.coming.poseIn(frame)
                val next = glide.coming.poseIn(frame + 1)
                // Each frame is the next of the cycle, and stands a fourteenth of a stride to
                // the left of the last: as far as the toe has gone forward under him in it.
                assertEquals(
                    "$width dp, frame $frame",
                    (here.sheetFrame!! + 1) % cycle,
                    next.sheetFrame,
                )
                assertEquals("$width dp, frame $frame", perFrame, here.axis - next.axis, exact)
            }
        }
    }

    // ------------------------------------------------------------ the glide out

    @Test
    fun `the glide out begins with the middle part's last frame, which has its time first`() {
        for (width in looked) {
            val glide = MascotGlide(width)
            val held = glide.going.poseIn(0)
            assertNull("$width dp", held.sheetFrame)
            assertEquals(width / 2, held.axis, exact)
            // Then the first frame of a cycle, where he stands.
            val first = glide.going.poseIn(1)
            assertEquals("$width dp", 0, first.sheetFrame)
            assertEquals(width / 2, first.axis, exact)
            assertEquals(glide.framesOut + 1, glide.going.frames)
        }
    }

    @Test
    fun `gliding out, the foot on its toe does not slide`() {
        for (width in looked) {
            val glide = MascotGlide(width)
            for (frame in 1 until glide.going.frames) {
                val here = glide.going.poseIn(frame)
                val next = glide.going.poseIn(frame + 1)
                assertEquals("$width dp, frame $frame", (frame - 1) % cycle, here.sheetFrame)
                assertEquals("$width dp, frame $frame", perFrame, here.axis - next.axis, exact)
            }
        }
    }

    @Test
    fun `he is gone when the glide out is over, and not a frame sooner`() {
        for (width in widths) {
            val glide = MascotGlide(width)
            val lastShown = glide.going.poseIn(glide.going.frames - 1)
            val rightmost = lastShown.axis + MoonwalkPicture.REACH_BEHIND
            assertTrue("$width dp: his last frame reaches to $rightmost", rightmost > 0f)
            val gone = glide.going.poseIn(glide.going.frames)
            assertTrue("$width dp", gone.axis + MoonwalkPicture.REACH_BEHIND <= exact)
            // Whatever is asked afterwards, he does not come back.
            assertEquals(gone, glide.going.poseIn(glide.going.frames + 50))
        }
    }

    // ------------------------------------------------------------ the clock

    @Test
    fun `every frame is due for 42 milliseconds, the first from the start`() {
        val glide = MascotGlide(393f)
        for (leg in listOf(glide.coming, glide.going)) {
            assertEquals(0, leg.frameAt(0))
            assertEquals(0, leg.frameAt(41))
            assertEquals(1, leg.frameAt(42))
            assertEquals(14, leg.frameAt(14 * 42L))
            val over = leg.frames * 42L
            assertEquals(leg.frames - 1, leg.frameAt(over - 1))
            assertEquals(leg.frames, leg.frameAt(over))
            assertEquals(leg.frames, leg.frameAt(over + 5_000))
            // Before the leg it waits on its first frame.
            assertEquals(0, leg.frameAt(-300))
            assertEquals(leg.poseIn(0), leg.poseIn(-3))
        }
    }

    @Test
    fun `a phone that draws slowly leaves frames out, and both glides still end on time`() {
        val glide = MascotGlide(393f)
        for (leg in listOf(glide.coming, glide.going)) {
            val over = leg.frames * 42L
            // The screen is drawn every 130 ms: three frames pass between two drawings.
            val drawn = (0..over + 130 step 130).map(leg::frameAt)
            assertEquals(listOf(0, 3, 6, 9, 12), drawn.take(5))
            assertTrue(drawn.zipWithNext().all { (earlier, later) -> later >= earlier })
            assertEquals(leg.frames, drawn.last())
        }
        // Whatever is left out, a frame's place is its own: he is never in one place with the
        // step of another.
        for (frame in 0 until glide.framesIn step 3) {
            val pose = glide.coming.poseIn(frame)
            assertEquals((frame - glide.framesIn).mod(cycle), pose.sheetFrame)
            assertEquals(glide.middle + (glide.framesIn - frame) * perFrame, pose.axis, exact)
        }
    }

    // ------------------------------------------------------------ how long it takes

    @Test
    fun `on the emulator's phone it is the preview's 176 frames, on Shawn's a little shorter`() {
        // The preview Shawn approved was laid over a phone 411 dp wide: 46 frames in, the 80 of
        // the middle part and 50 out, 7.33 seconds at 24 frames a second.
        val emulator = MascotGlide(411f)
        assertEquals(46, emulator.framesIn)
        assertEquals(50, emulator.framesOut)
        assertEquals(176 * 42L, emulator.millis)
        // Shawn's phone is 393 dp wide.
        val phone = MascotGlide(393f)
        assertEquals(44, phone.framesIn)
        assertEquals(49, phone.framesOut)
        assertEquals(1_848L, phone.millisIn)
        assertEquals(2_058L, phone.millisOut)
        assertEquals(7_266L, phone.millis)
    }

    @Test
    fun `a wider screen takes longer, by the width and nothing else`() {
        assertEquals(6_384L, MascotGlide(274f).millis)
        assertEquals(8_862L, MascotGlide(610f).millis)
        for (width in widths) {
            val glide = MascotGlide(width)
            val way = width + MoonwalkPicture.REACH_AHEAD + MoonwalkPicture.REACH_BEHIND
            // Each glide is made up to a whole frame: at most two frames more than the way.
            val frames = glide.framesIn + glide.framesOut
            assertTrue("$width dp: $frames frames", frames * perFrame >= way)
            assertTrue("$width dp: $frames frames", frames * perFrame < way + 2 * perFrame)
            assertEquals((frames + MoonwalkPicture.MIDDLE_FRAMES) * 42L, glide.millis)
        }
    }

    @Test
    fun `a stride, reaches and a frame's time of one's own are used as given`() {
        // 100 to the middle and 12 of reach ahead, 14 a cycle and so 1 a frame: 112 frames in.
        // 100 from the middle and 30 of reach behind: 130 frames out.
        val glide =
            MascotGlide(
                screenWidth = 200f,
                stride = 14f,
                reachAhead = 12f,
                reachBehind = 30f,
                frameMillis = 50,
            )
        assertEquals(1f, glide.perFrame, exact)
        assertEquals(112, glide.framesIn)
        assertEquals(130, glide.framesOut)
        assertEquals(5_600L, glide.millisIn)
        assertEquals(212f, glide.coming.poseIn(0).axis, exact)
        assertEquals(10, glide.coming.frameAt(500))
        assertEquals(-30f, glide.going.poseIn(glide.going.frames).axis, exact)
    }

    // ------------------------------------------------------------ the two greetings

    @Test
    fun `the wave stands in for a moonwalk that cannot be read, and nothing for the wave`() {
        assertEquals(MascotGreetingKind.WAVE, MascotGreetingKind.MOONWALK.instead)
        assertNull(MascotGreetingKind.WAVE.instead)
    }

    @Test
    fun `each greeting has its own pictures`() {
        assertEquals(MascotPicture.FRAMES, MascotGreetingKind.WAVE.frames)
        assertEquals(MoonwalkPicture.FRAMES, MascotGreetingKind.MOONWALK.frames)

        val wave = MascotPicture.FRAMES
        // The wave's sheet holds two cycles, one for each of its walks.
        assertEquals(24, wave.sheetFrames)
        assertEquals(6, wave.sheetRows)
        assertEquals(3_360L, wave.playedMillis)
        assertEquals(3_360, MascotPicture.MIDDLE_MS)
        assertEquals(118.4f, wave.axis, exact)

        val moonwalk = MoonwalkPicture.FRAMES
        assertEquals(14, moonwalk.sheetFrames)
        assertEquals(2, moonwalk.sheetRows)
        assertEquals(3_360L, moonwalk.playedMillis)
        assertEquals(3_360, MoonwalkPicture.MIDDLE_MS)
        // He is drawn half turned, with room for the kick: not in the middle of his frames.
        assertEquals(89.2f, moonwalk.axis, exact)
    }

    @Test
    fun `the wave crosses from the left to the right, the moonwalk from the right to the left`() {
        for (width in looked) {
            val wave = MascotGreetingKind.WAVE.wayAcross(width)
            val walk = MascotWalk(width)
            assertEquals(walk.framesIn, wave.coming.frames)
            assertEquals(walk.framesOut + 1, wave.going.frames)
            assertTrue(wave.coming.poseIn(0).axis < 0f)
            assertTrue(wave.going.poseIn(wave.going.frames).axis > width)

            val moonwalk = MascotGreetingKind.MOONWALK.wayAcross(width)
            val glide = MascotGlide(width)
            assertEquals(glide.framesIn, moonwalk.coming.frames)
            assertEquals(glide.framesOut + 1, moonwalk.going.frames)
            assertTrue(moonwalk.coming.poseIn(0).axis > width)
            assertTrue(moonwalk.going.poseIn(moonwalk.going.frames).axis < 0f)
            // Both do their middle part in the middle of the screen, and go on from there.
            assertEquals(width / 2, wave.coming.poseIn(wave.coming.frames).axis, exact)
            assertEquals(width / 2, wave.going.poseIn(0).axis, exact)
            assertEquals(width / 2, moonwalk.coming.poseIn(moonwalk.coming.frames).axis, exact)
            assertEquals(width / 2, moonwalk.going.poseIn(0).axis, exact)
        }
    }
}
