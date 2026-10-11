package com.shawnkowalchuk.milo.core.designsystem.component

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's six pictures, two for each greeting and two for the one-year party, are made by
 * hand, by scripts outside the build (`design/mascot/scripts/`: `render_greeting.py` and
 * `pack_greeting.py` for the wave, `render_moonwalk.py` and `pack_moonwalk.py` for the
 * moonwalk, `render_party.py` and `pack_party.py` for the party), and committed. This holds the
 * files to what the code expects ([MascotPicture], [MoonwalkPicture], [MascotPartyPicture]): a
 * picture that is made again with another size, a frame more or another way of repeating fails
 * here and not on the phone, where the mascot would be drawn in pieces or turn in the middle of
 * a step.
 *
 * A WebP file is a list of named chunks. "VP8X" says what the file holds and how large its
 * picture is. In an animated one "ANIM" says how often it plays, and each "ANMF" is one frame:
 * where it lies in the picture, how large it is, the time it is shown for, and how it is laid
 * over the frame before. Only those are read: no test off the phone can draw a frame.
 */
class MascotPicturesTest {
    /**
     * @property left and [top] where the frame lies in the picture.
     * @property replaces true if the frame takes the place of what lies under it, see-through
     * parts and all, and is not mixed with it.
     * @property clearedAfter true if its part of the picture is emptied before the next frame.
     */
    private class Frame(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val millis: Int,
        val replaces: Boolean,
        val clearedAfter: Boolean,
        val seeThrough: Boolean,
    )

    private class Picture(file: File) {
        val bytes: Long = file.length()
        var animated = false
        var seeThrough = false
        var width = 0
        var height = 0

        /** How often it plays, as the file says it: 0 is for ever. */
        var plays = -1
        val frames = mutableListOf<Frame>()

        init {
            val all = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("${file.name} starts with 'RIFF'", RIFF, all.getInt(0))
            assertEquals("${file.name} is a WebP file", WEBP, all.getInt(8))
            var at = FIRST_CHUNK
            while (at + CHUNK_HEADER <= all.limit()) {
                val size = all.getInt(at + 4)
                val body = at + CHUNK_HEADER
                when (all.getInt(at)) {
                    VP8X -> {
                        animated = all.get(body).toInt() and ANIMATION_FLAG != 0
                        seeThrough = all.get(body).toInt() and ALPHA_FLAG != 0
                        width = all.threeBytes(body + 4) + 1
                        height = all.threeBytes(body + 7) + 1
                    }

                    ANIM -> plays = all.getShort(body + 4).toInt() and 0xFFFF

                    ANMF ->
                        frames +=
                            Frame(
                                // Stored halved: a frame lies on even pixels.
                                left = all.threeBytes(body) * 2,
                                top = all.threeBytes(body + 3) * 2,
                                width = all.threeBytes(body + 6) + 1,
                                height = all.threeBytes(body + 9) + 1,
                                millis = all.threeBytes(body + 12),
                                replaces = all.get(body + 15).toInt() and NO_BLEND_FLAG != 0,
                                clearedAfter = all.get(body + 15).toInt() and DISPOSE_FLAG != 0,
                                // The frame's own picture follows its 16 bytes: "ALPH" first,
                                // if it has a see-through channel of its own.
                                seeThrough = all.getInt(body + 16) == ALPH,
                            )
                }
                // A chunk is padded to an even length.
                at = body + size + (size and 1)
            }
        }

        private fun ByteBuffer.threeBytes(at: Int): Int = (get(at).toInt() and 0xFF) or
            ((get(at + 1).toInt() and 0xFF) shl 8) or
            ((get(at + 2).toInt() and 0xFF) shl 16)
    }

    private val walk = Picture(File("$PICTURES/mascot_walk.webp"))
    private val turnAndWave = Picture(File("$PICTURES/mascot_turn_and_wave.webp"))
    private val glide = Picture(File("$PICTURES/mascot_moonwalk.webp"))
    private val middle = Picture(File("$PICTURES/mascot_stop_and_spin.webp"))
    private val popper = Picture(File("$PICTURES/mascot_party_popper.webp"))
    private val pop = Picture(File("$PICTURES/mascot_party_pop.webp"))

    /** One frame of the wave, as the code draws it: 600 by 420 pixels. */
    private val frameWidth = (MascotPicture.WIDTH * MascotPicture.PIXELS_PER_DP).roundToInt()
    private val frameHeight = (MascotPicture.HEIGHT * MascotPicture.PIXELS_PER_DP).roundToInt()

    /** One frame of the moonwalk: 480 by 480 pixels. */
    private val moonWidth = (MoonwalkPicture.WIDTH * MascotPicture.PIXELS_PER_DP).roundToInt()
    private val moonHeight = (MoonwalkPicture.HEIGHT * MascotPicture.PIXELS_PER_DP).roundToInt()

    @Test
    fun `a frame is 600 by 420 pixels`() {
        assertEquals(600, frameWidth)
        assertEquals(420, frameHeight)
    }

    @Test
    fun `the walk is a still, see-through sheet of twelve frames, four across and three down`() {
        // The app steps through the walk by its own clock; an animated picture would play by
        // Android's, and the mascot's place could not follow its step.
        assertFalse("the walk is not animated", walk.animated)
        assertTrue("the walk is see-through", walk.seeThrough)
        assertEquals(12, MascotPicture.WALK_FRAMES)
        assertEquals(4, MascotPicture.WALK_COLUMNS)
        assertEquals(3, MascotPicture.WALK_ROWS)
        assertEquals(MascotPicture.WALK_COLUMNS * frameWidth, walk.width)
        assertEquals(MascotPicture.WALK_ROWS * frameHeight, walk.height)
    }

    @Test
    fun `the turn and wave is an animated, see-through picture the size of one frame`() {
        assertTrue("the turn and wave is animated", turnAndWave.animated)
        assertTrue("the turn and wave is see-through", turnAndWave.seeThrough)
        assertTrue("every frame is see-through", turnAndWave.frames.all { it.seeThrough })
        // So it lies exactly over the walk's last frame.
        assertEquals(frameWidth, turnAndWave.width)
        assertEquals(frameHeight, turnAndWave.height)
        // Every frame is the whole picture: none is a patch laid over the one before.
        assertTrue(turnAndWave.frames.all { it.width == frameWidth && it.height == frameHeight })
    }

    @Test
    fun `the turn and wave is sixty frames of 42 milliseconds, and plays once`() {
        assertEquals(MascotPicture.TURN_AND_WAVE_FRAMES, turnAndWave.frames.size)
        assertEquals(60, turnAndWave.frames.size)
        assertEquals(1, turnAndWave.plays)
        // The walk's frames are shown for as long, each: the two are one film.
        assertEquals(42, MascotPicture.FRAME_MS)
        assertTrue(turnAndWave.frames.all { it.millis == MascotPicture.FRAME_MS })
        assertEquals(2_520, MascotPicture.TURN_AND_WAVE_MS)
    }

    @Test
    fun `the wave's two stay under a megabyte together`() {
        assertTrue("the walk: ${walk.bytes} bytes", walk.bytes < MOST_WALK_BYTES)
        assertTrue(
            "the turn and wave: ${turnAndWave.bytes} bytes",
            turnAndWave.bytes < MOST_WAVE_BYTES,
        )
        assertTrue(MOST_WALK_BYTES + MOST_WAVE_BYTES <= 1_000_000L)
    }

    // ------------------------------------------------------------ the moonwalk's two

    @Test
    fun `a frame of the moonwalk is 480 by 480 pixels`() {
        assertEquals(480, moonWidth)
        assertEquals(480, moonHeight)
    }

    @Test
    fun `the glide is a still, see-through sheet of fourteen frames, seven across and two down`() {
        assertFalse("the glide is not animated", glide.animated)
        assertTrue("the glide is see-through", glide.seeThrough)
        assertEquals(14, MoonwalkPicture.GLIDE_FRAMES)
        assertEquals(7, MoonwalkPicture.GLIDE_COLUMNS)
        assertEquals(2, MoonwalkPicture.FRAMES.sheetRows)
        assertEquals(MoonwalkPicture.GLIDE_COLUMNS * moonWidth, glide.width)
        assertEquals(MoonwalkPicture.FRAMES.sheetRows * moonHeight, glide.height)
        // No phone's graphics chip refuses a picture of up to 4,096 pixels a side.
        assertTrue("the sheet is ${glide.width} pixels wide", glide.width <= 4_096)
    }

    @Test
    fun `the middle part is an animated, see-through picture the size of one frame`() {
        assertTrue("the middle part is animated", middle.animated)
        assertTrue("the middle part is see-through", middle.seeThrough)
        assertTrue("every frame is see-through", middle.frames.all { it.seeThrough })
        // So it lies exactly over the glide's last frame, and the glide's first over it.
        assertEquals(moonWidth, middle.width)
        assertEquals(moonHeight, middle.height)
    }

    @Test
    fun `the middle part plays once and takes eighty frames' time`() {
        assertEquals(1, middle.plays)
        assertEquals(80, MoonwalkPicture.MIDDLE_FRAMES)
        // Fewer frames are stored than are played: one that is the frame before is not stored,
        // and the frame before is shown for two frames' time, or three.
        assertTrue("${middle.frames.size} frames stored", middle.frames.size <= 80)
        assertTrue(middle.frames.all { it.millis > 0 && it.millis % MascotPicture.FRAME_MS == 0 })
        assertEquals(MoonwalkPicture.MIDDLE_MS, middle.frames.sumOf { it.millis })
        assertEquals(3_360, MoonwalkPicture.MIDDLE_MS)
        // The frames the glide meets are whole frames' time, no longer: the first, which
        // follows the glide's last, and the last, which the glide's first follows.
        assertEquals(MascotPicture.FRAME_MS, middle.frames.first().millis)
        assertEquals(MascotPicture.FRAME_MS, middle.frames.last().millis)
    }

    @Test
    fun `a frame of the middle part is the whole picture, or a patch laid on the one before`() {
        // The first is the whole picture: there is nothing before it to lie on.
        val first = middle.frames.first()
        assertEquals(
            listOf(0, 0, moonWidth, moonHeight),
            first.run {
                listOf(left, top, width, height)
            },
        )
        for ((number, frame) in middle.frames.withIndex()) {
            // A patch lies inside the picture.
            assertTrue("frame $number", frame.left + frame.width <= moonWidth)
            assertTrue("frame $number", frame.top + frame.height <= moonHeight)
            // It takes the place of what is under it and leaves the rest as it was: mixed in,
            // a see-through part of it would let the frame before show through, and cleared
            // afterwards, the mascot would be gone but for the next patch.
            assertTrue("frame $number replaces what is under it", frame.replaces)
            assertFalse("frame $number is left for the next", frame.clearedAfter)
        }
        // Most frames are whole: he moves all over in nearly every one.
        val whole = middle.frames.count { it.width == moonWidth && it.height == moonHeight }
        assertTrue("$whole whole frames of ${middle.frames.size}", whole >= 60)
    }

    @Test
    fun `the moonwalk's two stay under a megabyte together, and all four under two`() {
        assertTrue("the glide: ${glide.bytes} bytes", glide.bytes < MOST_GLIDE_BYTES)
        assertTrue("the middle part: ${middle.bytes} bytes", middle.bytes < MOST_MIDDLE_BYTES)
        assertTrue(MOST_GLIDE_BYTES + MOST_MIDDLE_BYTES <= 1_000_000L)
        val all = walk.bytes + turnAndWave.bytes + glide.bytes + middle.bytes
        assertTrue("all four: $all bytes", all < MOST_BYTES)
    }

    // ------------------------------------------------------------ the party's two

    @Test
    fun `the party's two are animated, see-through pictures of one size`() {
        for ((name, picture) in listOf("before the pop" to popper, "from the pop" to pop)) {
            assertTrue("$name is animated", picture.animated)
            assertTrue("$name is see-through", picture.seeThrough)
            assertTrue("$name: every frame is see-through", picture.frames.all { it.seeThrough })
            // So the second lies exactly over the first, and the mascot does not move.
            assertEquals(name, MascotPartyPicture.PIXELS_WIDE, picture.width)
            assertEquals(name, MascotPartyPicture.PIXELS_HIGH, picture.height)
        }
        assertEquals(675, MascotPartyPicture.PIXELS_WIDE)
        assertEquals(800, MascotPartyPicture.PIXELS_HIGH)
    }

    @Test
    fun `a frame of the party is shown in the shape it was drawn in, he as large as in the film`() {
        // The lengths the code draws by are the pixels, at one number of pixels to the dp: the
        // film's 2.625, times the five eighths its frames were made smaller by.
        val toTheDp = MascotPartyPicture.PIXELS_PER_DP
        assertEquals(2.625f * 5 / 8, toTheDp, 0f)
        assertEquals(MascotPartyPicture.PIXELS_WIDE / toTheDp, MascotPartyPicture.WIDTH, 0.01f)
        assertEquals(MascotPartyPicture.PIXELS_HIGH / toTheDp, MascotPartyPicture.HEIGHT, 0.01f)
        assertEquals(411.43f, MascotPartyPicture.WIDTH, 0.01f)
        assertEquals(487.62f, MascotPartyPicture.HEIGHT, 0.01f)
        // He is as many pixels high as the greetings' mascot: 150 dp at 2.5 pixels to the dp.
        val pixelsHigh = MascotPicture.TALL * MascotPicture.PIXELS_PER_DP
        assertEquals(375f, pixelsHigh, 0f)
        assertEquals(pixelsHigh / toTheDp, MascotPartyPicture.TALL, 0.01f)
        // The film's screen was 1080 pixels wide at 2.625 to the dp, and he 600 pixels high.
        assertEquals(1080 / 2.625f, MascotPartyPicture.WIDTH, 0.01f)
        assertEquals(600 / 2.625f, MascotPartyPicture.TALL, 0.01f)
        // He stands in the middle of the frame's width.
        assertEquals(MascotPartyPicture.WIDTH / 2, MascotPartyPicture.AXIS, 0.01f)
    }

    @Test
    fun `before the pop it plays once for 61 frames' time, after the app held its first frame`() {
        assertEquals(1, popper.plays)
        assertEquals(61, MascotPartyPicture.POPPER_FRAMES)
        assertEquals(2_562, MascotPartyPicture.POPPER_MS)
        assertTrue(popper.frames.all { it.millis > 0 && it.millis % MascotPicture.FRAME_MS == 0 })
        assertEquals(MascotPartyPicture.POPPER_MS, popper.frames.sumOf { it.millis })
        // Its first frame is one frame's time, no longer. He stands still in it for twelve
        // frames more, and those are the app's to show: Android does not keep a picture's
        // first frame on the screen for a longer time the file gives it.
        assertEquals(MascotPicture.FRAME_MS, popper.frames.first().millis)
        assertEquals(12, MascotPartyPicture.STILL_FRAMES)
        assertEquals(504, MascotPartyPicture.STILL_MS)
        assertEquals(13 * MascotPicture.FRAME_MS, MascotPartyPicture.FIRST_FRAME_MS)
        // He is faded in while he stands still, and no longer.
        assertEquals(MascotPartyPicture.STILL_MS, MascotPartyPicture.FADE_MS)
        // Its last frame is one frame's time too: the pop follows it.
        assertEquals(MascotPicture.FRAME_MS, popper.frames.last().millis)
        // The popper goes off 73 frames after he is first drawn, as in the film.
        assertEquals(73 * MascotPicture.FRAME_MS, MascotPartyPicture.POP_AT_MS)
    }

    @Test
    fun `from the pop it plays once for 57 frames' time, the pop first`() {
        assertEquals(1, pop.plays)
        assertEquals(57, MascotPartyPicture.POP_FRAMES)
        assertEquals(2_394, MascotPartyPicture.POP_MS)
        assertTrue(pop.frames.all { it.millis > 0 && it.millis % MascotPicture.FRAME_MS == 0 })
        assertEquals(MascotPartyPicture.POP_MS, pop.frames.sumOf { it.millis })
        // The frame of the pop is one frame's time: the sound is laid on it.
        assertEquals(MascotPicture.FRAME_MS, pop.frames.first().millis)
        // The still frames and the two pictures together are the clip without its last frame,
        // in which nothing of him is left to see: 131 frames in the film, 130 here.
        val frames = MascotPartyPicture.run { STILL_FRAMES + POPPER_FRAMES + POP_FRAMES }
        assertEquals(130, frames)
        // He is faded out over the second picture's last frames, and it is long enough for that.
        assertTrue(MascotPartyPicture.POP_MS > 2 * MascotPartyPicture.FADE_MS)
    }

    @Test
    fun `a frame of the party is the whole picture, or a patch laid on the one before`() {
        val whole =
            listOf(0, 0, MascotPartyPicture.PIXELS_WIDE, MascotPartyPicture.PIXELS_HIGH)
        for ((name, picture) in listOf("before the pop" to popper, "from the pop" to pop)) {
            // The first is the whole picture: there is nothing before it to lie on.
            val first = picture.frames.first()
            assertEquals(name, whole, first.run { listOf(left, top, width, height) })
            for ((number, frame) in picture.frames.withIndex()) {
                val which = "$name, frame $number"
                assertTrue(which, frame.left + frame.width <= MascotPartyPicture.PIXELS_WIDE)
                assertTrue(which, frame.top + frame.height <= MascotPartyPicture.PIXELS_HIGH)
                // As in the moonwalk's middle part: it replaces what is under it, and stays.
                assertTrue("$which replaces what is under it", frame.replaces)
                assertFalse("$which is left for the next", frame.clearedAfter)
            }
        }
    }

    @Test
    fun `the party's two stay under two megabytes and four tenths together`() {
        assertTrue("before the pop: ${popper.bytes} bytes", popper.bytes < MOST_POPPER_BYTES)
        assertTrue("from the pop: ${pop.bytes} bytes", pop.bytes < MOST_POP_BYTES)
        assertTrue(MOST_POPPER_BYTES + MOST_POP_BYTES <= MOST_PARTY_BYTES)
        // The greetings' limits above are as they were: the party is counted apart from them.
        assertEquals(2_400_000L, MOST_PARTY_BYTES)
    }

    private companion object {
        const val PICTURES = "src/main/res/drawable-nodpi"

        /** The chunks' names, each as the four bytes of a little-endian number. */
        const val RIFF = 0x46464952
        const val WEBP = 0x50424557
        const val VP8X = 0x58385056
        const val ANIM = 0x4D494E41
        const val ANMF = 0x464D4E41
        const val ALPH = 0x48504C41

        /** "RIFF", the file's length and "WEBP" come first; a chunk is a name and a length. */
        const val FIRST_CHUNK = 12
        const val CHUNK_HEADER = 8

        const val ANIMATION_FLAG = 0x02
        const val ALPHA_FLAG = 0x10

        /** In a frame's own flags: laid on without mixing, and cleared away afterwards. */
        const val NO_BLEND_FLAG = 0x02
        const val DISPOSE_FLAG = 0x01

        /** Measured on 2026-10-09: 98 KB and 782 KB. */
        const val MOST_WALK_BYTES = 150_000L
        const val MOST_WAVE_BYTES = 850_000L

        /** Measured on 2026-10-10: 130 KB and 768 KB. */
        const val MOST_GLIDE_BYTES = 160_000L
        const val MOST_MIDDLE_BYTES = 840_000L

        /**
         * The mascot's pictures together. It was one megabyte while there was one greeting.
         * **A second greeting is a second megabyte, and that is why this limit is two:** the
         * moonwalk is 94 more frames, and a frame of the mascot at this size is about 10 KB
         * however it is packed (FINDINGS_LOG, 2026-10-10: what was tried). Each greeting is
         * still held under one megabyte, so a third would have to ask for a third.
         */
        const val MOST_BYTES = 2_000_000L

        /** Measured on 2026-10-10: 885 KB and 1,426 KB. */
        const val MOST_POPPER_BYTES = 920_000L
        const val MOST_POP_BYTES = 1_480_000L

        /**
         * The party's two pictures together, counted apart from the greetings' four.
         * **The party was to add no more than two and a half megabytes to MilO, sound and code
         * and all, and this is the pictures' share of that.** It is more than a greeting's
         * megabyte because it is more picture: 130 frames where a greeting has about 80, a
         * frame two and a half times as large (the film's whole screen width, for the confetti
         * to fly in), and 57 of them full of paper, which packs worse than he does. Packed as
         * the greetings' pictures are it weighed three megabytes, and at the film's own pixels
         * over four (FINDINGS_LOG, 2026-10-10: what was tried, and what was given up for it).
         */
        const val MOST_PARTY_BYTES = 2_400_000L
    }
}
