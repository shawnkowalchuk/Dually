package com.shawnkowalchuk.milo.core.designsystem.component

import kotlin.math.max

// The run's course, apart from its drawing (ADR-007, the addendum of 2026-10-11 for the run):
// what its three pictures hold, where the mascot is at each moment of his run in, where the
// two pictures that stand still are put, how long each part lasts on a screen, and which stage
// of a run follows which. Plain Kotlin with unit tests; `MascotRun.kt` draws what this works out.

/**
 * What the run's three pictures hold, as `design/mascot/scripts/pack_run_slide.py` printed it
 * when it packed them. Lengths are in dp; the pictures have the greetings' 2.5 pixels to the
 * dp, and the mascot is the greetings' 150 dp high. `MascotPicturesTest` holds the three files
 * to the sizes, the frames and the times.
 *
 * **A sheet for the run in, which the app moves; two animated pictures for the rest, which
 * stand still.** The film Shawn approved moved every frame by how far he had gone. The app can
 * do that only with frames it steps through itself, the run in's. From the frame he gets to
 * the spot on, Android plays the frames, and does not say which one it shows: so those were
 * laid, when they were packed, where the film laid them, on a picture as wide as he goes. He
 * moves in that picture, his smoke stays where it was made, and nothing has to follow anything.
 *
 * **The second animated picture begins with the frame the brake lights come on in,** and the
 * app starts it: that is how the app knows the moment the squeal belongs to, as the party's
 * second picture begins with the pop.
 *
 * TODO(debt): the halo round his lit brake lights is stored in the pictures for a dark ground,
 *  as the greetings' is ([MascotPicture]): red that covers a part of what lies behind. The
 *  lights are lit from the brake until he is gone, and on the Report screen what lies behind
 *  him there is often the lime button "Email the report": over it the halo is an orange mist
 *  that darkens the lime. See docs/FINDINGS_LOG.md, 2026-10-11.
 */
internal object RunPicture {
    /** A frame of the sheet: he, on the spot, with the dust his feet have kicked up behind him. */
    const val SHEET_WIDTH = 260f
    const val SHEET_HEIGHT = 172f

    /** In a frame of the sheet he stands this far from its left edge. */
    const val SHEET_AXIS = 167.6f

    /** And his soles are this far above its lower edge. */
    const val SHEET_SOLES = 4.8f

    /** One cycle of the run, two steps, is this many frames: a third of a second. */
    const val RUN_CYCLE = 8

    /**
     * The sheet holds the last two cycles of the run in, [SHEET_COLUMNS] across, row after
     * row. One would do for him: the dust behind him is not the same in both. In the frame
     * after the sheet's last he gets to the spot, and the first animated picture begins.
     */
    const val SHEET_FRAMES = 2 * RUN_CYCLE
    const val SHEET_COLUMNS = 4

    /** A frame of the run carries him this far. */
    const val RUN_STEP = 15.4525f

    /**
     * In the frame he gets to the spot, the first of the animated picture, he is still this
     * far short of it: he runs that out over the next four frames, in the picture.
     */
    const val SHORT_OF_SPOT = 20.604f

    /**
     * How far he reaches ahead of his axis, to its right, in each frame of a cycle of the run,
     * from the cycle's first: a fist, or a shoe. It says in which frame he is first seen.
     */
    val REACH_AHEAD = floatArrayOf(66.4f, 68.0f, 74.4f, 83.6f, 88.0f, 86.4f, 79.6f, 71.2f)

    /** Both animated pictures are this wide and this high, and stand in the same place. */
    const val WIDTH = 468f
    const val HEIGHT = 180f

    /** The spot he runs on is this far from their left edge. */
    const val SPOT = 156f

    /** While he stands on it, his soles are this far above their lower edge. */
    const val SOLES = 8.4f

    /**
     * The first animated picture: from the frame he gets to the spot to the last one before
     * the brake lights come on. He spins his wheels up, runs on the spot for two seconds with
     * his shoes smoking, crouches and bolts.
     */
    const val SPOT_FRAMES = 57
    const val SPOT_MS = SPOT_FRAMES * MascotPicture.FRAME_MS

    /**
     * The second: from the frame the brake lights come on in. He brakes, swings round and
     * slides out to the right, his smoke left lying behind him.
     */
    const val SLIDE_FRAMES = 45

    /**
     * How far to the right of the spot the last of him has gone by the frame after the
     * second picture's last, one more step of the slide's even pace. A screen whose right edge
     * is further from the spot than this would see him vanish before he had left it
     * ([RunWay.spot]).
     */
    const val SLIDE_REACH = 310.4f

    /**
     * For each frame of the second picture, how far to the right of the spot the leftmost thing
     * drawn in it is, he or his smoke; a minus is to the left of the spot. It says in which
     * frame the last of them has left a screen.
     */
    val SLIDE_LEFTMOST =
        floatArrayOf(
            -119.6f, -120.4f, -128.0f, -135.2f, -141.2f, -146.8f, -150.8f, -152.0f, -152.0f,
            -150.4f, -148.0f, -145.6f, -144.0f, -143.2f, -87.6f, -85.6f, -84.0f, -80.4f,
            -79.2f, 56.4f, 88.0f, 108.4f, 108.8f, 110.8f, 112.4f, 114.8f, 116.4f,
            117.6f, 144.4f, 146.4f, 148.0f, 177.2f, 178.4f, 187.6f, 189.2f, 190.8f,
            192.4f, 194.0f, 194.8f, 226.4f, 229.6f, 231.6f, 235.2f, 236.4f, 303.6f,
        )

    /** The sheet, for whoever draws its frames. */
    val SHEET =
        MascotFrames(
            width = SHEET_WIDTH,
            height = SHEET_HEIGHT,
            axis = SHEET_AXIS,
            soles = SHEET_SOLES,
            sheetFrames = SHEET_FRAMES,
            sheetColumns = SHEET_COLUMNS,
            playedFrames = SPOT_FRAMES,
        )
}

/**
 * The run's way across a screen: in from wholly beyond the left edge, on the spot, and out
 * beyond the right edge. Positions are in dp from the screen's left edge.
 *
 * **The spot is the middle of the screen's width,** as in the film, on every screen whose right
 * edge the slide's pictures reach: up to 620 dp wide, which is every phone held upright. On a
 * wider one the spot is as near the middle as lets the last of him leave at the right edge
 * ([RunPicture.SLIDE_REACH]); the run in is as much longer.
 *
 * **The run in** is the greetings' leg ([MascotLeg]): the app draws the frame that is due in
 * that frame's place, [RunPicture.RUN_STEP] to the right of the one before, so his planted foot
 * stays where it is and a phone that draws slowly leaves frames out but never shows him in one
 * place with the step of another. It begins with the frame in which he is first seen, a fist
 * at the left edge, and ends on the last frame of a cycle, one step before the frame in which
 * he gets to the spot.
 *
 * **From there on nothing is moved.** The two animated pictures stand with the spot they were
 * packed about on this screen's spot, and he moves in them.
 *
 * **It is over when the last of him and of his smoke has left the screen:** on a narrow
 * screen sooner than on a wide one, and before the second picture has played through.
 *
 * @param screenWidth the width he runs across, in dp.
 */
internal class RunWay(screenWidth: Float) {
    /** Where he runs on the spot. */
    val spot: Float = max(screenWidth / 2, screenWidth - RunPicture.SLIDE_REACH)

    /** How many frames of the run bring him in. In the first, something of him is seen. */
    val framesIn: Int = generateSequence(1) { it + 1 }.first { !seenBeforeArriving(it + 1) }

    /**
     * How many frames of the second picture show anything on this screen: after the last of
     * them, he and his smoke have left it.
     */
    val slideFrames: Int = RunPicture.SLIDE_LEFTMOST.indexOfLast { spot + it < screenWidth } + 1

    /** When he gets to the spot, and when he brakes, if the phone keeps up: the sounds' moments. */
    val arrivesAtMillis: Long = framesIn.toLong() * MascotPicture.FRAME_MS
    val brakesAtMillis: Long = arrivesAtMillis + RunPicture.SPOT_MS

    /** How long the slide is on this screen, from the brake. */
    val slideMillis: Long = slideFrames.toLong() * MascotPicture.FRAME_MS

    /** How long the whole run takes on a phone that keeps up. */
    val millis: Long = brakesAtMillis + slideMillis

    /** Where the two animated pictures' left edge is put. It may lie beyond the screen's. */
    val playedLeft: Float = spot - RunPicture.SPOT

    /**
     * The run in, [framesIn] frames of the sheet. Once it is over, in frame [framesIn], the
     * sheet is drawn no more: that frame is the first animated picture's first.
     */
    val coming: MascotLeg =
        SteppedLeg(framesIn, MascotPicture.FRAME_MS) { frame ->
            val toGo = framesIn - frame
            MascotPose(
                sheetFrame = (-toGo).takeIf { it < 0 }?.mod(RunPicture.SHEET_FRAMES),
                axis = axisBeforeArriving(toGo),
            )
        }

    /** Where his axis is [frames] frames before the one he gets to the spot in. */
    private fun axisBeforeArriving(frames: Int): Float =
        spot - RunPicture.SHORT_OF_SPOT - RunPicture.RUN_STEP * frames

    /** Whether anything of him is on the screen [frames] frames before he gets to the spot. */
    private fun seenBeforeArriving(frames: Int): Boolean {
        val reach = RunPicture.REACH_AHEAD[(-frames).mod(RunPicture.RUN_CYCLE)]
        return axisBeforeArriving(frames) + reach > 0
    }
}

/** How far a run has come. */
internal enum class RunStage {
    /** The pictures are being read. Nothing is drawn. */
    READING,

    /** He runs in from the left edge: the app steps through the sheet. */
    RUNNING_IN,

    /** He is at the spot: Android plays the first animated picture. */
    ON_THE_SPOT,

    /** He has braked and slides out: Android plays the second. */
    SLIDING,

    /** The run has ended, in whichever way. Nothing is drawn, and nothing follows. */
    OVER,
}

/**
 * What can happen to a run.
 *
 * @property from the stage the event belongs to, or null for one that belongs to every stage.
 * @property to the stage it leads to.
 */
internal enum class RunEvent(val from: RunStage?, val to: RunStage) {
    /** The mascot is about to be drawn for the first time. */
    PICTURES_READ(RunStage.READING, RunStage.RUNNING_IN),

    /** The first animated picture is about to be drawn: he gets to the spot. */
    ARRIVED(RunStage.RUNNING_IN, RunStage.ON_THE_SPOT),

    /** The second is about to be drawn: his brake lights come on. */
    BRAKED(RunStage.ON_THE_SPOT, RunStage.SLIDING),

    /** The last of him and of his smoke has left the screen. */
    GONE(RunStage.SLIDING, RunStage.OVER),

    /**
     * The run ends where it stands: the phone's animations are off, a picture could not be
     * read, MilO was put away in the middle of it, or the screen it plays over was left.
     */
    CUT_SHORT(null, RunStage.OVER),
}

/**
 * One run's course from stage to stage, as [GreetingCourse] is a greeting's. Each event moves
 * it on from the one stage it belongs to and is ignored in every other, so nothing that arrives
 * late or twice can take a run back, start a sound a second time, or end it a second time.
 *
 * @param onStage told each new stage, for the drawing and for the sounds: the stages
 * [RunStage.ON_THE_SPOT] and [RunStage.SLIDING] are each entered once at most, and entering
 * them is the moment of the whirr and of the squeal.
 * @param onDone called when the run is over: **once, whatever happens and in whatever order.**
 * It is told whether the run was played to its end, or cut short.
 */
internal class RunCourse(
    private val onStage: (RunStage) -> Unit,
    private val onDone: (whole: Boolean) -> Unit,
) {
    var stage: RunStage = RunStage.READING
        private set

    fun on(event: RunEvent) {
        val belongs = event.from == null || event.from == stage
        if (stage == RunStage.OVER || !belongs) return
        stage = event.to
        onStage(event.to)
        if (event.to == RunStage.OVER) onDone(event == RunEvent.GONE)
    }
}
