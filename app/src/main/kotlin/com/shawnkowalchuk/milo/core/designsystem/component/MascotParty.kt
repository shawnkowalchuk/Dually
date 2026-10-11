package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * MilO's one-year party (Shawn, 2026-10-10: "i want it to fade in with the mascot in the center
 * with a birthday cap. i want hime to put his hand behind his back and pull out a party popper
 * and pull it and shoot confeti above his head"). The mascot fades in in the middle of the
 * screen in his party cap, feels about behind his back, brings out a party popper, holds it up,
 * aims it and pulls its string; confetti goes up past his face and comes down round him, and he
 * fades away with it still falling. Five and a half seconds, as the film he was shown.
 *
 * **He stands still while he fades in,** for half a second: that is the first picture's first
 * frame, which the app shows before it lets Android play the picture ([START_AHEAD_MS] says
 * why the app and not the picture).
 *
 * It is two animated pictures, the confetti drawn into them, which Android plays one after the
 * other (ADR-007). **The second begins with the frame the popper goes off on, and the app
 * starts it,** so the app knows the moment of the pop and [onPop] is called in the same drawing
 * of the screen that first shows it. The pictures are read only now, when a party plays.
 *
 * **It takes nothing from the screen under it,** as the greeting takes nothing: it is a drawing
 * and no more. No press, no Back and no screen reader ever meets it, the screen is not dimmed,
 * and every button under the mascot and his confetti works while it plays: a trip has just
 * started, and its End button is one of them.
 *
 * **[onDone] is called once, however the party ends:** when he has faded away; at once, with
 * nothing drawn, while the phone's animations are switched off or when a picture cannot be
 * read; and when MilO is put away in the middle of it, which is also how a turn of the phone
 * ends it. Leaving the composition ends it too.
 *
 * Whether there is a party, and when, is not decided here (`feature/party`); what its pictures
 * hold, where it stands and how strongly it is drawn is worked out in `MascotPartyPicture.kt`.
 *
 * @param onDone told whether the popper had gone off when the party ended.
 * @param onShown called once, when the mascot is about to be drawn for the first time. A party
 * that ends before that has shown nothing.
 * @param onPop called once, as the popper goes off: the moment for its sound.
 * @param onUnreadable told why, if the pictures could not be read.
 */
@Composable
fun MascotParty(
    onDone: (popped: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onShown: () -> Unit = {},
    onPop: () -> Unit = {},
    onUnreadable: (IOException) -> Unit = {},
) {
    var stage by remember { mutableStateOf(PartyStage.READING) }
    // The party outlives a recomposition; these keep it calling the newest functions.
    val newestOnDone by rememberUpdatedState(onDone)
    val newestOnShown by rememberUpdatedState(onShown)
    val newestOnPop by rememberUpdatedState(onPop)
    val newestOnUnreadable by rememberUpdatedState(onUnreadable)
    val course =
        remember {
            PartyCourse(
                onStage = {
                    stage = it
                    if (it == PartyStage.BEFORE_THE_POP) newestOnShown()
                    if (it == PartyStage.POPPED) newestOnPop()
                },
                onDone = { popped -> newestOnDone(popped) },
            )
        }

    // Put away in the middle of a party, MilO does not carry on with it when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { course.on(PartyEvent.CUT_SHORT) }

    // Leaving the composition is what stops the party (the effect in PartyOnScreen).
    if (stage != PartyStage.OVER) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            PartyOnScreen(course, popped = { stage == PartyStage.POPPED }) { why ->
                newestOnUnreadable(why)
            }
        }
    }
}

/** Plays one party through, telling [course] of each step, and draws the mascot. */
@Composable
private fun BoxWithConstraintsScope.PartyOnScreen(
    course: PartyCourse,
    popped: () -> Boolean,
    onUnreadable: (IOException) -> Unit,
) {
    val resources = LocalResources.current
    // Read once: a window that is resized under the party does not start it again.
    val place = remember { MascotPartyPlace(maxWidth.value, maxHeight.value) }
    var pictures by remember { mutableStateOf<MascotPartyPictures?>(null) }
    // How strongly he is drawn, of 1. Read while drawing only.
    var strength by remember { mutableFloatStateOf(0f) }
    // How many pictures have been started. Read while drawing only, so that starting one draws
    // again: Android begins to play a picture when it next draws it, and not before. A picture
    // that is started while nothing else on the screen changes would otherwise wait, on its
    // first frame, for whatever next draws the screen.
    var started by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var toClose: MascotPartyPictures? = null
        try {
            // With the phone's animations switched off there is no party.
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
            val read =
                try {
                    readMascotPartyPictures(resources)
                } catch (unreadable: IOException) {
                    onUnreadable(unreadable)
                    return@LaunchedEffect
                }
            toClose = read
            pictures = read
            course.on(PartyEvent.PICTURES_READ)
            // He stands still while he is faded in: the first picture's first frame, which a
            // picture shows before it plays. Then Android plays it.
            val popperOver = CompletableDeferred<Unit>()
            everyFrame { since ->
                strength = MascotPartyPicture.fadeIn(since)
                val still = since < MascotPartyPicture.FIRST_FRAME_MS - START_AHEAD_MS
                if (!still) {
                    read.popper.playOnce { popperOver.complete(Unit) }
                    started++
                }
                still
            }
            withTimeoutOrNull(MascotPartyPicture.POPPER_MS + PICTURE_PATIENCE_MS) {
                popperOver.await()
            }
            // When the second picture took the first one's place, on this clock; null until then.
            var poppedAt: Long? = null
            var popPlays = false
            everyFrame { since ->
                val at = poppedAt
                when {
                    // The first picture's last frame is on the screen and has not had its time
                    // yet: Android says so when it draws that frame, not when the frame's time
                    // is up. Some of that time has gone by before this clock began.
                    at == null && since < MascotPicture.FRAME_MS - START_AHEAD_MS -> true

                    // Still before the screen is drawn: the drawing that no longer shows the
                    // first picture is the one that shows the second one's first frame, the
                    // pop. The sound is asked for in the same moment.
                    at == null -> {
                        course.on(PartyEvent.POP)
                        poppedAt = since
                        true
                    }

                    else -> {
                        if (!popPlays && since - at >= MascotPicture.FRAME_MS - START_AHEAD_MS) {
                            popPlays = true
                            read.pop.playOnce {}
                            started++
                        }
                        strength = MascotPartyPicture.fadeOut(since - at)
                        since - at < MascotPartyPicture.POP_MS
                    }
                }
            }
            course.on(PartyEvent.GONE)
        } finally {
            // Also when the party is cut short, leaves the screen in the middle, or never began.
            toClose?.close()
            course.on(PartyEvent.CUT_SHORT)
        }
    }

    // Nothing is drawn while the pictures are being read.
    val shown = pictures
    if (shown != null) {
        val shadow = MaterialTheme.colorScheme.scrim.copy(alpha = SHADOW_DARKEST)
        Box(
            modifier =
                Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset(x = place.left.dp, y = place.top.dp)
                    // Read while drawing, so a step of the fade draws again and nothing is
                    // composed or laid out for it.
                    .graphicsLayer { alpha = strength }
                    .drawBehind {
                        val times = place.tall / MascotPicture.TALL
                        groundShadow(shadow, place.axis.dp, place.soles.dp, times)
                    },
        ) {
            MascotPlayed(
                // "started" is read here for one reason: a picture that is started draws again.
                picture = { if (started >= 0 && popped()) shown.pop else shown.popper },
                width = place.width.dp,
                height = place.height.dp,
            )
        }
    }
}

/**
 * Calls [onFrame] before every drawing of the screen with the milliseconds since the first of
 * them, until it answers false. What [onFrame] does is drawn in that same drawing of the screen.
 *
 * The clock is the screen's frame time, read as it is and not stretched by the phone's "animator
 * duration scale": an animated picture plays at its own pace whatever that is set to.
 */
private suspend fun everyFrame(onFrame: (sinceMillis: Long) -> Boolean) {
    val began = withFrameNanos { it }
    do {
        val goesOn = withFrameNanos { now -> onFrame((now - began) / NANOS_IN_MILLI) }
    } while (goesOn)
}

private const val NANOS_IN_MILLI = 1_000_000L

/**
 * How long before a picture's first frame has had its time the app starts the picture. **Android
 * does not keep the first frame of an animated picture on the screen for the time the file gives
 * it:** it shows the second as soon as that is ready, a drawing of the screen after the picture
 * was started (seen on an emulator on 2026-10-10: a first frame of 546 ms was gone after about a
 * tenth of a second, one of 42 ms after 17). So each of the party's pictures is shown for its
 * first frame's time by the app, not yet playing, and started when all but this is up.
 */
private const val START_AHEAD_MS = MascotPicture.FRAME_MS / 2
