package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
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
 * The mascot's run across the screen (Shawn, 2026-10-10: "the mascot runs on to the screen.
 * than he runs in one spot in the center for 2 seconds. than sort of turns and slides out. if
 * we can make smoke from his shoes or something visual that would be cool. the lights on his
 * butt should be off tell he stops and slides and than turn on like a car stopping"). He runs
 * in from beyond the left edge along the top of the bottom bar, dust at his heels; in the
 * middle of the screen he spins his wheels up and runs on the spot for two seconds, his shoes
 * smoking; he crouches, bolts, brakes with his brake lights coming on, swings round and slides
 * out beyond the right edge, the smoke left lying behind him. Four and three quarter seconds
 * on a phone, as the film he approved. MilO plays it when a report has been recorded as sent
 * with "I sent it" (`feature/sentrun`).
 *
 * It is played as the greetings and the party are (ADR-007): the app steps through a sheet of
 * frames by its own clock for the run in and moves the mascot with every one; Android plays
 * two animated pictures after it, which stand still, with him moving in them. **The app starts
 * each of the two,** as it starts the party's second, so it knows the two moments its sounds
 * belong to: [onArrived] is called
 * in the drawing of the screen that first shows him at the spot, and [onBraked] in the one that
 * first shows his brake lights lit. No second clock times a sound.
 *
 * **It takes nothing from the screen under it,** as the greeting takes nothing: it is a drawing
 * and no more. No press, no Back and no screen reader ever meets it, the screen is not dimmed,
 * and every button under the mascot and his smoke works while he goes across.
 *
 * **[onDone] is called once, however the run ends:** when the last of him and of his smoke has
 * left the screen; at once, with nothing drawn, while the phone's animations are switched off
 * or when a picture cannot be read; and when MilO is put away in the middle of it. Leaving the
 * composition ends it too.
 *
 * TODO(debt): each animated picture's first frame stands for about 65 ms where the film gives it
 *  42, as the party's does, and with a frame of 42 ms for the film's 41.7 the run is two to three
 *  frames behind the film by its end. The seam's length is [START_AHEAD_MS], which the party
 *  shares. See docs/FINDINGS_LOG.md, 2026-10-11.
 *
 * Whether there is a run, and when, is not decided here; what its pictures hold, where he is at
 * each moment and how long each part lasts is worked out in `MascotRunPicture.kt`.
 *
 * @param onDone told whether the run was played to its end, or cut short.
 * @param barHeight the room the bottom bar takes at the foot of the screen, with everything
 * under it: what a `Scaffold` reports as its content's bottom padding.
 * @param onArrived called once, as he gets to the spot: the moment for the wheels' whirr.
 * @param onBraked called once, as his brake lights come on: the moment for the squeal.
 * @param onUnreadable told why, if the pictures could not be read.
 */
@Composable
fun MascotRun(
    onDone: (whole: Boolean) -> Unit,
    barHeight: Dp,
    modifier: Modifier = Modifier,
    onArrived: () -> Unit = {},
    onBraked: () -> Unit = {},
    onUnreadable: (IOException) -> Unit = {},
) {
    var stage by remember { mutableStateOf(RunStage.READING) }
    // The run outlives a recomposition; these keep it calling the newest functions.
    val newestOnDone by rememberUpdatedState(onDone)
    val newestOnArrived by rememberUpdatedState(onArrived)
    val newestOnBraked by rememberUpdatedState(onBraked)
    val newestOnUnreadable by rememberUpdatedState(onUnreadable)
    val course =
        remember {
            RunCourse(
                onStage = {
                    stage = it
                    if (it == RunStage.ON_THE_SPOT) newestOnArrived()
                    if (it == RunStage.SLIDING) newestOnBraked()
                },
                onDone = { whole -> newestOnDone(whole) },
            )
        }

    // Put away in the middle of a run, MilO does not carry on with it when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { course.on(RunEvent.CUT_SHORT) }

    // Leaving the composition is what stops the run (the effect in RunOnScreen).
    if (stage != RunStage.OVER) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            RunOnScreen(course, barHeight, braked = { stage == RunStage.SLIDING }) { why ->
                newestOnUnreadable(why)
            }
        }
    }
}

/** Plays one run through, telling [course] of each step, and draws the mascot. */
@Composable
private fun BoxWithConstraintsScope.RunOnScreen(
    course: RunCourse,
    barHeight: Dp,
    braked: () -> Boolean,
    onUnreadable: (IOException) -> Unit,
) {
    val resources = LocalResources.current
    // Read once: a window that is resized under the run does not start it again.
    val way = remember { RunWay(maxWidth.value) }
    var pictures by remember { mutableStateOf<MascotRunPictures?>(null) }
    // What is drawn of the run in, and where. His step and his place are one value, and both
    // are read from it while drawing: they cannot come apart. Without a frame of the sheet,
    // the animated picture whose turn it is is drawn.
    var pose by remember { mutableStateOf(way.coming.poseIn(0)) }
    // How many pictures have been started. Read while drawing only, so that starting one draws
    // again: Android begins to play a picture when it next draws it, and not before.
    var started by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var toClose: MascotRunPictures? = null
        try {
            // With the phone's animations switched off there is no run.
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
            val read =
                try {
                    readMascotRunPictures(resources)
                } catch (unreadable: IOException) {
                    onUnreadable(unreadable)
                    return@LaunchedEffect
                }
            toClose = read
            pictures = read
            course.on(RunEvent.PICTURES_READ)
            // The run in, and the frame he gets to the spot in, on one clock. That frame is the
            // first picture's first, which the app shows for its time before Android plays the
            // picture (START_AHEAD_MS says why).
            val spotOver = CompletableDeferred<Unit>()
            everyFrame { since ->
                val frame = way.coming.frameAt(since)
                pose = way.coming.poseIn(frame)
                // Still before the screen is drawn: the drawing that no longer shows the sheet
                // is the one that shows him at the spot. The whirr is asked for in that moment.
                if (frame == way.coming.frames) course.on(RunEvent.ARRIVED)
                val still = since < way.arrivesAtMillis + MascotPicture.FRAME_MS - START_AHEAD_MS
                if (!still) {
                    read.onSpot.playOnce { spotOver.complete(Unit) }
                    started++
                }
                still
            }
            withTimeoutOrNull(RunPicture.SPOT_MS + PICTURE_PATIENCE_MS) { spotOver.await() }
            // When the second picture took the first one's place, on this clock; null until then.
            var brakedAt: Long? = null
            var slidePlays = false
            everyFrame { since ->
                val at = brakedAt
                when {
                    // The first picture's last frame is on the screen and has not had its time
                    // yet: Android says so when it draws that frame, not when its time is up.
                    at == null && since < MascotPicture.FRAME_MS - START_AHEAD_MS -> true

                    // Still before the screen is drawn: the drawing that no longer shows the
                    // first picture is the one that shows the second one's first frame, the
                    // brake lights lit. The squeal is asked for in the same moment.
                    at == null -> {
                        course.on(RunEvent.BRAKED)
                        brakedAt = since
                        true
                    }

                    else -> {
                        if (!slidePlays && since - at >= MascotPicture.FRAME_MS - START_AHEAD_MS) {
                            slidePlays = true
                            read.slide.playOnce {}
                            started++
                        }
                        // Until the last of him and of his smoke has left this screen, which
                        // on a narrow one is before the picture has played through.
                        since - at < way.slideMillis
                    }
                }
            }
            course.on(RunEvent.GONE)
        } finally {
            // Also when the run is cut short, leaves the screen in the middle, or never began.
            toClose?.close()
            course.on(RunEvent.CUT_SHORT)
        }
    }

    // Nothing is drawn while the pictures are being read.
    val shown = pictures
    if (shown != null) {
        val shadow = MaterialTheme.colorScheme.scrim.copy(alpha = SHADOW_DARKEST)
        // The greeting's line: the bar's tile begins rowGap below the top of the room the bar
        // takes (MiloNavigationBar), and the mascot's soles are a small step above the tile.
        val spacing = MiloTheme.spacing
        val solesAboveFoot = barHeight - spacing.rowGap + spacing.extraSmall
        val sheet = RunPicture.SHEET
        // The pictures that stand still are wider than a phone's screen, and a picture is laid
        // out no wider than the room it is given unless it is told otherwise: left alone, it
        // was drawn squeezed to the screen's width (seen on an emulator, 2026-10-11). Its left
        // and lower edges stay where the lines above them put them.
        val asLargeAsItIs =
            Modifier.wrapContentSize(align = AbsoluteAlignment.BottomLeft, unbounded = true)
        Box(
            modifier =
                Modifier
                    .align(AbsoluteAlignment.BottomLeft)
                    .offset(y = sheet.soles.dp - solesAboveFoot)
                    // Read while drawing, so a step moves the mascot and nothing is composed
                    // or laid out again.
                    .graphicsLayer { translationX = (pose.axis - sheet.axis).dp.toPx() }
                    // The animated pictures have the shadow in them: he moves in them.
                    .drawBehind {
                        if (pose.sheetFrame != null) {
                            groundShadow(shadow, sheet.axis.dp, sheet.soles.dp)
                        }
                    },
        ) {
            MascotSheet(shown.sheet, sheet, frame = { pose.sheetFrame }, modifier = asLargeAsItIs)
        }
        MascotPlayed(
            // "started" is read here for one reason: a picture that is started draws again.
            picture = {
                when {
                    pose.sheetFrame != null -> null
                    started >= 0 && braked() -> shown.slide
                    else -> shown.onSpot
                }
            },
            width = RunPicture.WIDTH.dp,
            height = RunPicture.HEIGHT.dp,
            modifier =
                Modifier
                    .align(AbsoluteAlignment.BottomLeft)
                    .absoluteOffset(
                        x = way.playedLeft.dp,
                        y = RunPicture.SOLES.dp - solesAboveFoot,
                    ).then(asLargeAsItIs),
        )
    }
}
