package com.shawnkowalchuk.milo.core.designsystem.component

// What every greeting is made of, apart from its drawing (ADR-007): which greetings there are,
// what their pictures are like, and the words for where the mascot is at a moment. Plain Kotlin
// with unit tests. The wave's own numbers and its two walks are in `MascotWalk.kt`, the moonwalk's
// in `MascotMoonwalk.kt`.

/**
 * The greetings MilO has (since 2026-10-10 there are two). Which of them plays at a start is
 * not decided here: they take turns (`feature/greeting`).
 */
enum class MascotGreetingKind {
    /**
     * The mascot walks in from the left, stops in the middle of the screen with his brake
     * lights coming on behind him, turns to the viewer and waves once, turns away to the right,
     * and walks on and off at the right as the lights go out.
     */
    WAVE,

    /**
     * In his hat he glides in backward from the right, stops in the middle with a skid and his
     * brake lights on, thrusts his hips three times, kicks, turns once round, and glides on and
     * out at the left.
     */
    MOONWALK,
    ;

    /** The greeting that plays if this one's pictures cannot be read, or null if none does. */
    internal val instead: MascotGreetingKind?
        get() = if (this == MOONWALK) WAVE else null

    /** What this greeting's two pictures are like. */
    internal val frames: MascotFrames
        get() = when (this) {
            WAVE -> MascotPicture.FRAMES
            MOONWALK -> MoonwalkPicture.FRAMES
        }

    /** This greeting's way across a screen [screenWidth] dp wide. */
    internal fun wayAcross(screenWidth: Float): MascotWay = when (this) {
        WAVE -> MascotWalk(screenWidth).let { MascotWay(it.coming, it.going) }
        MOONWALK -> MascotGlide(screenWidth).let { MascotWay(it.coming, it.going) }
    }
}

/**
 * What a greeting's two pictures are like, for whoever draws them. A greeting is two pictures
 * with frames of one size from one camera: a still sheet of frames the app steps through while
 * it moves the mascot, and an animated picture Android plays while he stays where he is.
 * Lengths are in dp: the pictures are drawn at this size on every phone, whatever its density.
 *
 * @property width and [height] the size of one frame.
 * @property axis how far from a frame's left edge the mascot stands: the line he turns about.
 * @property soles how far above a frame's lower edge his soles are while the app moves him.
 * @property sheetFrames how many frames the sheet holds: one cycle of his glide, or one cycle
 * of each of his two walks, the walk in's and then the walk off's.
 * @property sheetColumns how many of them lie side by side in a row of the sheet.
 * @property playedFrames how many frames long the animated picture is.
 */
internal class MascotFrames(
    val width: Float,
    val height: Float,
    val axis: Float,
    val soles: Float,
    val sheetFrames: Int,
    val sheetColumns: Int,
    val playedFrames: Int,
) {
    val sheetRows: Int = sheetFrames / sheetColumns

    /** How long the animated picture takes if the phone keeps up with it. */
    val playedMillis: Long = playedFrames.toLong() * MascotPicture.FRAME_MS
}

/**
 * What the screen shows of the mascot at one moment.
 *
 * **His step and his place are one value.** Whoever draws reads both from it in the same
 * drawing of the screen, so he cannot be shown in one place with the step of another. Two that
 * say the same are equal, so a screen that is drawn more often than the mascot steps is not
 * drawn again for nothing.
 *
 * @property sheetFrame the frame of the sheet to draw, or null for the animated picture as
 * Android plays it.
 * @property axis where the mascot stands, in dp from the screen's left edge.
 */
internal data class MascotPose(val sheetFrame: Int?, val axis: Float)

/**
 * A stretch of a greeting that the app plays itself: it steps through the sheet's frames by
 * its own clock and moves the mascot with every one. Moments are milliseconds since the stretch
 * began.
 */
internal interface MascotLeg {
    /** How many frames it shows, one after the other. */
    val frames: Int

    /** The frame that is due at [atMillis], counted from 0; [frames] once the leg is over. */
    fun frameAt(atMillis: Long): Int

    /** What is drawn in [frame], and where. In frame [frames] the leg is over. */
    fun poseIn(frame: Int): MascotPose
}

/**
 * A leg of [frames] frames, each shown for [frameMillis]: the walk in and the walk off, the
 * glide in and the glide out.
 *
 * @param pose what is drawn in a frame from 0 to [frames], and where.
 */
internal class SteppedLeg(
    override val frames: Int,
    private val frameMillis: Int,
    private val pose: (Int) -> MascotPose,
) : MascotLeg {
    override fun frameAt(atMillis: Long): Int =
        (atMillis.coerceIn(0, frames.toLong() * frameMillis) / frameMillis).toInt()

    override fun poseIn(frame: Int): MascotPose = pose(frame.coerceIn(0, frames))
}

/**
 * A greeting's way across the screen: the leg that brings the mascot in, to where Android plays
 * the animated picture, and the leg that takes him out again at the other edge. No greeting
 * fades: it is over when the last of him has left the screen.
 */
internal class MascotWay(val coming: MascotLeg, val going: MascotLeg)
