package com.shawnkowalchuk.milo.core.designsystem.component

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * MilO's greeting, one of two ([MascotGreetingKind]).
 *
 * **The wave** (Shawn, 2026-10-09: "walked from the right side of the screen to the center at
 * the bottom of the screen. walking just above the bottom navigation than turned and waved").
 * The mascot comes in from beyond the right edge and walks to the left along the top of the
 * bottom bar; in the middle of the screen it turns to the viewer, waves once and fades away.
 *
 * **The moonwalk** (Shawn, 2026-10-10: "moon walking on to the screen stops mid way. adoes some
 * hip thrusts than spins and continues moon walking", in a hat). He glides in backward along
 * the same line, does the middle part in the middle of the screen, and glides on until he is
 * wholly beyond the left edge. It does not fade.
 *
 * Both are played the same way: the app steps through a sheet of frames by its own clock and
 * moves the mascot with every one, Android plays an animated picture while he stays where he
 * is, and the one takes the other's place in the same drawing of the screen.
 *
 * **It takes nothing from the screen under it.** It is a drawing and no more: no press, no
 * Back and no screen reader ever meets it, the screen is not dimmed, and every button under
 * the mascot works while it goes across.
 *
 * **[onDone] is called once, however the greeting ends:** when the mascot has gone; at once,
 * with nothing drawn, while the phone's animations are switched off or when no picture can be
 * read; and when MilO is put away in the middle of it, which is also how a turn of the phone
 * ends it.
 *
 * Whether there is a greeting at all, and which of the two, is not decided here
 * (`feature/greeting`); which frame is due at each moment, and where the mascot is in it, is
 * worked out in `MascotWalk.kt` and `MascotMoonwalk.kt`.
 *
 * @param kind the greeting to play. If its pictures cannot be read, the one that stands in for
 * it plays ([MascotGreetingKind.instead]).
 * @param barHeight the room the bottom bar takes at the foot of the screen, with everything
 * under it: what a `Scaffold` reports as its content's bottom padding.
 * @param onShown called once, when the mascot is about to be drawn for the first time. A
 * greeting that ends before that has shown nothing.
 * @param onUnreadable told of each greeting whose pictures could not be read, and why.
 */
@Composable
fun MascotGreeting(
    kind: MascotGreetingKind,
    onDone: () -> Unit,
    barHeight: Dp,
    modifier: Modifier = Modifier,
    onShown: () -> Unit = {},
    onUnreadable: (MascotGreetingKind, IOException) -> Unit = { _, _ -> },
) {
    var stage by remember { mutableStateOf(GreetingStage.READING) }
    // The greeting outlives a recomposition; these keep it calling the newest functions.
    val newestOnDone by rememberUpdatedState(onDone)
    val newestOnShown by rememberUpdatedState(onShown)
    val newestOnUnreadable by rememberUpdatedState(onUnreadable)
    val course =
        remember {
            GreetingCourse(
                onStage = {
                    stage = it
                    if (it == GreetingStage.COMING) newestOnShown()
                },
                onDone = { newestOnDone() },
            )
        }

    // Put away in the middle of a greeting, MilO does not carry on with it when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { course.on(GreetingEvent.CUT_SHORT) }

    // Leaving the composition is what stops the greeting (the effect in GreetingOnScreen).
    if (stage != GreetingStage.OVER) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            GreetingOnScreen(kind, course, barHeight) { which, why ->
                newestOnUnreadable(which, why)
            }
        }
    }
}

/** Plays one greeting through, telling [course] of each step, and draws the mascot. */
@Composable
private fun BoxWithConstraintsScope.GreetingOnScreen(
    kind: MascotGreetingKind,
    course: GreetingCourse,
    barHeight: Dp,
    onUnreadable: (MascotGreetingKind, IOException) -> Unit,
) {
    val resources = LocalResources.current
    // Read once: a window that is resized under the greeting does not start it again.
    val screenWidth = remember { maxWidth.value }
    var pictures by remember { mutableStateOf<MascotPictures?>(null) }
    // What is drawn of the mascot, and where. His step and his place are one value, and both
    // are read from it while drawing: they cannot come apart.
    var pose by remember { mutableStateOf(MascotPose(sheetFrame = 0, axis = 0f)) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val animationsOff = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
        val read = if (animationsOff) null else readFirstReadable(resources, kind, onUnreadable)
        if (read == null) {
            course.on(GreetingEvent.CUT_SHORT)
            return@LaunchedEffect
        }
        try {
            val way = read.kind.wayAcross(screenWidth)
            pose = way.coming.poseIn(0)
            pictures = read
            course.on(GreetingEvent.PICTURES_READ)
            val played = CompletableDeferred<Unit>()
            step(way.coming) { frame ->
                pose = way.coming.poseIn(frame)
                // Still before the screen is drawn: the drawing that no longer shows the sheet
                // is the one that shows the animated picture's first frame, and starts its clock.
                if (frame == way.coming.frames) {
                    read.played.playOnce { played.complete(Unit) }
                    course.on(GreetingEvent.ARRIVED)
                }
            }
            withTimeoutOrNull(read.kind.frames.playedMillis + PICTURE_PATIENCE_MS) {
                played.await()
            }
            course.on(GreetingEvent.MIDDLE_OVER)
            if (way.going == null) {
                fade.animateTo(0f, tween(LEAVE_MS))
            } else {
                step(way.going) { frame -> pose = way.going.poseIn(frame) }
            }
            course.on(GreetingEvent.GONE)
        } finally {
            // Also when the greeting is cut short, or leaves the screen in the middle.
            read.close()
        }
    }

    // Nothing is drawn while the pictures are being read.
    val shown = pictures
    if (shown != null) {
        val frames = shown.kind.frames
        val shadow = MaterialTheme.colorScheme.scrim.copy(alpha = SHADOW_DARKEST)
        // The bar's tile begins rowGap below the top of the room the bar takes
        // (MiloNavigationBar); the mascot's soles are a small step above the tile.
        val spacing = MiloTheme.spacing
        val solesAboveFoot = barHeight - spacing.rowGap + spacing.extraSmall
        Box(
            modifier =
                Modifier
                    .align(AbsoluteAlignment.BottomLeft)
                    .offset(y = frames.soles.dp - solesAboveFoot)
                    // Read while drawing, so a step moves the mascot and nothing is composed
                    // or laid out again.
                    .graphicsLayer {
                        translationX = (pose.axis - frames.axis).dp.toPx()
                        alpha = fade.value
                    }.drawBehind { groundShadow(shadow, frames.axis.dp, frames.soles.dp) },
        ) {
            Mascot(shown, sheetFrame = { pose.sheetFrame })
        }
    }
}

/**
 * Reads the pictures of [kind], or, if they cannot be read, of the greeting that plays instead
 * of it. [onUnreadable] is told of each one that could not be read. Null if none could.
 */
private suspend fun readFirstReadable(
    resources: Resources,
    kind: MascotGreetingKind,
    onUnreadable: (MascotGreetingKind, IOException) -> Unit,
): MascotPictures? {
    var wanted: MascotGreetingKind? = kind
    while (wanted != null) {
        try {
            return readMascotPictures(resources, wanted)
        } catch (unreadable: IOException) {
            onUnreadable(wanted, unreadable)
            wanted = wanted.instead
        }
    }
    return null
}

/**
 * Takes the mascot along one [leg]. Before every drawing of the screen, [onFrame] is told the
 * frame that is due ([MascotLeg.frameAt]); the last it is told is [MascotLeg.frames], when the
 * leg is over. What [onFrame] does is drawn in that same drawing of the screen.
 *
 * The leg's clock is the screen's frame time, read as it is and not stretched by the phone's
 * "animator duration scale": the animated picture plays at its own pace whatever that is set
 * to. The clock starts with a drawing of the screen that shows what was there before the leg.
 */
private suspend fun step(leg: MascotLeg, onFrame: (Int) -> Unit) {
    val began = withFrameNanos { it }
    do {
        val frame =
            withFrameNanos { now -> leg.frameAt((now - began) / NANOS_IN_MILLI).also(onFrame) }
    } while (frame < leg.frames)
}

/**
 * A faint, soft patch of dark on the ground under the mascot's feet, where he stands. The
 * greetings and the one-year party draw it.
 *
 * @param axis where he stands, from the left edge of what is being drawn.
 * @param soles how far his soles are above its lower edge.
 * @param times how many times as high he is drawn as in the greetings.
 */
internal fun DrawScope.groundShadow(darkest: Color, axis: Dp, soles: Dp, times: Float = 1f) {
    val radius = SHADOW_WIDTH.toPx() * times / 2
    val centre =
        Offset(
            x = axis.toPx(),
            y = size.height - soles.toPx() - SHADOW_ABOVE_SOLES.toPx() * times,
        )
    val fading = Brush.radialGradient(listOf(darkest, Color.Transparent), centre, radius)
    scale(scaleX = 1f, scaleY = SHADOW_HEIGHT / SHADOW_WIDTH, pivot = centre) {
        drawCircle(fading, radius, centre)
    }
}

/** How long the waving mascot takes to fade away. */
private const val LEAVE_MS = 260

private const val NANOS_IN_MILLI = 1_000_000L

/** The shadow: about as wide as the mascot's two shoes, and flat. */
private val SHADOW_WIDTH = 92.dp
private val SHADOW_HEIGHT = 14.dp

/** The shadow's middle, above the mascot's soles: between the nearer foot and the other. */
private val SHADOW_ABOVE_SOLES = 3.4.dp

/** How dark the shadow is in its middle. It fades to nothing at its rim. */
internal const val SHADOW_DARKEST = 0.5f
