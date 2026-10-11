package com.shawnkowalchuk.milo.core.designsystem.component

import kotlin.math.ceil
import kotlin.math.max

// The moonwalk's course, apart from its drawing (ADR-007, the addendum of 2026-10-10): what its
// two pictures hold, and where the mascot is at each moment of his glide in and of his glide
// out. Plain Kotlin with unit tests; `MascotGreeting.kt` draws what this works out.

/**
 * What the moonwalk's two pictures hold, as `design/mascot/scripts/pack_moonwalk.py` printed it
 * when it packed them. Lengths are in dp, and the pictures have [MascotPicture.PIXELS_PER_DP]
 * pixels to the dp, as the wave's have. `MascotPicturesTest` holds the two files to the sizes
 * and the frames.
 *
 * The frames are higher than the wave's because of his hat, and the mascot does not stand in
 * their middle: he is drawn half turned, and the kick and the halo of his brake lights need
 * more room on one side than on the other.
 *
 * TODO(debt): the halo round his lit brake lights is stored in the pictures for a dark ground:
 *  red that covers a part of what lies behind. Over Home's dark page and dark tiles that is a
 *  glow; over the lime tile it is an orange mist that darkens the lime. He stands in front of
 *  the lime tile with the phone on its side and in a window about 280 dp wide. See
 *  docs/FINDINGS_LOG.md, 2026-10-10.
 */
internal object MoonwalkPicture {
    /** Every frame of both pictures is this wide and this high. */
    const val WIDTH = 192f
    const val HEIGHT = 192f

    /** The mascot stands this far from a frame's left edge: the line he turns about. */
    const val AXIS = 89.2f

    /** While he glides, his soles are this far above the frame's lower edge. */
    const val SOLES = 4.5f

    /** While he glides, he reaches this far to the left of his axis, the way he goes. */
    const val REACH_AHEAD = 54.8f

    /** While he glides, he reaches this far to the right of his axis, the way he came from. */
    const val REACH_BEHIND = 81.6f

    /** One cycle of the glide, a slide of each foot, carries him this far. */
    const val STRIDE = 80.6f

    /**
     * The glide's picture is one cycle, drawn on the spot: its frames lie side by side on one
     * sheet, [GLIDE_COLUMNS] across, row after row. The frame after the last is the first.
     */
    const val GLIDE_FRAMES = 14
    const val GLIDE_COLUMNS = 7

    /**
     * The other picture is the middle part: the skid with his brake lights on, the pose and the
     * hip thrusts, the kick and the turn, and the way back into the glide. It plays once. Its
     * first frame is the frame the glide would show next, and the frame after its last is the
     * glide's first.
     */
    const val MIDDLE_FRAMES = 80
    const val MIDDLE_MS = MIDDLE_FRAMES * MascotPicture.FRAME_MS

    /** The same, for whoever draws them. */
    val FRAMES =
        MascotFrames(
            width = WIDTH,
            height = HEIGHT,
            axis = AXIS,
            soles = SOLES,
            sheetFrames = GLIDE_FRAMES,
            sheetColumns = GLIDE_COLUMNS,
            playedFrames = MIDDLE_FRAMES,
        )
}

/**
 * The moonwalk's two glides across a screen: in from wholly beyond the right edge to the middle
 * of its width, and, after the middle part, on to wholly beyond the left edge. Positions are
 * where the mascot's axis is, in dp from the screen's left edge.
 *
 * **The foot that is up on its toe stays where it is on the screen,** because each frame of the
 * glide stands a fourteenth of [stride] to the left of the one before it: that is how far the
 * toe has gone forward under him in it. As in the wave's walks ([MascotWalk]), the frame and
 * the place come from one number, so a phone that draws too slowly leaves frames out and cannot
 * show him in one place with the step of another.
 *
 * **The glide in ends on the last frame of a cycle,** because the middle part begins with the
 * frame that follows it. So it begins partway into a cycle, with the mascot just beyond the
 * edge: he is seen at once.
 *
 * **The glide out begins with the first frame of a cycle,** where the mascot stands: the frame
 * that follows the middle part's last one.
 *
 * @param screenWidth the width the mascot glides across, in dp.
 * @param stride how far one cycle of the glide carries the mascot, in dp.
 * @param reachAhead how far the gliding mascot reaches to the left of his axis, in dp.
 * @param reachBehind how far he reaches to the right of it, in dp.
 * @param frameMillis how long one frame of the glide is shown.
 */
internal class MascotGlide(
    screenWidth: Float,
    stride: Float = MoonwalkPicture.STRIDE,
    reachAhead: Float = MoonwalkPicture.REACH_AHEAD,
    reachBehind: Float = MoonwalkPicture.REACH_BEHIND,
    private val frameMillis: Int = MascotPicture.FRAME_MS,
) {
    /** How far one frame of the glide carries him. */
    val perFrame: Float = stride / MoonwalkPicture.GLIDE_FRAMES

    /** Where he does the middle part: the middle of the screen's width. */
    val middle: Float = screenWidth / 2

    /** How many frames of the glide bring him in: in the first, nothing of him is seen yet. */
    val framesIn: Int = max(1, ceil((screenWidth - middle + reachAhead) / perFrame).toInt())

    /** How many frames of the glide take him out: after the last, nothing of him is seen. */
    val framesOut: Int = max(1, ceil((middle + reachBehind) / perFrame).toInt())

    /** How long the glide in takes, and the glide out. */
    val millisIn: Long = framesIn.toLong() * frameMillis
    val millisOut: Long = framesOut.toLong() * frameMillis

    /**
     * How long the whole greeting takes on a phone that keeps up: both glides and the middle
     * part between them.
     */
    val millis: Long = millisIn + MoonwalkPicture.MIDDLE_MS + millisOut

    /**
     * The glide in, [framesIn] frames. Its last frame is the last of a cycle; once it is over,
     * the middle part plays, in the middle.
     */
    val coming: MascotLeg =
        SteppedLeg(framesIn, frameMillis) { frame ->
            val toGo = framesIn - frame
            MascotPose(
                sheetFrame = (-toGo).takeIf { it < 0 }?.mod(MoonwalkPicture.GLIDE_FRAMES),
                axis = middle + perFrame * toGo,
            )
        }

    /**
     * The glide out. **Its first frame is the middle part's last one, once more:** Android says
     * that it shows that frame when it draws it, not when the frame's time is up, so the frame
     * is given its time here. Then come [framesOut] frames of the glide, from the first of a
     * cycle, and once the leg is over the mascot is wholly beyond the left edge.
     */
    val going: MascotLeg =
        SteppedLeg(framesOut + 1, frameMillis) { frame ->
            val glided = frame - 1
            MascotPose(
                sheetFrame = glided.takeIf { it >= 0 }?.mod(MoonwalkPicture.GLIDE_FRAMES),
                axis = middle - perFrame * max(glided, 0),
            )
        }
}
