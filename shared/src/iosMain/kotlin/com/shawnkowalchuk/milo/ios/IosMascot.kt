package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.designsystem.component.MascotGreetingKind
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setGreetingsShown
import com.shawnkowalchuk.milo.feature.greeting.greetingDue
import com.shawnkowalchuk.milo.feature.greeting.greetingsCounted

/**
 * The mascot on the iPhone (2026-11-16), by Android's rules (`GreetingViewModel`): whose turn it
 * is at a fresh start, the wave or the moonwalk, counted in the same `greetings_shown` of the
 * settings file, and the Log's line when a picture cannot be read. Where he stands at each
 * moment is the shared course itself (`MascotGreetingKind.wayAcross`), read by Swift.
 *
 * @param clock wall-clock milliseconds, for the event log.
 */
class IosMascot internal constructor(
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) {
    /** How many greetings this iPhone has been shown, as it stood at this start. */
    private var counted: Int? = null

    /**
     * The greeting due at this start, read once for it. A new iPhone's count begins here, in its
     * first start, as on Android; an iPhone that had Dually before the greetings counts as having
     * had its wave, so its first is the moonwalk.
     */
    suspend fun due(): MascotGreetingKind {
        counted?.let { return greetingDue(it) }
        val stored = runCatching { settings.current() }.getOrNull()
        val shown = stored?.let { greetingsCounted(it.greetingsShown, it.firstRunStage) } ?: 0
        counted = shown
        if (stored != null && stored.greetingsShown == null &&
            stored.firstRunStage != FirstRunStage.DONE
        ) {
            store(shown)
        }
        return greetingDue(shown)
    }

    /** The greeting has come on the screen: the next start has the other one. */
    suspend fun shown() {
        val before = counted ?: return
        counted = before + 1
        store(before + 1)
    }

    /** A picture of the mascot could not be read: said in the Log in Android's words. */
    suspend fun unreadable(kind: MascotGreetingKind, why: String) {
        val what =
            when (kind) {
                MascotGreetingKind.WAVE ->
                    "Dually did not greet: a picture of its mascot could not be read"

                MascotGreetingKind.MOONWALK ->
                    "Dually's mascot did not moonwalk: a picture of him could not be read. " +
                        "Dually shows the wave instead"
            }
        eventLog.add(clock(), EventCategory.ERROR, what, why)
    }

    private suspend fun store(shown: Int) {
        try {
            settings.setGreetingsShown(shown)
        } catch (notStored: Exception) {
            val what = "Dually could not store how many greetings it has shown ($shown)"
            eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
        }
    }
}
