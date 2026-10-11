package com.shawnkowalchuk.milo.core.designsystem.component

// The party's course, apart from its drawing (ADR-007, the addendum of 2026-10-10 for the
// party): what its two pictures hold, where the mascot stands on a screen, how strongly he is
// drawn at each moment, and which stage of a party follows which. Plain Kotlin with unit tests;
// `MascotParty.kt` draws what this works out.

/**
 * What the party's two pictures hold, as `design/mascot/scripts/pack_party.py` printed it when
 * it packed them. `MascotPicturesTest` holds the two files to the pixels, the frames and the
 * times.
 *
 * **Lengths are in dp, as large as the party is shown on a screen it fits on,** which is how
 * large the film showed it that Shawn was shown (2026-10-10): the mascot [TALL] high on a
 * screen 411 dp wide, more than half as high again as in the greetings. The pictures have fewer
 * pixels to the dp than the greetings' (1.64 where those have 2.5), which is what keeps the
 * party under two and a half megabytes: he is as many pixels high as in the greetings, and
 * shown larger. He is a little softer for it.
 *
 * Both pictures are animated ones that Android plays, with frames of one size from one camera:
 * the second takes the first's place without the mascot moving. A frame is a fixed room round
 * him, the film's own, not measured from what is in it: confetti that drifts out of it is to
 * drift out of it.
 *
 * TODO(debt): he is softer than in the film Shawn approved, and than the greetings' mascot: the
 *  numbers on his dials most of all. It is what the party's weight was bought with. Packed as
 *  they are now, the pictures would weigh 0.56 MB more with him 450 pixels high, and 0.98 MB
 *  more at the film's own 600. See docs/FINDINGS_LOG.md, 2026-10-10.
 */
internal object MascotPartyPicture {
    /** A frame of either file, in pixels. */
    const val PIXELS_WIDE = 675
    const val PIXELS_HIGH = 800

    /**
     * The pictures have this many pixels to the dp when the party is shown as large as in the
     * film: the film's 2.625, times the five eighths the film's frames were made smaller by.
     */
    const val PIXELS_PER_DP = 1.640625f

    /** A frame as it is shown: 411.4 by 487.6 dp. */
    const val WIDTH = PIXELS_WIDE / PIXELS_PER_DP
    const val HEIGHT = PIXELS_HIGH / PIXELS_PER_DP

    /** The mascot stands this far from a frame's left edge, its middle: 337.5 pixels, 205.7 dp. */
    const val AXIS = WIDTH / 2

    /** His soles are this far above the frame's lower edge: 37.5 pixels, 22.9 dp. */
    const val SOLES = 37.5f / PIXELS_PER_DP

    /** He stands this high, without his cap: 375 pixels, 228.6 dp. */
    const val TALL = 375 / PIXELS_PER_DP

    /** How far a frame reaches above the middle of his height: the room the confetti has. */
    const val ABOVE_HIS_MIDDLE = HEIGHT - SOLES - TALL / 2

    /**
     * He stands still for the clip's first frames, which is what he is faded in on. **The app
     * shows the first picture's first frame for that long before it lets Android play the
     * picture.** The file could say "show the first frame for thirteen frames' time", and did
     * at first; Android let that frame go after about a tenth of a second, and the popper went
     * off half a second early (seen on an emulator, FINDINGS_LOG 2026-10-10). A frame that is
     * held in the middle of a picture is kept for its time.
     */
    const val STILL_FRAMES = 12
    const val STILL_MS = STILL_FRAMES * MascotPicture.FRAME_MS

    /**
     * The first picture: from the frame he stands still in to the last one before the popper
     * goes off, the clip's 13th frame to its 73rd.
     */
    const val POPPER_FRAMES = 61
    const val POPPER_MS = POPPER_FRAMES * MascotPicture.FRAME_MS

    /**
     * How long the first picture's first frame is on the screen: the frames he stands still
     * in, and its own.
     */
    const val FIRST_FRAME_MS = STILL_MS + MascotPicture.FRAME_MS

    /** The popper goes off this long after he is first drawn, if the phone keeps up. */
    const val POP_AT_MS = STILL_MS + POPPER_MS

    /**
     * The second picture: from the frame the popper goes off on, the 74th of the clip, to the
     * last frame anything of him is seen in.
     */
    const val POP_FRAMES = 57
    const val POP_MS = POP_FRAMES * MascotPicture.FRAME_MS

    /** He is faded in over this many frames from the start, and out over as many to the end. */
    const val FADE_FRAMES = STILL_FRAMES
    const val FADE_MS = FADE_FRAMES * MascotPicture.FRAME_MS

    /**
     * How strongly he is drawn, of 1, [sinceMillis] after he was first drawn: from nothing to
     * wholly there over [FADE_MS], eased at both ends as the film's fade is.
     */
    fun fadeIn(sinceMillis: Long): Float = eased(sinceMillis.toFloat() / FADE_MS)

    /**
     * How strongly he is drawn, of 1, [sinceMillis] after the second picture began: wholly
     * there until [FADE_MS] before its end, and nothing at its end ([POP_MS]) and after.
     */
    fun fadeOut(sinceMillis: Long): Float = eased((POP_MS - sinceMillis).toFloat() / FADE_MS)

    /** The film's ease (`preview_party.py`, `smooth`): slow, fast, slow, from 0 to 1. */
    private fun eased(part: Float): Float {
        val t = part.coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

/**
 * Where the party stands on a screen, and how large. Lengths are in dp.
 *
 * **He stands in the middle of the screen,** as Shawn asked ("fade in with the mascot in the
 * center") and as the film showed him: the middle of his height is the middle of the screen's,
 * and his axis the middle of its width.
 *
 * **He is as large as in the film where the screen has the room, and smaller where it has
 * not:** the frame is never wider than the screen, and the room the confetti goes up into
 * never reaches above its top. On the film's screen, 411 by 914 dp, that is the film's size. On
 * a narrower phone he is a little smaller and fills the same part of the screen's width. With
 * the phone on its side he is about half the size.
 *
 * @param screenWidth and [screenHeight] the whole screen, with the room Android's own bars take.
 */
internal class MascotPartyPlace(screenWidth: Float, screenHeight: Float) {
    /** How many times as large as [MascotPartyPicture] says the party is drawn: 1 at most. */
    val scale: Float =
        minOf(
            1f,
            screenWidth / MascotPartyPicture.WIDTH,
            (screenHeight / 2) / MascotPartyPicture.ABOVE_HIS_MIDDLE,
        )

    /** A frame as it is drawn on this screen. */
    val width: Float = MascotPartyPicture.WIDTH * scale
    val height: Float = MascotPartyPicture.HEIGHT * scale

    /** How high he stands on this screen. */
    val tall: Float = MascotPartyPicture.TALL * scale

    /** Where his axis is in the drawn frame, and how far his soles are above its lower edge. */
    val axis: Float = MascotPartyPicture.AXIS * scale
    val soles: Float = MascotPartyPicture.SOLES * scale

    /** The drawn frame's left edge, from the screen's: it may lie beyond it. */
    val left: Float = screenWidth / 2 - axis

    /** The drawn frame's upper edge, from the screen's. */
    val top: Float = screenHeight / 2 + tall / 2 + soles - height
}

/** How far a party has come. */
internal enum class PartyStage {
    /** The pictures are being read. Nothing is drawn. */
    READING,

    /** He is on the screen and has not fired the popper yet: Android plays the first picture. */
    BEFORE_THE_POP,

    /** The popper has gone off: Android plays the second picture. */
    POPPED,

    /** The party has ended, in whichever way. Nothing is drawn, and nothing follows. */
    OVER,
}

/**
 * What can happen to a party.
 *
 * @property from the stage the event belongs to, or null for one that belongs to every stage.
 * @property to the stage it leads to.
 */
internal enum class PartyEvent(val from: PartyStage?, val to: PartyStage) {
    /** The mascot is about to be drawn for the first time: from here on he has been shown. */
    PICTURES_READ(PartyStage.READING, PartyStage.BEFORE_THE_POP),

    /** The second picture is about to be drawn for the first time: this is the pop. */
    POP(PartyStage.BEFORE_THE_POP, PartyStage.POPPED),
    GONE(PartyStage.POPPED, PartyStage.OVER),

    /**
     * The party ends where it stands: the phone's animations are off, a picture could not be
     * read, or MilO was put away in the middle of it.
     */
    CUT_SHORT(null, PartyStage.OVER),
}

/**
 * One party's course from stage to stage, as [GreetingCourse] is a greeting's. Each event moves
 * it on from the one stage it belongs to and is ignored in every other, so nothing that arrives
 * late or twice can take a party back, pop a second time, or end it a second time.
 *
 * @param onStage told each new stage, for the drawing and for the sound: the stage
 * [PartyStage.POPPED] is entered once at most, and entering it is the moment of the pop.
 * @param onDone called when the party is over: **once, whatever happens and in whatever
 * order.** It is told whether the popper went off before the end.
 */
internal class PartyCourse(
    private val onStage: (PartyStage) -> Unit,
    private val onDone: (popped: Boolean) -> Unit,
) {
    var stage: PartyStage = PartyStage.READING
        private set

    private var popped = false

    fun on(event: PartyEvent) {
        val belongs = event.from == null || event.from == stage
        if (stage == PartyStage.OVER || !belongs) return
        stage = event.to
        if (event.to == PartyStage.POPPED) popped = true
        onStage(event.to)
        if (event.to == PartyStage.OVER) onDone(popped)
    }
}
