package com.shawnkowalchuk.milo.core.designsystem.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The film's screen, which Shawn was shown the run on: 1080 pixels wide, 2.625 to the dp. */
private const val FILM_TO_THE_DP = 2.625f
private const val FILM_WIDTH = 1080 / FILM_TO_THE_DP

/**
 * How far from the spot the film drew him in each frame of the run in it shows, from the frame
 * in which he is first seen to the one before he gets to the spot: frames 8 to 23 of the whole,
 * in the film's pixels (`run-slide.json`, "travel", as `render_run_slide.py` wrote it for the
 * film). The app's pictures were drawn again at their own size, and its arithmetic is its own.
 */
private val FILM_TRAVEL =
    floatArrayOf(
        -703.53f, -662.94f, -622.36f, -581.77f, -541.18f, -500.59f, -460.0f, -419.41f,
        -378.83f, -338.24f, -297.65f, -257.06f, -216.47f, -175.88f, -135.29f, -94.71f,
    )

/** Frames of the film: three of the empty page come before the first he is seen in. */
private const val FILM_LEAD = 3
private const val FILM_AT_THE_SPOT = 19
private const val FILM_BRAKE = 76
private const val FILM_HE_IS_GONE = 106
private const val FILM_LAST_SMOKE = 114

private const val FRAME_MS = MascotPicture.FRAME_MS

/**
 * The run's course apart from its drawing: where the spot is, where the mascot is in every
 * frame of his run in, where the two pictures that stand still are put, when each sound is due,
 * when it is over, and which stage follows which.
 */
class MascotRunTest {
    private val widths = (274..1_280).map { it.toFloat() } + FILM_WIDTH

    /** How far he reaches ahead of his axis in the frame of the sheet a pose shows. */
    private fun MascotPose.reach(): Float =
        RunPicture.REACH_AHEAD[sheetFrame!! % RunPicture.RUN_CYCLE]

    // ---- The spot -------------------------------------------------------------------------------

    @Test
    fun `the spot is the middle of the screen on every phone, and on the film's`() {
        for (width in listOf(274f, 320f, 360f, 393f, 411f, FILM_WIDTH, 448f, 610f, 620f)) {
            assertEquals("$width dp wide", width / 2, RunWay(width).spot, 0f)
        }
    }

    @Test
    fun `on a wider screen the spot is as far from the right edge as the slide reaches`() {
        assertEquals(800f - RunPicture.SLIDE_REACH, RunWay(800f).spot, 0.001f)
        assertEquals(1_280f - RunPicture.SLIDE_REACH, RunWay(1_280f).spot, 0.001f)
        // Never left of the middle, and nothing jumps where the one rule takes over from the other.
        for (width in widths) assertTrue(RunWay(width).spot >= width / 2)
        assertEquals(RunWay(620f).spot, RunWay(621f).spot, 1f)
    }

    // ---- The run in -----------------------------------------------------------------------------

    @Test
    fun `he is seen in the first frame of the run in, and a frame sooner he would not be`() {
        for (width in widths) {
            val way = RunWay(width)
            val first = way.coming.poseIn(0)

            assertTrue("$width dp: seen in the first frame", first.axis + first.reach() > 0)
            val sooner = first.copy(sheetFrame = first.sheetFrame!! + RunPicture.RUN_CYCLE - 1)
            val edgeSooner = first.axis - RunPicture.RUN_STEP + sooner.reach()
            assertTrue("$width dp: not seen a frame sooner", edgeSooner <= 0)
        }
    }

    @Test
    fun `every frame of the run in stands one step to the right of the one before`() {
        for (width in widths) {
            val leg = RunWay(width).coming
            for (frame in 1..leg.frames) {
                val stepped = leg.poseIn(frame).axis - leg.poseIn(frame - 1).axis
                assertEquals("$width dp, frame $frame", RunPicture.RUN_STEP, stepped, 0.001f)
            }
        }
    }

    @Test
    fun `the run in shows the sheet's frames in their order and ends on its last`() {
        for (width in widths) {
            val leg = RunWay(width).coming
            for (frame in 1 until leg.frames) {
                val before = leg.poseIn(frame - 1).sheetFrame!!
                val which = "$width dp, frame $frame"
                assertEquals(
                    which,
                    (before + 1) % RunPicture.SHEET_FRAMES,
                    leg.poseIn(frame).sheetFrame,
                )
            }
            // The frame after the sheet's last is the first animated picture's first.
            assertEquals(RunPicture.SHEET_FRAMES - 1, leg.poseIn(leg.frames - 1).sheetFrame)
        }
    }

    @Test
    fun `when the run in is over the sheet is drawn no more, and nothing has jumped`() {
        for (width in widths) {
            val way = RunWay(width)
            val last = way.coming.poseIn(way.coming.frames - 1)
            val arrived = way.coming.poseIn(way.coming.frames)

            assertNull("$width dp", arrived.sheetFrame)
            // The animated picture's first frame shows him this far short of the spot: one step
            // on from the sheet's last frame, as every frame of the run is from the one before.
            assertEquals(way.spot - RunPicture.SHORT_OF_SPOT, arrived.axis, 0.001f)
            assertEquals(RunPicture.RUN_STEP, arrived.axis - last.axis, 0.001f)
            // Asked for a frame past its end, the leg answers with its end.
            assertEquals(arrived, way.coming.poseIn(way.coming.frames + 5))
        }
    }

    @Test
    fun `the run in's clock gives every frame its 42 ms and stops at the end`() {
        val leg = RunWay(FILM_WIDTH).coming

        assertEquals(0, leg.frameAt(0))
        assertEquals(0, leg.frameAt(41))
        assertEquals(1, leg.frameAt(42))
        assertEquals(15, leg.frameAt(16L * FRAME_MS - 1))
        assertEquals(16, leg.frameAt(16L * FRAME_MS))
        assertEquals(16, leg.frameAt(60_000))
    }

    // ---- Against the film -----------------------------------------------------------------------

    @Test
    fun `on the film's screen it is the film, frame for frame`() {
        val way = RunWay(FILM_WIDTH)

        // He is first seen in the film's fourth frame; the app's first frame is that one.
        assertEquals(FILM_AT_THE_SPOT - FILM_LEAD, way.framesIn)
        assertEquals(16, way.framesIn)
        // He brakes 57 frames after he gets to the spot.
        assertEquals(FILM_BRAKE - FILM_AT_THE_SPOT, RunPicture.SPOT_FRAMES)
        // The last smoke is seen 38 frames after the brake: 39 frames of the slide's picture.
        assertEquals(FILM_LAST_SMOKE - FILM_BRAKE + 1, way.slideFrames)
        // 112 frames from the first he is seen in to the last anything is seen in.
        assertEquals((FILM_LAST_SMOKE - FILM_LEAD + 1L) * FRAME_MS, way.millis)
        assertEquals(4_704L, way.millis)
    }

    @Test
    fun `the run in's places are the film's, to a fifth of a dp`() {
        val way = RunWay(FILM_WIDTH)

        assertEquals(FILM_TRAVEL.size, way.framesIn)
        for ((frame, travelled) in FILM_TRAVEL.withIndex()) {
            val inTheFilm = FILM_WIDTH / 2 + travelled / FILM_TO_THE_DP
            assertEquals("frame $frame", inTheFilm, way.coming.poseIn(frame).axis, 0.2f)
        }
        // A step of the run, and how far short of the spot he is when the run in is over.
        assertEquals(324.71f / 8 / FILM_TO_THE_DP, RunPicture.RUN_STEP, 0.02f)
        assertEquals(54.12f / FILM_TO_THE_DP, RunPicture.SHORT_OF_SPOT, 0.02f)
    }

    // ---- How long it is -------------------------------------------------------------------------

    @Test
    fun `the lengths at four widths`() {
        // In, on the spot, sliding out: frames.
        val lengths =
            listOf(274f, 393f, 411f, 610f).associateWith { width ->
                RunWay(width).run { listOf(framesIn, RunPicture.SPOT_FRAMES, slideFrames) }
            }

        assertEquals(listOf(12, 57, 28), lengths[274f])
        // Shawn's phone.
        assertEquals(listOf(15, 57, 39), lengths[393f])
        assertEquals(listOf(16, 57, 39), lengths[411f])
        assertEquals(listOf(22, 57, 45), lengths[610f])
        assertEquals(97L * FRAME_MS, RunWay(274f).millis)
        assertEquals(111L * FRAME_MS, RunWay(393f).millis)
        assertEquals(124L * FRAME_MS, RunWay(610f).millis)
    }

    @Test
    fun `a wider screen has a longer run in, and never a shorter slide`() {
        var before = RunWay(274f)
        for (width in 275..1_280) {
            val way = RunWay(width.toFloat())
            assertTrue("$width dp", way.framesIn >= before.framesIn)
            assertTrue("$width dp", way.slideFrames >= before.slideFrames)
            before = way
        }
        // Past the widest screen the slide reaches across, only the run in grows.
        assertEquals(RunPicture.SLIDE_FRAMES, RunWay(1_280f).slideFrames)
        assertTrue(RunWay(1_280f).framesIn > RunWay(622f).framesIn)
    }

    // ---- The two pictures that stand still ------------------------------------------------------

    @Test
    fun `the animated pictures stand with their spot on the screen's spot`() {
        for (width in widths) {
            val way = RunWay(width)
            assertEquals("$width dp", way.spot, way.playedLeft + RunPicture.SPOT, 0.001f)
        }
        assertEquals(FILM_WIDTH / 2 - 156f, RunWay(FILM_WIDTH).playedLeft, 0.001f)
    }

    @Test
    fun `he leaves at the screen's right edge, never at the picture's`() {
        for (width in widths) {
            val way = RunWay(width)
            assertTrue("$width dp", way.playedLeft + RunPicture.WIDTH >= width)
        }
    }

    @Test
    fun `the smoke that lies furthest to the left is inside the picture`() {
        assertTrue(RunPicture.SLIDE_LEFTMOST.min() >= -RunPicture.SPOT)
        assertEquals(RunPicture.SLIDE_FRAMES, RunPicture.SLIDE_LEFTMOST.size)
    }

    // ---- When it is over ------------------------------------------------------------------------

    @Test
    fun `it is over when the last of him and of his smoke has left the screen`() {
        for (width in widths) {
            val way = RunWay(width)
            val edge = width - way.spot
            val which = "$width dp"

            assertTrue(which, way.slideFrames in 1..RunPicture.SLIDE_FRAMES)
            // Something is on the screen in the last frame that is shown, and nothing after it.
            assertTrue(which, RunPicture.SLIDE_LEFTMOST[way.slideFrames - 1] < edge)
            for (frame in way.slideFrames until RunPicture.SLIDE_FRAMES) {
                assertTrue("$which, frame $frame", RunPicture.SLIDE_LEFTMOST[frame] >= edge)
            }
            assertEquals(which, way.slideFrames.toLong() * FRAME_MS, way.slideMillis)
        }
    }

    @Test
    fun `on the widest screen the slide reaches across, he goes with its last frame`() {
        val last = RunPicture.SLIDE_LEFTMOST.last()

        // His hand is still on such a screen in the last frame, and one more step of the
        // slide's even pace, 6.87 dp, takes it past the edge: the picture ends as he leaves.
        assertTrue(last < RunPicture.SLIDE_REACH)
        assertTrue(RunPicture.SLIDE_REACH - last < 6.87f)
        // The smoke has gone by then: in the frame before, it was the leftmost thing drawn.
        assertTrue(last - RunPicture.SLIDE_LEFTMOST[RunPicture.SLIDE_FRAMES - 2] > 60f)
        assertEquals(RunPicture.SLIDE_FRAMES, RunWay(2 * RunPicture.SLIDE_REACH).slideFrames)
    }

    // ---- The sounds' moments --------------------------------------------------------------------

    @Test
    fun `the whirr is due as he gets to the spot and the squeal 57 frames later, on any screen`() {
        for (width in widths) {
            val way = RunWay(width)
            val which = "$width dp"

            assertEquals(which, way.framesIn.toLong() * FRAME_MS, way.arrivesAtMillis)
            assertEquals(which, way.arrivesAtMillis + 57L * FRAME_MS, way.brakesAtMillis)
            assertEquals(which, way.brakesAtMillis + way.slideMillis, way.millis)
        }
        // On the film's screen: its frames 19 and 76, less the three empty ones before him.
        val film = RunWay(FILM_WIDTH)
        assertEquals((FILM_AT_THE_SPOT - FILM_LEAD).toLong() * FRAME_MS, film.arrivesAtMillis)
        assertEquals((FILM_BRAKE - FILM_LEAD).toLong() * FRAME_MS, film.brakesAtMillis)
    }

    @Test
    fun `the whirr has ended before he brakes, and the squeal when the last of him is gone`() {
        val whirr = soundMillis("report_run_whirr.wav")
        val squeal = soundMillis("report_run_squeal.wav")

        // 1.89 seconds of the 2.39 he is on the spot for.
        assertEquals(1_886, whirr)
        assertTrue(whirr < RunPicture.SPOT_MS)
        // The film's squeal, from the frame of the brake to the frame the last of him has gone.
        assertEquals(1_250, squeal)
        assertTrue(squeal <= (FILM_HE_IS_GONE - FILM_BRAKE) * FRAME_MS)
    }

    /** How long a sound in `res/raw` plays: 16 bits, one channel, 44,100 samples a second. */
    private fun soundMillis(name: String): Int {
        val file = File("src/main/res/raw/$name")
        return ((file.length() - WAV_HEADER) / 2 * 1_000 / 44_100).toInt()
    }

    // ---- The course -----------------------------------------------------------------------------

    private class Told {
        val stages = mutableListOf<RunStage>()
        val done = mutableListOf<Boolean>()
        val course = RunCourse(onStage = { stages += it }, onDone = { done += it })
    }

    @Test
    fun `a run goes through its stages in order, and ends once, whole`() {
        val told = Told()

        for (event in RunEvent.entries.filter { it != RunEvent.CUT_SHORT }) told.course.on(event)

        assertEquals(RunStage.entries.drop(1), told.stages)
        assertEquals(listOf(true), told.done)
        // Whatever arrives after the end changes nothing.
        for (event in RunEvent.entries) told.course.on(event)
        assertEquals(RunStage.entries.drop(1), told.stages)
        assertEquals(listOf(true), told.done)
    }

    @Test
    fun `cut short in any stage, it ends at once and once, and not whole`() {
        val inOrder = RunEvent.entries.filter { it != RunEvent.CUT_SHORT }
        // Before anything is read, while he runs in, on the spot, and in the slide.
        for (reached in 0..3) {
            val told = Told()
            inOrder.take(reached).forEach(told.course::on)

            told.course.on(RunEvent.CUT_SHORT)
            told.course.on(RunEvent.CUT_SHORT)
            inOrder.forEach(told.course::on)

            assertEquals(RunStage.OVER, told.course.stage)
            assertEquals("after $reached steps", listOf(false), told.done)
            assertEquals(reached + 1, told.stages.size)
        }
    }

    @Test
    fun `nothing that arrives late, early or twice starts a sound again`() {
        val told = Told()

        // He cannot brake before he is at the spot, nor arrive before he is drawn.
        told.course.on(RunEvent.BRAKED)
        told.course.on(RunEvent.ARRIVED)
        told.course.on(RunEvent.GONE)
        assertEquals(emptyList<RunStage>(), told.stages)

        told.course.on(RunEvent.PICTURES_READ)
        repeat(3) { told.course.on(RunEvent.ARRIVED) }
        repeat(3) { told.course.on(RunEvent.BRAKED) }
        told.course.on(RunEvent.ARRIVED)

        assertEquals(1, told.stages.count { it == RunStage.ON_THE_SPOT })
        assertEquals(1, told.stages.count { it == RunStage.SLIDING })
        assertEquals(emptyList<Boolean>(), told.done)
    }

    private companion object {
        /** The bytes before the samples in a file `tools/make_run_sounds.py` writes. */
        const val WAV_HEADER = 44
    }
}
