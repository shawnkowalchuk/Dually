package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wave's two walks across the screen, and a greeting's course from stage to stage: where
 * the mascot is at each moment of his way in from the left and of his way off at the right,
 * which frame of which walk is drawn there, that a planted foot cannot slide, when the middle
 * part begins, when he is gone, and that a greeting ends once. The moonwalk's glides are in
 * `MascotMoonwalkTest`.
 */
class MascotWalkTest {
    /** A thousandth of a dp: what the arithmetic may be off by, and nothing on a screen. */
    private val exact = 0.001f

    /** Every width from the narrowest window to a tablet on its side, in dp. */
    private val widths = (274..1280).map { it.toFloat() }

    /** The widths the greeting is looked at: narrow, Shawn's phone, the emulator's, wide. */
    private val looked = listOf(274f, 393f, 411f, 610f)

    private val cycle = MascotPicture.WALK_FRAMES

    /** How far one frame of a walk carries the mascot. */
    private val perFrame = MascotPicture.STRIDE / cycle

    // ------------------------------------------------------------ the walk in

    @Test
    fun `at the start nothing of the mascot is on the screen, and he is just beyond the left`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            val rightmost = walk.coming.poseIn(0).axis + MascotPicture.REACH_AHEAD
            assertTrue("$width dp: he begins at $rightmost", rightmost <= exact)
            // No time is spent out of sight: one frame on, a part of him is in.
            assertTrue("$width dp: he begins at $rightmost", rightmost > -perFrame)
        }
    }

    @Test
    fun `the walk in ends on the last frame of its cycle, so the middle part follows it`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            val last = walk.coming.poseIn(walk.framesIn - 1)
            assertEquals("$width dp", cycle - 1, last.sheetFrame)
            // One step short of the middle: the middle part's first frame is the next.
            assertEquals("$width dp", width / 2 - perFrame, last.axis, exact)
        }
    }

    @Test
    fun `it begins partway into a cycle, wherever that leaves him just beyond the edge`() {
        // 47 frames on Shawn's phone: three whole cycles and eleven frames, so it begins on
        // frame 1.
        val phone = MascotWalk(393f)
        assertEquals(47, phone.framesIn)
        assertEquals(1, phone.coming.poseIn(0).sheetFrame)
        assertEquals(2, phone.coming.poseIn(1).sheetFrame)
        assertEquals(11, phone.coming.poseIn(10).sheetFrame)
        assertEquals(0, phone.coming.poseIn(11).sheetFrame)
        // 48 on the emulator's, as in the film Shawn approved: four whole cycles.
        val emulator = MascotWalk(411f)
        assertEquals(48, emulator.framesIn)
        assertEquals(0, emulator.coming.poseIn(0).sheetFrame)
    }

    @Test
    fun `when the walk in is over he is in the middle, and the animated picture plays`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            val arrived = walk.coming.poseIn(walk.coming.frames)
            assertEquals(walk.framesIn, walk.coming.frames)
            assertNull("$width dp", arrived.sheetFrame)
            assertEquals(width / 2, arrived.axis, exact)
            assertEquals(width / 2, walk.middle, exact)
            // And stays so, however long the middle part takes.
            assertEquals(arrived, walk.coming.poseIn(walk.coming.frames + 50))
        }
    }

    @Test
    fun `walking in, a planted foot does not slide`() {
        for (width in looked) {
            val walk = MascotWalk(width)
            for (frame in 0 until walk.framesIn - 1) {
                val here = walk.coming.poseIn(frame)
                val next = walk.coming.poseIn(frame + 1)
                // Each frame is the next of the walk in's cycle, and stands a twelfth of a
                // stride to the right of the last: as far as the planted foot has gone back
                // under him in it.
                assertEquals(
                    "$width dp, frame $frame",
                    (here.sheetFrame!! + 1) % cycle,
                    next.sheetFrame,
                )
                assertEquals("$width dp, frame $frame", perFrame, next.axis - here.axis, exact)
            }
        }
    }

    // ------------------------------------------------------------ the walk off

    @Test
    fun `the walk off begins with the middle part's last frame, which has its time first`() {
        for (width in looked) {
            val walk = MascotWalk(width)
            val held = walk.going.poseIn(0)
            assertNull("$width dp", held.sheetFrame)
            assertEquals(width / 2, held.axis, exact)
            // Then the first frame of the walk off's cycle, where he stands: the sheet's
            // thirteenth frame.
            val first = walk.going.poseIn(1)
            assertEquals("$width dp", cycle, first.sheetFrame)
            assertEquals(width / 2, first.axis, exact)
            assertEquals(walk.framesOut + 1, walk.going.frames)
        }
    }

    @Test
    fun `walking off, a planted foot does not slide`() {
        for (width in looked) {
            val walk = MascotWalk(width)
            for (frame in 1 until walk.going.frames) {
                val here = walk.going.poseIn(frame)
                val next = walk.going.poseIn(frame + 1)
                val inCycle = (frame - 1) % cycle
                assertEquals("$width dp, frame $frame", cycle + inCycle, here.sheetFrame)
                assertEquals("$width dp, frame $frame", perFrame, next.axis - here.axis, exact)
            }
        }
    }

    @Test
    fun `each walk shows its own half of the sheet, the walk in's first and the walk off's last`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            val walkingIn = (0 until walk.framesIn).map { walk.coming.poseIn(it).sheetFrame!! }
            val walkingOff = (1..walk.framesOut).map { walk.going.poseIn(it).sheetFrame!! }
            assertTrue("$width dp", walkingIn.all { it in 0 until cycle })
            assertTrue("$width dp", walkingOff.all { it in cycle until 2 * cycle })
        }
        assertEquals(2 * cycle, MascotPicture.SHEET_FRAMES)
        assertEquals(MascotPicture.SHEET_FRAMES, MascotPicture.FRAMES.sheetFrames)
    }

    @Test
    fun `he is gone when the walk off is over, and not a frame sooner`() {
        for (width in widths) {
            val walk = MascotWalk(width)
            val lastShown = walk.going.poseIn(walk.going.frames - 1)
            val leftmost = lastShown.axis - MascotPicture.REACH_BEHIND
            assertTrue("$width dp: his last frame reaches back to $leftmost", leftmost < width)
            // That is when the greeting is over: nothing fades.
            val gone = walk.going.poseIn(walk.going.frames)
            assertTrue("$width dp", gone.axis - MascotPicture.REACH_BEHIND >= width - exact)
            // Whatever is asked afterwards, he does not come back.
            assertEquals(gone, walk.going.poseIn(walk.going.frames + 50))
        }
    }

    // ------------------------------------------------------------ the feet, as they were drawn

    /**
     * Where `render_wave2.py` found an edge of a shoe on the floor, frame after frame of a
     * cycle, in pixels from the left edge of the frame it drew (2.5 to the dp): written into
     * its `wave2.json` on 2026-10-11. Frame 12 is the next cycle's first. The right heel is
     * the edge that keeps its place worst in either walk; the left toe is on the floor across
     * the cycle's end.
     */
    private val stoodOnTheFloor =
        mapOf(
            "the right heel, walking in" to
                mapOf(3 to 339.2f, 4 to 324.55f, 5 to 309.9f, 6 to 295.25f, 7 to 280.6f),
            "the left toe, walking in" to
                mapOf(11 to 449.35f, 12 to 434.4f, 13 to 419.46f, 14 to 404.51f, 15 to 389.57f),
            "the right heel, walking off" to
                mapOf(3 to 376.22f, 4 to 361.27f, 5 to 346.33f, 6 to 331.39f, 7 to 316.45f),
            "the left toe, walking off" to
                mapOf(11 to 410.05f, 12 to 395.39f, 13 to 380.72f, 14 to 366.06f, 15 to 351.4f),
        )

    @Test
    fun `the stride is the pictures' own, a shoe's edge on the floor stays within half a dp`() {
        // If the stride here were not the one the frames were drawn with, the feet would slide:
        // a tenth of a dp a frame for every 1.2 dp the stride is out by.
        for ((edge, stood) in stoodOnTheFloor) {
            // Frame k of a cycle stands k frames' way on from frame 0, in either walk.
            val onScreen =
                stood.map { (frame, pixels) ->
                    frame * perFrame + pixels / MascotPicture.PIXELS_PER_DP
                }
            val strays = onScreen.max() - onScreen.min()
            assertTrue("$edge strays $strays dp while it stands", strays < 0.5f)
        }
    }

    // ------------------------------------------------------------ the clock

    @Test
    fun `every frame is due for 42 milliseconds, the first from the start`() {
        val walk = MascotWalk(393f)
        for (leg in listOf(walk.coming, walk.going)) {
            assertEquals(0, leg.frameAt(0))
            assertEquals(0, leg.frameAt(41))
            assertEquals(1, leg.frameAt(42))
            assertEquals(12, leg.frameAt(12 * 42L))
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
    fun `a phone that draws slowly leaves frames out, and both walks still end on time`() {
        val walk = MascotWalk(393f)
        for (leg in listOf(walk.coming, walk.going)) {
            val over = leg.frames * 42L
            // The screen is drawn every 130 ms: three frames pass between two drawings.
            val drawn = (0..over + 130 step 130).map(leg::frameAt)
            assertEquals(listOf(0, 3, 6, 9, 12), drawn.take(5))
            assertTrue(drawn.zipWithNext().all { (earlier, later) -> later >= earlier })
            assertEquals(leg.frames, drawn.last())
        }
        // Whatever is left out, a frame's place is its own: he is never in one place with the
        // step of another.
        for (frame in 0 until walk.framesIn step 3) {
            val pose = walk.coming.poseIn(frame)
            assertEquals((frame - walk.framesIn).mod(cycle), pose.sheetFrame)
            assertEquals(walk.middle - (walk.framesIn - frame) * perFrame, pose.axis, exact)
        }
        for (walked in 0 until walk.framesOut step 3) {
            val pose = walk.going.poseIn(walked + 1)
            assertEquals(cycle + walked % cycle, pose.sheetFrame)
            assertEquals(walk.middle + walked * perFrame, pose.axis, exact)
        }
    }

    // ------------------------------------------------------------ how long it takes

    @Test
    fun `on the emulator's phone it is the film's 173 frames, on Shawn's two fewer`() {
        // The film Shawn approved was laid over a phone 411 dp wide: 48 frames in, the 80 of
        // the middle part and 45 off, 7.21 seconds at 24 frames a second.
        val emulator = MascotWalk(1080 / 2.625f)
        assertEquals(48, emulator.framesIn)
        assertEquals(45, emulator.framesOut)
        assertEquals(173 * 42L, emulator.millis)
        // Shawn's phone is 393 dp wide.
        val phone = MascotWalk(393f)
        assertEquals(47, phone.framesIn)
        assertEquals(44, phone.framesOut)
        assertEquals(1_974L, phone.millisIn)
        assertEquals(1_848L, phone.millisOut)
        assertEquals(171 * 42L, phone.millis)
        assertEquals(7_182L, phone.millis)
    }

    @Test
    fun `a wider screen takes longer, by the width and nothing else`() {
        assertEquals(6_342L, MascotWalk(274f).millis)
        assertEquals(8_694L, MascotWalk(610f).millis)
        for (width in widths) {
            val walk = MascotWalk(width)
            val way = width + MascotPicture.REACH_AHEAD + MascotPicture.REACH_BEHIND
            // Each walk is made up to a whole frame: at most two frames more than the way.
            val frames = walk.framesIn + walk.framesOut
            assertTrue("$width dp: $frames frames", frames * perFrame >= way)
            assertTrue("$width dp: $frames frames", frames * perFrame < way + 2 * perFrame)
            assertEquals((frames + MascotPicture.MIDDLE_FRAMES) * 42L, walk.millis)
        }
    }

    @Test
    fun `a stride, reaches and a frame's time of one's own are used as given`() {
        // 100 to the middle and 20 of reach ahead, 12 a cycle and so 1 a frame: 120 frames in.
        // 100 from the middle and 30 of reach behind: 130 frames off.
        val walk =
            MascotWalk(
                screenWidth = 200f,
                stride = 12f,
                reachAhead = 20f,
                reachBehind = 30f,
                frameMillis = 50,
            )
        assertEquals(1f, walk.perFrame, exact)
        assertEquals(120, walk.framesIn)
        assertEquals(130, walk.framesOut)
        assertEquals(6_000L, walk.millisIn)
        assertEquals(-20f, walk.coming.poseIn(0).axis, exact)
        assertEquals(10, walk.coming.frameAt(500))
        assertEquals(230f, walk.going.poseIn(walk.going.frames).axis, exact)
    }

    // ------------------------------------------------------------ a greeting's course

    private class Told {
        val stages = mutableListOf<GreetingStage>()
        var done = 0
        val course = GreetingCourse(onStage = { stages += it }, onDone = { done++ })
    }

    private val inOrder =
        listOf(
            GreetingEvent.PICTURES_READ,
            GreetingEvent.ARRIVED,
            GreetingEvent.MIDDLE_OVER,
            GreetingEvent.GONE,
        )

    @Test
    fun `a greeting goes from reading to coming, the middle and leaving, and is over once`() {
        val told = Told()
        assertEquals(GreetingStage.READING, told.course.stage)

        inOrder.forEach(told.course::on)

        assertEquals(
            listOf(
                GreetingStage.COMING,
                GreetingStage.IN_THE_MIDDLE,
                GreetingStage.LEAVING,
                GreetingStage.OVER,
            ),
            told.stages,
        )
        assertEquals(1, told.done)
    }

    @Test
    fun `the mascot is first drawn once, when the pictures are read, and never if they are not`() {
        // That stage is what "shown" means: the greeting's turn is taken from it.
        val shown = Told()
        inOrder.forEach(shown.course::on)
        shown.course.on(GreetingEvent.PICTURES_READ)
        assertEquals(1, shown.stages.count { it == GreetingStage.COMING })

        // Animations off, or no picture that can be read: over with nothing drawn.
        val never = Told()
        never.course.on(GreetingEvent.CUT_SHORT)
        never.course.on(GreetingEvent.PICTURES_READ)
        assertEquals(0, never.stages.count { it == GreetingStage.COMING })
        assertEquals(1, never.done)
    }

    @Test
    fun `cut short at any stage, it is over at once and once`() {
        for (before in 0..inOrder.size) {
            val told = Told()
            inOrder.take(before).forEach(told.course::on)

            told.course.on(GreetingEvent.CUT_SHORT)

            assertEquals(GreetingStage.OVER, told.course.stage)
            assertEquals("after $before steps", 1, told.done)
            assertEquals("after $before steps", 1, told.stages.count { it == GreetingStage.OVER })
        }
    }

    @Test
    fun `an event that does not belong to the stage changes nothing`() {
        val told = Told()
        told.course.on(GreetingEvent.MIDDLE_OVER)
        told.course.on(GreetingEvent.GONE)
        told.course.on(GreetingEvent.ARRIVED)
        assertEquals(GreetingStage.READING, told.course.stage)
        assertEquals(emptyList<GreetingStage>(), told.stages)
        assertEquals(0, told.done)
    }

    @Test
    fun `whatever happens, in whatever order and however often, a greeting is done at most once`() {
        val events = GreetingEvent.entries
        // Every run of six events, of five kinds: 15,625 of them.
        val runs = generateSequence(listOf(emptyList<GreetingEvent>())) { shorter ->
            shorter.flatMap { run -> events.map { run + it } }
        }.elementAt(6)
        assertEquals(15_625, runs.size)
        for (run in runs) {
            val told = Told()
            run.forEach(told.course::on)
            val over = told.course.stage == GreetingStage.OVER
            assertEquals("$run", if (over) 1 else 0, told.done)
            // Once it is over, nothing is told again and no stage follows.
            assertEquals("$run", if (over) 1 else 0, told.stages.count { it == GreetingStage.OVER })
            if (over) assertEquals("$run", GreetingStage.OVER, told.stages.last())
            // A run that holds the cut, or the four steps in their order, is over.
            if (GreetingEvent.CUT_SHORT in run) assertTrue("$run", over)
        }
    }
}
