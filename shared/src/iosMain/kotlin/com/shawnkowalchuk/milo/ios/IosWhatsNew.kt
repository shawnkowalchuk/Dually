package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.data.changelog.ChangeKind
import com.shawnkowalchuk.milo.data.changelog.ChangelogUnreadableException
import com.shawnkowalchuk.milo.data.changelog.parseChangelog
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setFirstRunStage
import com.shawnkowalchuk.milo.data.settings.setWhatsNewSeenVersion
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.feature.whatsnew.isDue
import kotlinx.coroutines.flow.first

/**
 * The iPhone's What's new and its first start (2026-11-16), as Android's: the list of versions
 * built into the app (`ios/Dually/changelog.json`, read with Android's own reader), opened by
 * itself once after an update ([isDue]), and the welcome page, then Setup with its Done button,
 * on a fresh install only ([FirstRunStage]).
 *
 * @param clock wall-clock milliseconds, for the event log.
 */
class IosWhatsNew internal constructor(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val eventLog: EventLogRepository,
    private val locale: () -> Locale,
    private val clock: () -> Long,
) {
    /**
     * The list in [text], newest version first, with the heading of each ("1.0.1 · not
     * released yet · on this phone"), or [WhatsNewText.unreadable] for a list that cannot be
     * read, whose reason goes to the event log, as on Android.
     */
    suspend fun list(text: String, installed: String): WhatsNewText {
        val changelog =
            try {
                parseChangelog(text)
            } catch (unreadable: ChangelogUnreadableException) {
                val what = "The list of changes of Dually $installed cannot be read"
                eventLog.add(clock(), EventCategory.ERROR, what, unreadable.stackTraceToString())
                return WhatsNewText(emptyList(), unreadable = true)
            }
        return WhatsNewText(
            releases =
                changelog.releases.map { release ->
                    val day = release.releasedOn
                    val heading =
                        release.version + " · " +
                            if (day == null) "not released yet" else formatMediumDay(day, locale())
                    ReleaseText(
                        heading = if (release.version ==
                            installed
                        ) {
                            "$heading · on this phone"
                        } else {
                            heading
                        },
                        changes =
                            release.changes.map { change ->
                                ChangeText(
                                    kind = change.kind.name,
                                    tag = tagWord(change.kind),
                                    title = change.title,
                                    body = change.body,
                                )
                            },
                    )
                },
            unreadable = false,
        )
    }

    /** Whether What's new is to open by itself: once after an update, never on a fresh install. */
    suspend fun due(installed: String): Boolean = isDue(settings.current(), installed)

    /** What's new was opened: not due again until the next version. */
    suspend fun shown(installed: String) = store("that What's new was shown for $installed") {
        settings.setWhatsNewSeenVersion(installed)
    }

    /**
     * How far the first start has got: "INTRO" (the welcome page), "SETUP" (Setup with its Done
     * button) or "DONE".
     *
     * An iPhone that had Dually before the welcome page existed (1.0) counts as done, its first
     * start long over: it shows earlier use (a trip or a line in the event log) and has never
     * stored a version as seen, which 1.0 did not do. It is told so here, the first time it is
     * asked, and sees the update's What's new once, as Android's phones did when theirs came.
     * A fresh install stores [installed] as seen the moment the welcome page is shown, so that
     * the lines its own start writes to the log never pass it for such an iPhone: the page
     * shows again at each start until OK is pressed, as on Android.
     */
    suspend fun stage(installed: String): String {
        val now = settings.current()
        if (now.firstRunStage != FirstRunStage.INTRO) return now.firstRunStage.name
        if (now.whatsNewSeenVersion == null && usedBefore()) {
            store("that the first start was over before the welcome page existed") {
                settings.setFirstRunStage(FirstRunStage.DONE)
            }
            return FirstRunStage.DONE.name
        }
        // The welcome page has said what Dually does: this version's list is not shown to it.
        if (now.whatsNewSeenVersion != installed) shown(installed)
        return FirstRunStage.INTRO.name
    }

    /** OK on the welcome page: Setup is next, with its Done button. */
    suspend fun welcomeDone() = store("how far the first start has got (SETUP)") {
        settings.setFirstRunStage(FirstRunStage.SETUP)
    }

    /** Done on Setup: the first start is over. */
    suspend fun setupDone() = store("how far the first start has got (DONE)") {
        settings.setFirstRunStage(FirstRunStage.DONE)
    }

    private suspend fun usedBefore(): Boolean =
        eventLog.observeNewest(1, null).first().isNotEmpty() ||
            trips.observeTripsStartedBetween(0L, Long.MAX_VALUE).first().isNotEmpty()

    // A stage or a version that cannot be stored counts here all the same, as on Android: the
    // failure goes to the event log, and the screen shows again at the next start.
    private suspend fun store(what: String, write: suspend () -> Unit) {
        try {
            write()
        } catch (notStored: Exception) {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "Dually could not store $what",
                notStored.stackTraceToString(),
            )
        }
    }

    private fun tagWord(kind: ChangeKind): String = when (kind) {
        ChangeKind.NEW -> "NEW"
        ChangeKind.IMPROVED -> "IMPROVED"
        ChangeKind.FIXED -> "FIXED"
        ChangeKind.SECURITY -> "SECURITY"
    }
}

/** The What's new screen: every version, newest first, or a list that cannot be read. */
data class WhatsNewText(val releases: List<ReleaseText>, val unreadable: Boolean)

/** One version: "1.0 · Nov 16, 2026 · on this phone", and its changes. */
data class ReleaseText(val heading: String, val changes: List<ChangeText>)

/** One change: its kind ("NEW"), the word on its tag, its title and what it says under it. */
data class ChangeText(val kind: String, val tag: String, val title: String, val body: String?)
