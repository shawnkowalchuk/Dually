package com.shawnkowalchuk.milo.core.designsystem.component

import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The run's pictures, read and drawn. They are read and drawn by `Mascot.kt`'s means, and stand
// in a file of their own only because that file is at its size limit (ENGINEERING_STANDARDS
// section 3).

/**
 * The three pictures of the run across the Report screen, read and ready (ADR-007, the run's
 * addendum). What they hold is said in [RunPicture].
 *
 * @property sheet the frames of the run in, drawn on the spot: a still picture with its frames
 * side by side, which the app steps through while it moves the mascot.
 * @property onSpot an animated picture: from the frame he gets to the spot to the last one
 * before he brakes.
 * @property slide an animated picture: from the frame his brake lights come on in. The app
 * starts it itself, so that it knows the moment of the brake.
 */
internal class MascotRunPictures(
    val sheet: ImageBitmap,
    val onSpot: AnimatedImageDrawable,
    val slide: AnimatedImageDrawable,
) {
    /** Stops both. Their listeners are left where they are, for [MascotPictures.close]'s reason. */
    fun close() {
        onSpot.stop()
        slide.stop()
    }
}

/**
 * Reads the run's three pictures, off the main thread, each animated one with its first frame
 * drawn and waiting. Only when a run is about to play: nothing of them is read at a start.
 *
 * @throws IOException if a picture cannot be read, or one of the two is not an animated one.
 */
internal suspend fun readMascotRunPictures(resources: Resources): MascotRunPictures =
    withContext(Dispatchers.IO) {
        val sheet = ImageDecoder.createSource(resources, R.drawable.mascot_run_in)
        MascotRunPictures(
            sheet = ImageDecoder.decodeBitmap(sheet).asImageBitmap(),
            onSpot =
                readAnimated(
                    resources,
                    R.drawable.mascot_run_on_the_spot,
                    "mascot_run_on_the_spot",
                ),
            slide =
                readAnimated(
                    resources,
                    R.drawable.mascot_brake_and_slide,
                    "mascot_brake_and_slide",
                ),
        )
    }

/**
 * One frame of a sheet of the mascot, as wide and as high as [frames] say: the run in of the
 * run across the Report screen is drawn with it. Like [Mascot] it only draws.
 *
 * @param frame the frame of the sheet to draw, or null for nothing at all. Asked while drawing,
 * so a new frame draws again and nothing is composed or laid out for it.
 */
@Composable
internal fun MascotSheet(
    sheet: ImageBitmap,
    frames: MascotFrames,
    frame: () -> Int?,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier.size(frames.width.dp, frames.height.dp).drawBehind {
                val due = frame() ?: return@drawBehind
                val whole = IntSize(size.width.roundToInt(), size.height.roundToInt())
                drawSheetFrame(sheet, frames, due, whole)
            },
    )
}
