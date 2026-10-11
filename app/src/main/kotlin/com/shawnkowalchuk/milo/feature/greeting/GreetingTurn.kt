package com.shawnkowalchuk.milo.feature.greeting

import com.shawnkowalchuk.milo.core.designsystem.component.MascotGreetingKind
import com.shawnkowalchuk.milo.data.settings.FirstRunStage

// Whose turn it is (Shawn, 2026-10-10, of the moonwalk: "yes take turns with the wave"). MilO has
// two greetings and shows one for every fresh start: the wave, the moonwalk, the wave, and so on.
// What is remembered from one start to the next is how many greetings this phone has been shown
// (`greetings_shown` in the settings file); which one is due follows from that number.

/** The greetings in the order they take their turns. A phone that has been shown none starts with the first. */
private val TURNS = listOf(MascotGreetingKind.WAVE, MascotGreetingKind.MOONWALK)

/**
 * What a phone that had MilO before the greetings took turns counts as: it has been shown the
 * wave, the only greeting there was. So the first greeting after the update that brings the
 * moonwalk is the moonwalk, and Shawn sees the new thing at once.
 */
private const val SHOWN_BEFORE_THE_TURNS = 1

/**
 * How many greetings a phone counts as having been shown.
 *
 * - **A count that is stored** is the answer.
 * - **None stored, and the first start is over:** the phone had MilO before the greetings took
 *   turns, and has had its wave.
 * - **None stored, in the middle of the first start:** a new phone, which has been shown
 *   nothing. Its count is stored at that start (`GreetingViewModel`), because at the next one
 *   the first start is over, and it would be taken for a phone of the second kind.
 *
 * @param stored the count in the settings file, or null if none is.
 * @param firstRun how far the first start has come.
 */
fun greetingsCounted(stored: Int?, firstRun: FirstRunStage): Int = when {
    stored != null -> stored
    firstRun == FirstRunStage.DONE -> SHOWN_BEFORE_THE_TURNS
    else -> 0
}

/** The greeting that is due on a phone that has been shown [shown] of them. */
fun greetingDue(shown: Int): MascotGreetingKind = TURNS[shown.mod(TURNS.size)]
