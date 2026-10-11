package com.shawnkowalchuk.milo.feature.party

import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(hour: Int, minute: Int = 0, day: Int = 6): Long =
    LocalDateTime.of(2027, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

/** Which party plays over Home, by the facts of one moment. */
class PartyTest {
    private val tripStarted = at(7, 42)

    /** Home in front, the settings read, and a trip that started this morning owed its party. */
    private val owedAndWatching =
        PartyFacts(read = true, owedSinceMs = tripStarted, watching = true)

    private fun PartyFacts.at(nowMs: Long) = settled(nowMs, EDMONTON)

    @Test
    fun `nothing owed and nothing asked for, nothing plays`() {
        val facts = PartyFacts(read = true, watching = true).at(at(9))

        assertNull(facts.showing)
        assertFalse(facts.owedToday)
        assertEquals(false, facts.hasTheScreen)
    }

    @Test
    fun `a party owed for a trip of today plays when Home is in front`() {
        val facts = owedAndWatching.at(at(7, 42))

        assertEquals(PartyShowing.THE_DAY, facts.showing)
        assertEquals(true, facts.hasTheScreen)
    }

    @Test
    fun `it waits while Home is not in front, and plays when it next is that day`() {
        val away = owedAndWatching.copy(watching = false).at(at(7, 42))
        assertNull(away.showing)
        assertTrue(away.owedToday)

        val back = away.copy(watching = true).at(at(17, 5))

        assertEquals(PartyShowing.THE_DAY, back.showing)
    }

    @Test
    fun `after midnight nothing left over plays`() {
        val away = owedAndWatching.copy(watching = false).at(at(22))

        val nextMorning = away.copy(watching = true).at(at(6, day = 7))

        assertNull(nextMorning.showing)
        assertFalse(nextMorning.owedToday)
        assertEquals(false, nextMorning.hasTheScreen)
    }

    @Test
    fun `the party that is playing is not changed when what it was owed for is taken out`() {
        val playing = owedAndWatching.at(at(7, 42))

        // Its mascot is drawn: this start has dealt with that trip, and the settings forget it.
        val shown = playing.copy(shownForMs = tripStarted).at(at(7, 42))
        val forgotten = shown.copy(owedSinceMs = null).at(at(7, 43))

        assertEquals(PartyShowing.THE_DAY, shown.showing)
        assertEquals(PartyShowing.THE_DAY, forgotten.showing)
    }

    @Test
    fun `a party that was shown is not shown again for the same trip`() {
        // The settings still hold it: taking it out failed.
        val over = owedAndWatching.copy(shownForMs = tripStarted, showing = null).at(at(7, 50))

        assertNull(over.showing)
        assertFalse(over.owedToday)
    }

    @Test
    fun `a trip that starts later the same day is owed a party of its own`() {
        val afterTheFirst = owedAndWatching.copy(shownForMs = tripStarted)

        val second = afterTheFirst.copy(owedSinceMs = at(13, 10)).at(at(13, 10))

        assertEquals(PartyShowing.THE_DAY, second.showing)
    }

    @Test
    fun `a party stops when Home stops watching`() {
        val playing = owedAndWatching.at(at(7, 42))

        val left = playing.copy(watching = false).at(at(7, 42))

        assertNull(left.showing)
    }

    @Test
    fun `asked for on demand, it plays on any day once Home is in front`() {
        val asked = PartyFacts(read = true, asked = true)
        assertNull(asked.at(at(9)).showing)
        // The greeting stands back for it already.
        assertEquals(true, asked.at(at(9)).hasTheScreen)

        val onHome = asked.copy(watching = true).at(at(9))

        assertEquals(PartyShowing.ON_DEMAND, onHome.showing)
    }

    @Test
    fun `the day's party comes before one on demand, and is not replaced by it`() {
        val both = owedAndWatching.copy(asked = true).at(at(8))
        assertEquals(PartyShowing.THE_DAY, both.showing)

        // Asked for while the day's party plays: that one plays on.
        val askedDuring = owedAndWatching.at(at(8)).copy(asked = true).at(at(8))
        assertEquals(PartyShowing.THE_DAY, askedDuring.showing)
    }

    @Test
    fun `until the settings are read nobody knows whether a party has the screen`() {
        assertNull(PartyFacts().hasTheScreen)
        assertNull(PartyFacts(watching = true).at(at(9)).hasTheScreen)
        // Read, with nothing owed: the greeting may go ahead.
        assertEquals(false, PartyFacts(read = true).at(at(9)).hasTheScreen)
    }

    @Test
    fun `a party that is owed has the screen before Home is in front, so the greeting waits`() {
        // A fresh start on the day: the settings are read before the activity is in front.
        val opening = PartyFacts(read = true, owedSinceMs = tripStarted).at(at(17))

        assertNull(opening.showing)
        assertEquals(true, opening.hasTheScreen)
    }

    @Test
    fun `Home is drawn once the first start's page has been left, Setup's Done pressed or not`() {
        assertTrue(homeIsDrawn(onTop = true, firstRun = FirstRunStage.DONE))
        // OK was pressed and Setup left by another way than its Done button: an ordinary Home.
        assertTrue(homeIsDrawn(onTop = true, firstRun = FirstRunStage.SETUP))
    }

    @Test
    fun `Home is not drawn under the first start's page, nor while the stage is being read`() {
        // The page that says what MilO does is drawn in place of every screen.
        assertFalse(homeIsDrawn(onTop = true, firstRun = FirstRunStage.INTRO))
        assertFalse(homeIsDrawn(onTop = true, firstRun = null))
    }

    @Test
    fun `Home is not drawn while another screen is on top`() {
        FirstRunStage.entries.forEach { stage ->
            assertFalse(homeIsDrawn(onTop = false, firstRun = stage))
        }
        assertFalse(homeIsDrawn(onTop = false, firstRun = null))
    }
}
