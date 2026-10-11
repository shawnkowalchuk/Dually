package com.shawnkowalchuk.milo.core.designsystem.component

import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One greeting's pictures, read and ready (ADR-007). The mascot is a 3D model in Blender
 * (`design/mascot/`); what the app carries is that model drawn there once, frame by frame, with
 * a see-through background. Android's own decoder reads them, so the app has no library for it
 * and no native code of its own.
 *
 * Every frame of both is the same size and was drawn by the same camera, so one can take
 * another's place without the mascot moving. What they hold is said in [MascotPicture] for the
 * wave and in [MoonwalkPicture] for the moonwalk.
 *
 * @property kind the greeting these are the pictures of.
 * @property sheet one cycle of the walk, or of the glide, toward the left: a still picture with
 * its frames side by side. The app draws the frame it wants, because the mascot's place has to
 * change with its step, and an animated picture does not say which frame it shows.
 * @property played an animated picture, from the frame that follows the sheet's last one: the
 * turn and the wave, or the moonwalk's middle part. The mascot stands where it is, so Android
 * plays it.
 */
internal class MascotPictures(
    val kind: MascotGreetingKind,
    val sheet: ImageBitmap,
    val played: AnimatedImageDrawable,
) {
    /**
     * Stops the animated picture. It is not played again.
     *
     * **Its listener is left where it is.** Android tells a picture's listeners of a stop a
     * moment later, from a list it reads only then; with the list cleared in between it ends
     * the app (seen on 2026-10-09: a NullPointerException in
     * `AnimatedImageDrawable.postOnAnimationEnd`). The listener goes when the picture does.
     */
    fun close() {
        played.stop()
    }
}

/**
 * Reads both pictures of one greeting, off the main thread, with the first frame of the
 * animated one drawn and waiting. Both at once and before anything is shown, so that the second
 * is there the moment the mascot has come in.
 *
 * @throws IOException if a picture cannot be read, or the second is not an animated one.
 */
internal suspend fun readMascotPictures(
    resources: Resources,
    kind: MascotGreetingKind,
): MascotPictures = withContext(Dispatchers.IO) {
    val files = kind.files
    val sheet = ImageDecoder.createSource(resources, files.sheet)
    MascotPictures(
        kind = kind,
        sheet = ImageDecoder.decodeBitmap(sheet).asImageBitmap(),
        played = readAnimated(resources, files.played, files.playedName),
    )
}

/**
 * The two pictures of MilO's one-year party, read and ready (ADR-007, the party's addendum).
 * Both are animated pictures that Android plays; what they hold is said in
 * [MascotPartyPicture].
 *
 * @property popper from the clip's first frame to the last one before the popper goes off.
 * @property pop from the frame the popper goes off on. The app starts it itself, so that it
 * knows the moment of the pop: Android does not say which frame of a picture it shows.
 */
internal class MascotPartyPictures(
    val popper: AnimatedImageDrawable,
    val pop: AnimatedImageDrawable,
) {
    /** Stops both. Their listeners are left where they are, for [MascotPictures.close]'s reason. */
    fun close() {
        popper.stop()
        pop.stop()
    }
}

/**
 * Reads the party's two pictures, off the main thread, each with its first frame drawn and
 * waiting. Only when a party is about to play: nothing of them is read at an ordinary start.
 *
 * @throws IOException if a picture cannot be read, or is not an animated one.
 */
internal suspend fun readMascotPartyPictures(resources: Resources): MascotPartyPictures =
    withContext(Dispatchers.IO) {
        MascotPartyPictures(
            popper = readAnimated(resources, R.drawable.mascot_party_popper, "mascot_party_popper"),
            pop = readAnimated(resources, R.drawable.mascot_party_pop, "mascot_party_pop"),
        )
    }

/**
 * Reads one animated picture from the app's resources.
 *
 * @param name the picture's name, for the Log. Written out by whoever asks and not asked of
 * Android: the release build packs a resource under another, shorter name (ADR-008), and the
 * line that says a picture could not be read must not count on what Android answers there.
 * @throws IOException if it cannot be read, or is not an animated picture.
 */
private fun readAnimated(resources: Resources, picture: Int, name: String): AnimatedImageDrawable =
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, picture))
        as? AnimatedImageDrawable
        ?: throw IOException("$name is not an animated picture")

/**
 * A greeting's two pictures among the app's resources.
 *
 * @property playedName the second one's name, for the Log ([readAnimated] says why it is
 * written out).
 */
private class MascotFiles(val sheet: Int, val played: Int, val playedName: String)

private val MascotGreetingKind.files: MascotFiles
    get() = when (this) {
        MascotGreetingKind.WAVE ->
            MascotFiles(
                sheet = R.drawable.mascot_walk,
                played = R.drawable.mascot_turn_and_wave,
                playedName = "mascot_turn_and_wave",
            )

        MascotGreetingKind.MOONWALK ->
            MascotFiles(
                sheet = R.drawable.mascot_moonwalk,
                played = R.drawable.mascot_stop_and_spin,
                playedName = "mascot_stop_and_spin",
            )
    }

/**
 * Plays the picture once from its start and leaves it on its last frame.
 *
 * @param onLastFrame called when the last frame is on the screen. It has then been shown for no
 * time at all: Android says so when it draws that frame, not when the frame's time is up.
 * Called once more, and to no purpose, when the picture is stopped.
 */
internal fun AnimatedImageDrawable.playOnce(onLastFrame: () -> Unit) {
    // Android counts the times a picture starts again, not the times it plays.
    repeatCount = 0
    registerAnimationCallback(
        object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) = onLastFrame()
        },
    )
    start()
}

/**
 * The mascot, as one of a greeting's [pictures] shows it, as wide and as high as one of their
 * frames ([MascotFrames]) on every phone. It only draws: which frame of the sheet, when the
 * animated picture plays and where the mascot stands is its caller's business.
 *
 * **Nothing here redraws the animated picture for its next frame.** A screen of MilO is drawn
 * by the phone's graphics chip, and there Android moves an animated picture on by itself,
 * between two of the app's own drawings, for as long as some part of it is on the screen.
 *
 * It is the first thing in the app that is not drawn by Compose's own means: picture files,
 * read by Android. A further use of the mascot goes through this file: a greeting through this
 * component, with its pictures added to [readMascotPictures]; the one-year party, which is two
 * animated pictures and no sheet, through [MascotPlayed] and [readMascotPartyPictures].
 *
 * @param sheetFrame the frame of the sheet to draw, or null for the animated picture as it
 * plays. Asked while drawing, so a new frame draws again and nothing is composed or laid out
 * for it.
 */
@Composable
internal fun Mascot(
    pictures: MascotPictures,
    sheetFrame: () -> Int?,
    modifier: Modifier = Modifier,
) {
    val frames = pictures.kind.frames
    Spacer(
        modifier =
            modifier
                .size(frames.width.dp, frames.height.dp)
                .drawBehind {
                    val whole = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    val frame = sheetFrame()
                    if (frame != null) {
                        drawSheetFrame(pictures.sheet, frames, frame, whole)
                    } else {
                        drawPlayed(pictures.played, whole)
                    }
                },
    )
}

/**
 * One animated picture of the mascot as Android plays it, [width] by [height]: the party's two
 * are drawn with it, one after the other. Like [Mascot] it only draws, and nothing here redraws
 * the picture for its next frame.
 *
 * @param picture the picture to draw. Asked while drawing, so another picture taking its place
 * draws again and nothing is composed or laid out for it.
 */
@Composable
internal fun MascotPlayed(
    picture: () -> AnimatedImageDrawable,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier.size(width, height).drawBehind {
                drawPlayed(picture(), IntSize(size.width.roundToInt(), size.height.roundToInt()))
            },
    )
}

/** Draws an animated picture as it plays, [whole] in size. */
private fun DrawScope.drawPlayed(played: AnimatedImageDrawable, whole: IntSize) {
    played.setBounds(0, 0, whole.width, whole.height)
    drawIntoCanvas { played.draw(it.nativeCanvas) }
}

/** Draws one frame of a [sheet], [whole] in size: the sheet's frames lie row after row. */
private fun DrawScope.drawSheetFrame(
    sheet: ImageBitmap,
    frames: MascotFrames,
    frame: Int,
    whole: IntSize,
) {
    val one = IntSize(sheet.width / frames.sheetColumns, sheet.height / frames.sheetRows)
    val inCycle = frame.mod(frames.sheetFrames)
    val column = inCycle % frames.sheetColumns
    val row = inCycle / frames.sheetColumns
    drawImage(
        image = sheet,
        srcOffset = IntOffset(column * one.width, row * one.height),
        srcSize = one,
        dstSize = whole,
    )
}
