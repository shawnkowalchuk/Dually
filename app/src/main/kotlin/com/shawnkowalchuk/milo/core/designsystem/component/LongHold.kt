package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** How long a finger has to rest on something for [onLongHold]: long enough to be meant. */
private const val LONG_HOLD_MS = 2_000L

/**
 * Calls [onHeld] when a finger has rested on this for two seconds without lifting or moving
 * off it. For something that is there to be found by whoever was told of it and must not
 * happen by accident: it is four times as long as Android's own long press, and a finger that
 * scrolls the page is not resting.
 *
 * **It gives no sign of itself.** Nothing is drawn while the finger rests, and a screen reader
 * is told nothing: what it is put on is not a button and does not become one. So it is not for
 * anything a person needs in order to use MilO.
 *
 * A deliberate addition to the design system (2026-10-10), used in one place: the line of the
 * version on this phone, on the What's new screen, which plays MilO's one-year party on demand.
 */
fun Modifier.onLongHold(onHeld: () -> Unit): Modifier = pointerInput(onHeld) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        // Null only when the time ran out with the finger still down: lifting it, or the page
        // taking the gesture for a scroll, ends the wait first.
        val liftedOrMoved = withTimeoutOrNull(LONG_HOLD_MS) { waitForUpOrCancellation() ?: Unit }
        if (liftedOrMoved == null) onHeld()
    }
}
