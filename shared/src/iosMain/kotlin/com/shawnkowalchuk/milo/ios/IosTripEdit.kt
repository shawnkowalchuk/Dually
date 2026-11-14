package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.addDays
import com.shawnkowalchuk.milo.core.time.dateOf
import com.shawnkowalchuk.milo.core.time.timeOf
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.formatTimeSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.recordedValues
import com.shawnkowalchuk.milo.feature.tripedit.EditSession
import com.shawnkowalchuk.milo.feature.tripedit.FormCheck
import com.shawnkowalchuk.milo.feature.tripedit.FormPart
import com.shawnkowalchuk.milo.feature.tripedit.FormProblem
import com.shawnkowalchuk.milo.feature.tripedit.SaveResult
import com.shawnkowalchuk.milo.feature.tripedit.TripEditing
import com.shawnkowalchuk.milo.feature.tripedit.TripForm
import com.shawnkowalchuk.milo.feature.tripedit.englishFormText
import com.shawnkowalchuk.milo.feature.tripedit.englishKindWords
import com.shawnkowalchuk.milo.feature.tripedit.englishProblemWords
import com.shawnkowalchuk.milo.feature.tripedit.formProblems
import com.shawnkowalchuk.milo.feature.tripedit.holdsUnsavedWork
import com.shawnkowalchuk.milo.feature.tripedit.kindShown
import kotlinx.datetime.number

/**
 * The edit screen's door on the iPhone (2026-11-13): a finished trip's times, addresses, distance
 * and Business or Personal changed by hand, and a trip Dually missed typed in. The form, its
 * checks, the saves and their lines in the event log are Android's own (TripForm, formProblems,
 * TripEditing and kindShown, shared since 2026-11-13); this turns what the iPhone's pickers give
 * into that form and its answers into English words.
 */
class IosTripEdit internal constructor(
    private val editing: TripEditing,
    private val unit: () -> DistanceUnit,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
    private val clock: () -> Long,
) {
    /** The form for trip [tripId], or null if it cannot be edited: not a finished trip (any more). */
    suspend fun open(tripId: Long): TripEditor? = editing.findEditable(tripId)?.let { begin(it) }

    /** The empty form for a trip Dually missed. */
    suspend fun blank(): TripEditor = begin(stored = null)

    private suspend fun begin(stored: Trip?): TripEditor {
        // The schedule sorts a trip whose start is typed or changed; the unit is the settings
        // file's, as on Android, and stays the form's for as long as it is open.
        val fromSettings = editing.formSettings()
        val session =
            EditSession(
                stored = stored,
                schedule = fromSettings?.schedule,
                zone = zone(),
                openedAtMs = clock(),
                unit = fromSettings?.unit ?: unit(),
            )
        return TripEditor(editing, session, locale())
    }
}

/**
 * One open form: what it opened with, and what it was last checked against. The iPhone's sheet
 * holds it for as long as it is up, as Android's screen holds its session.
 */
class TripEditor internal constructor(
    private val editing: TripEditing,
    private val session: EditSession,
    private val locale: Locale,
) {
    private val opened: TripForm = session.openedForm()
    private val stored: Trip? = session.stored
    private val unit: DistanceUnit = session.unit

    /** What the last press on Save checked the form against, or null before the first. */
    private var check: FormCheck? = null

    /** What the form shows when it opens. */
    val text: EditFormText = openedText()

    /** Business or Personal as a save of [fields] would store it, and who chose it. */
    fun kind(fields: EditFields): KindText {
        val (category, source) = kindShown(formOf(fields), session)
        return KindText(category?.name, englishKindWords(source))
    }

    /**
     * What is wrong with [fields], against what the last press on Save checked: kept up to date
     * while the form is put right, as on Android. Nothing before the first press.
     */
    fun problems(fields: EditFields): ProblemsText {
        val against = check ?: return ProblemsText(emptyList(), null)
        return problemsText(formProblems(formOf(fields), stored, against, session.zone))
    }

    /** "The end time is on Tuesday, November 3, 2026.", while [fields] end on a later day. */
    fun endsOn(fields: EditFields): String? {
        val form = formOf(fields)
        if (form.endDayOffset <= 0) return null
        return formatText(
            locale,
            englishFormText("trip_edit_ends_on"),
            formatDay(form.date.addDays(form.endDayOffset), locale),
        )
    }

    /** Whether [fields] hold something picked or typed that leaving now would lose. */
    fun unsaved(fields: EditFields): Boolean = formOf(fields).holdsUnsavedWork(opened, stored)

    /** Saves [fields]: over the trip the form was opened for, or as a trip that was missed. */
    suspend fun save(fields: EditFields): EditOutcome {
        val form = formOf(fields)
        val result =
            if (stored == null) {
                editing.add(form, session.schedule, session.zone)
            } else {
                editing.save(stored, form, session.schedule, session.zone)
            }
        return when (result) {
            SaveResult.Stored -> EditOutcome(stored = true, ProblemsText(emptyList(), null), null)

            is SaveResult.Invalid -> {
                check = result.check
                EditOutcome(stored = false, problemsText(result.problems), null)
            }

            SaveResult.Failed ->
                EditOutcome(
                    stored = false,
                    ProblemsText(emptyList(), null),
                    englishFormText("trip_edit_save_failed"),
                )
        }
    }

    /** Puts back what Dually recorded of the trip. False if it could not be: the log says why. */
    suspend fun putBack(): Boolean {
        val trip = stored ?: return false
        return editing.restore(trip.id, session.schedule, session.zone)
    }

    private fun formOf(fields: EditFields): TripForm {
        val nextDay = fields.nextDay
        return TripForm(
            date = dateOf(fields.year, fields.month, fields.day),
            start = timeOfMinute(fields.startMinute),
            end = timeOfMinute(fields.endMinute),
            // A stored trip that ends two days after it starts keeps that while the switch is
            // left as it opened, as Android's switch leaves it.
            endDayOffset =
                when {
                    nextDay == (opened.endDayOffset > 0) -> opened.endDayOffset
                    nextDay -> 1
                    else -> 0
                },
            // A field that still shows what it opened with is untouched, as on Android: it
            // stands for what is stored, exact to the metre and to the letter.
            from = fields.from.takeIf { it != text.from },
            to = fields.to.takeIf { it != text.to },
            kilometres = fields.distance.takeIf { it != text.distance },
            chosenCategory = fields.chosenCategory?.let(TripCategory::valueOf),
            unit = unit,
        )
    }

    private fun problemsText(problems: List<FormProblem>): ProblemsText = ProblemsText(
        times =
            problems
                .filter { it.part == FormPart.TIMES }
                .map { englishProblemWords(it, unit, locale) },
        distance =
            problems
                .firstOrNull { it.part == FormPart.DISTANCE }
                ?.let { englishProblemWords(it, unit, locale) },
    )

    private fun openedText(): EditFormText {
        val zone = session.zone
        val today = localDateOf(session.openedAtMs, zone)
        val shortUnit = unitWord(unit)
        val recorded = stored?.recordedValues
        val twentyFourHour = !usesTwelveHourClock(locale)
        val km = recorded?.let { "${formatDistance(it.distanceMetres, unit, locale)} $shortUnit" }
        val recordedLine =
            recorded?.let {
                val day = localDateOf(it.startedAtMs, zone)
                val (start, end) =
                    formatTimeSpan(it.startedAtMs, it.endedAtMs, zone, locale, twentyFourHour)
                val times = "$start – $end"
                if (day == opened.date) {
                    formatText(locale, englishFormText("trip_edit_recorded_line"), times, km)
                } else {
                    formatText(
                        locale,
                        englishFormText("trip_edit_recorded_line_with_day"),
                        formatShortDay(day, locale),
                        times,
                        km,
                    )
                }
            }
        val restoreText =
            recorded?.let {
                val day = localDateOf(it.startedAtMs, zone)
                val times =
                    formatTimeOfDay(it.startedAtMs, zone, locale, twentyFourHour) + " – " +
                        formatTimeOfDay(it.endedAtMs, zone, locale, twentyFourHour)
                val trip =
                    formatText(
                        locale,
                        englishFormText("trip_edit_recorded_trip"),
                        formatDay(day, locale),
                        times,
                        km,
                    )
                formatText(locale, englishFormText("trip_edit_restore_text"), trip)
            }
        return EditFormText(
            adding = stored == null,
            title = englishFormText(if (stored == null) "trip_add_title" else "trip_edit_title"),
            intro =
                when {
                    stored == null -> englishFormText("trip_add_intro")
                    stored.addedByHand -> englishFormText("trip_edit_added_intro")
                    else -> null
                },
            saveWords = englishFormText(if (stored == null) "trip_add_save" else "trip_edit_save"),
            year = opened.date.year,
            month = opened.date.month.number,
            day = opened.date.day,
            latestYear = today.year,
            latestMonth = today.month.number,
            latestDay = today.day,
            startMinute = opened.start?.let { it.hour * MINUTES_PER_HOUR + it.minute } ?: NO_TIME,
            endMinute = opened.end?.let { it.hour * MINUTES_PER_HOUR + it.minute } ?: NO_TIME,
            nextDay = opened.endDayOffset > 0,
            from = stored?.startAddress.orEmpty(),
            to = stored?.endAddress.orEmpty(),
            distance =
                stored
                    ?.distanceMetres
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { formatDistance(it, unit, locale) }
                    .orEmpty(),
            distanceTitle =
                formatText(locale, englishFormText("trip_edit_distance_label"), shortUnit),
            recorded = recordedLine,
            restoreQuestion = restoreText,
        )
    }
}

private const val MINUTES_PER_HOUR = 60

/** The minute that stands for "no time chosen yet", for Swift, which has no Int? from Kotlin. */
const val NO_TIME: Int = -1

private fun timeOfMinute(minute: Int) =
    if (minute < 0) null else timeOf(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)

/**
 * What the sheet's pickers and fields hold. A time is minutes from midnight, or [NO_TIME].
 * [chosenCategory] is "BUSINESS" or "PERSONAL" once one of the two is pressed, else null.
 */
data class EditFields(
    val year: Int,
    val month: Int,
    val day: Int,
    val startMinute: Int,
    val endMinute: Int,
    val nextDay: Boolean,
    val from: String,
    val to: String,
    val distance: String,
    val chosenCategory: String?,
)

/**
 * What the form shows when it opens. [latestYear], [latestMonth] and [latestDay] are today: the
 * calendar offers no later day. [recorded] is what Dually recorded of a trip edited since, with
 * [restoreQuestion] the question asked before it is put back; both null otherwise.
 */
data class EditFormText(
    val adding: Boolean,
    val title: String,
    val intro: String?,
    val saveWords: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val latestYear: Int,
    val latestMonth: Int,
    val latestDay: Int,
    val startMinute: Int,
    val endMinute: Int,
    val nextDay: Boolean,
    val from: String,
    val to: String,
    val distance: String,
    val distanceTitle: String,
    val recorded: String?,
    val restoreQuestion: String?,
)

/** Business or Personal ("BUSINESS", "PERSONAL", or null while none), and who chose it. */
data class KindText(val category: String?, val note: String)

/** What is wrong with the times, said in the tile "When", and with the distance. */
data class ProblemsText(val times: List<String>, val distance: String?)

/**
 * What became of a press on Save: [stored], or not, with what to put right, or [failed] when
 * storage did not take it.
 */
data class EditOutcome(val stored: Boolean, val problems: ProblemsText, val failed: String?)
