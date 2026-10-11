package com.shawnkowalchuk.milo.core.designsystem.component

import kotlin.math.ceil
import kotlin.math.max

// The wave's course, apart from its drawing (ADR-007, the addendum of 2026-10-11): what its two
// pictures hold, where the mascot is at each moment of his walk in and of his walk off, and
// which stage of a greeting follows which. Plain Kotlin with unit tests; `MascotGreeting.kt`
// draws what this works out. What every greeting is made of is in `MascotGreetingKind.kt`, and
// the moonwalk's numbers and glides in `MascotMoonwalk.kt`.

/**
 * What the wave's two pictures hold, as `design/mascot/scripts/pack_wave2.py` printed it when
 * it packed them, and three numbers every picture of the mascot shares. Lengths are in dp.
 * `MascotPicturesTest` holds the two files to the sizes and the frames. The moonwalk's are in
 * [MoonwalkPicture].
 *
 * The mascot does not stand in the middle of a frame: he waves with the arm on one side, and
 * the walks reach further the way he goes than the way he came from.
 *
 * TODO(debt): the halo round his lit brake lights is stored in the pictures for a dark ground,
 *  as the moonwalk's is ([MoonwalkPicture]): red that covers a part of what lies behind. The
 *  lights are lit while he stands in the middle of the screen. Over Home's dark page and dark
 *  tiles the halo is a glow; over the lime tile it is an orange mist that darkens the lime. He
 *  stands in front of the lime tile with the phone on its side and in a window about 280 dp
 *  wide. See docs/FINDINGS_LOG.md, 2026-10-11.
 *
 * TODO(debt): the sheet holds both walks, and takes 22.8 MB of the phone's graphics memory for
 *  as long as the wave is on the screen, where a greeting's sheet took about 12. See
 *  docs/FINDINGS_LOG.md, 2026-10-11.
 */
internal object MascotPicture {
    /** Every frame of the wave's two pictures is this wide and this high. */
    const val WIDTH = 216f
    const val HEIGHT = 176f

    /** Every picture of the mascot in a greeting has this many pixels to the dp. */
    const val PIXELS_PER_DP = 2.5f

    /** The standing mascot is this high, in both greetings. */
    const val TALL = 150f

    /** The mascot stands this far from a frame's left edge: the line he turns about. */
    const val AXIS = 118.4f

    /**
     * While he walks in, his soles are this far above the frame's lower edge. Walking off, the
     * lowest of him is 1.2 dp higher: he is seen from another side, and the floor is the same.
     */
    const val SOLES = 5.0f

    /** Walking in, he reaches this far to the right of his axis, the way he goes. */
    const val REACH_AHEAD = 79.2f

    /** Walking off, he reaches this far to the left of his axis, the way he came from. */
    const val REACH_BEHIND = 61.2f

    /** One cycle of either walk, two steps, carries the mascot this far. */
    const val STRIDE = 71.43f

    /** Every frame of every picture of the mascot is shown for this long: 24 frames a second. */
    const val FRAME_MS = 42

    /** One cycle of the walk in, and of the walk off, is this many frames. */
    const val WALK_FRAMES = 12

    /**
     * The sheet holds one cycle of each walk, drawn on the spot: the walk in's frames and then
     * the walk off's, in which he is seen from behind. They lie side by side, [SHEET_COLUMNS]
     * across, row after row. In either walk the frame after the cycle's last is its first.
     */
    const val SHEET_FRAMES = 2 * WALK_FRAMES
    const val SHEET_COLUMNS = 4

    /**
     * The other picture is the middle part: the stop with his brake lights coming on, the turn
     * to the viewer, the wave, the turn away with the lights lit, and the foot lifted for the
     * first step off as they go out. It plays once. Its first frame is the frame the walk in
     * would show next, and the frame after its last is the walk off's first.
     */
    const val MIDDLE_FRAMES = 80
    const val MIDDLE_MS = MIDDLE_FRAMES * FRAME_MS

    /** The same, for whoever draws them. */
    val FRAMES =
        MascotFrames(
            width = WIDTH,
            height = HEIGHT,
            axis = AXIS,
            soles = SOLES,
            sheetFrames = SHEET_FRAMES,
            sheetColumns = SHEET_COLUMNS,
            playedFrames = MIDDLE_FRAMES,
        )
}

/**
 * How long an animated picture is waited for past the time it should have taken, if it does not
 * say it has ended. Android moves such a picture on by itself, and only while it is being drawn.
 */
internal const val PICTURE_PATIENCE_MS = 1_000L

/**
 * The wave's two walks across a screen: in from wholly beyond the left edge to the middle of
 * its width, and, after the middle part, on to wholly beyond the right edge. Positions are
 * where the mascot's axis is, in dp from the screen's left edge. It is the moonwalk's way
 * across ([MascotGlide]) the other way round, with a sheet that holds a cycle for each leg.
 *
 * **The step and the place come from one number,** the frame that is due: the app draws that
 * frame of the sheet in that frame's place. A phone that draws too slowly leaves frames out; it
 * cannot show the mascot in one place with the step of another, and a walk ends when the clock
 * says so.
 *
 * **The feet do not slide,** because each frame of a walk stands a twelfth of [stride] to the
 * right of the one before it: that is how far the planted foot has gone back under him in it.
 *
 * **The walk in ends on the last frame of its cycle,** because the middle part begins with the
 * frame that follows it. So it begins partway into a cycle, with the mascot just beyond the
 * edge: he is seen at once.
 *
 * **The walk off begins with the first frame of its cycle,** where the mascot stands: the frame
 * that follows the middle part's last one. It is over when the last of him has passed the right
 * edge. He does not fade.
 *
 * @param screenWidth the width the mascot walks across, in dp.
 * @param stride how far one cycle of either walk carries the mascot, in dp.
 * @param reachAhead how far he reaches to the right of his axis while he walks in, in dp.
 * @param reachBehind how far he reaches to the left of it while he walks off, in dp.
 * @param frameMillis how long one frame of a walk is shown.
 */
internal class MascotWalk(
    screenWidth: Float,
    stride: Float = MascotPicture.STRIDE,
    reachAhead: Float = MascotPicture.REACH_AHEAD,
    reachBehind: Float = MascotPicture.REACH_BEHIND,
    frameMillis: Int = MascotPicture.FRAME_MS,
) {
    /** How far one frame of a walk carries him. */
    val perFrame: Float = stride / MascotPicture.WALK_FRAMES

    /** Where he does the middle part: the middle of the screen's width. */
    val middle: Float = screenWidth / 2

    /** How many frames of the walk bring him in: in the first, nothing of him is seen yet. */
    val framesIn: Int = max(1, ceil((middle + reachAhead) / perFrame).toInt())

    /** How many frames of the walk take him off: after the last, nothing of him is seen. */
    val framesOut: Int = max(1, ceil((screenWidth - middle + reachBehind) / perFrame).toInt())

    /** How long the walk in takes, and the walk off. */
    val millisIn: Long = framesIn.toLong() * frameMillis
    val millisOut: Long = framesOut.toLong() * frameMillis

    /**
     * How long the whole greeting takes on a phone that keeps up: both walks and the middle
     * part between them.
     */
    val millis: Long = millisIn + MascotPicture.MIDDLE_MS + millisOut

    /**
     * The walk in, [framesIn] frames of the sheet's first cycle. Its last frame is the last of
     * that cycle; once it is over, the middle part plays, in the middle.
     */
    val coming: MascotLeg =
        SteppedLeg(framesIn, frameMillis) { frame ->
            val toGo = framesIn - frame
            MascotPose(
                sheetFrame = (-toGo).takeIf { it < 0 }?.mod(MascotPicture.WALK_FRAMES),
                axis = middle - perFrame * toGo,
            )
        }

    /**
     * The walk off. **Its first frame is the middle part's last one, once more:** Android says
     * that it shows that frame when it draws it, not when the frame's time is up, so the frame
     * is given its time here. Then come [framesOut] frames of the sheet's second cycle, from
     * its first, and once the leg is over the mascot is wholly beyond the right edge.
     */
    val going: MascotLeg =
        SteppedLeg(framesOut + 1, frameMillis) { frame ->
            val walked = frame - 1
            MascotPose(
                sheetFrame =
                    walked
                        .takeIf { it >= 0 }
                        ?.let { MascotPicture.WALK_FRAMES + it.mod(MascotPicture.WALK_FRAMES) },
                axis = middle + perFrame * max(walked, 0),
            )
        }
}

/** How far a greeting has come. Each of the greetings goes through the same stages. */
internal enum class GreetingStage {
    /** The pictures are being read. Nothing is drawn. */
    READING,

    /** The mascot comes in from an edge: he walks in at the left, or glides in at the right. */
    COMING,

    /** He is in the middle of the screen, and Android plays the animated picture. */
    IN_THE_MIDDLE,

    /** He goes on and out at the other edge: he walks off at the right, or glides out left. */
    LEAVING,

    /** The greeting has ended, in whichever way. Nothing is drawn, and nothing follows. */
    OVER,
}

/**
 * What can happen to a greeting.
 *
 * @property from the stage the event belongs to, or null for one that belongs to every stage.
 * @property to the stage it leads to.
 */
internal enum class GreetingEvent(val from: GreetingStage?, val to: GreetingStage) {
    /** The mascot is about to be drawn for the first time: from here on he has been shown. */
    PICTURES_READ(GreetingStage.READING, GreetingStage.COMING),
    ARRIVED(GreetingStage.COMING, GreetingStage.IN_THE_MIDDLE),
    MIDDLE_OVER(GreetingStage.IN_THE_MIDDLE, GreetingStage.LEAVING),
    GONE(GreetingStage.LEAVING, GreetingStage.OVER),

    /**
     * The greeting ends where it stands: the phone's animations are off, a picture could not
     * be read, or MilO was put away in the middle of it.
     */
    CUT_SHORT(null, GreetingStage.OVER),
}

/**
 * One greeting's course from stage to stage. Each event moves it on from the one stage it
 * belongs to and is ignored in every other, so nothing that arrives late or twice can take a
 * greeting back, or end it a second time.
 *
 * @param onStage told each new stage, for the drawing.
 * @param onDone called when the greeting is over: **once, whatever happens and in whatever
 * order.**
 */
internal class GreetingCourse(
    private val onStage: (GreetingStage) -> Unit,
    private val onDone: () -> Unit,
) {
    var stage: GreetingStage = GreetingStage.READING
        private set

    fun on(event: GreetingEvent) {
        val belongs = event.from == null || event.from == stage
        if (stage == GreetingStage.OVER || !belongs) return
        stage = event.to
        onStage(event.to)
        if (event.to == GreetingStage.OVER) onDone()
    }
}
