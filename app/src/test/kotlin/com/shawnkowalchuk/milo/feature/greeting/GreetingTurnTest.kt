package com.shawnkowalchuk.milo.feature.greeting

import com.shawnkowalchuk.milo.core.designsystem.component.MascotGreetingKind
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Whose turn it is: the wave, the moonwalk, the wave, and what a phone starts with. */
class GreetingTurnTest {
    @Test
    fun `the wave and the moonwalk take turns, the wave first`() {
        val turns = (0..6).map(::greetingDue)
        assertEquals(
            listOf(
                MascotGreetingKind.WAVE,
                MascotGreetingKind.MOONWALK,
                MascotGreetingKind.WAVE,
                MascotGreetingKind.MOONWALK,
                MascotGreetingKind.WAVE,
                MascotGreetingKind.MOONWALK,
                MascotGreetingKind.WAVE,
            ),
            turns,
        )
        // However many it has been shown, one of them is due.
        assertEquals(MascotGreetingKind.MOONWALK, greetingDue(Int.MAX_VALUE))
    }

    @Test
    fun `a count that is stored is the count`() {
        for (firstRun in FirstRunStage.entries) {
            assertEquals(0, greetingsCounted(stored = 0, firstRun))
            assertEquals(1, greetingsCounted(stored = 1, firstRun))
            assertEquals(37, greetingsCounted(stored = 37, firstRun))
        }
    }

    @Test
    fun `after the update that brings the turns, the next greeting is the moonwalk`() {
        // A phone that had MilO before has no count, and its first start is long over. It has
        // had its wave.
        val shown = greetingsCounted(stored = null, firstRun = FirstRunStage.DONE)
        assertEquals(1, shown)
        assertEquals(MascotGreetingKind.MOONWALK, greetingDue(shown))
        // And then they take turns from there.
        assertEquals(MascotGreetingKind.WAVE, greetingDue(shown + 1))
        assertEquals(MascotGreetingKind.MOONWALK, greetingDue(shown + 2))
    }

    @Test
    fun `on a new phone the first greeting is the wave`() {
        // A new phone has no count either, and is in the middle of its first start.
        for (firstRun in listOf(FirstRunStage.INTRO, FirstRunStage.SETUP)) {
            val shown = greetingsCounted(stored = null, firstRun)
            assertEquals(0, shown)
            assertEquals(MascotGreetingKind.WAVE, greetingDue(shown))
        }
        // Its count is stored at that start, so it is still the wave once the first start is over.
        assertEquals(0, greetingsCounted(stored = 0, firstRun = FirstRunStage.DONE))
    }
}
