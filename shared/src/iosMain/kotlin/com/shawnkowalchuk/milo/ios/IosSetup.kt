package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.compareDates
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.usesTwelveHourClock

// The iPhone's Setup screen (phase 4d, second part; ADR-015): every requirement with its state,
// one sentence, and a button that takes the user to fix it, as Android's Setup screen
// (SetupRules.kt in the app). What is worked out here is only which row says what; Swift reads
// the facts from iOS and carries out the buttons.

/** What iOS lets Dually do with the location. */
enum class LocationAccess { NOT_ASKED, WHILE_USING, ALWAYS, OFF }

/** How the iPhone connects to the vehicle, as the user chose: what the automations watch. */
enum class VehicleLink { CARPLAY, BLUETOOTH }

enum class SetupState {
    READY,

    /** Something for the user to do. */
    NEEDS_YOU,

    /** Waits for a row above it. */
    WAITING,
}

/** What a row's button does. Swift carries it out. */
enum class SetupAction { ASK_LOCATION, ASK_ALWAYS, OPEN_SETTINGS, OPEN_SHORTCUTS, CHOOSE_VEHICLE }

/** One requirement. [steps] are numbered instructions, empty for most rows. */
data class SetupRow(
    val title: String,
    val state: SetupState,
    val sentence: String,
    val steps: List<String>,
    val button: String?,
    val action: SetupAction?,
)

/** The screen: how many rows are ready, and the rows in order. */
data class SetupScreen(val ready: Int, val rows: List<SetupRow>) {
    val total: Int get() = rows.size

    /** Every row ready: trips can start and end with the vehicle, with Dually closed. */
    val complete: Boolean get() = ready == total
}

/**
 * The Setup rows for what iOS reports.
 *
 * @param alwaysAsked whether Dually has already asked iOS for "Always". iOS shows that question
 *   once; after it, only the Settings app can change the answer.
 * @param connectHeardAtMs when the automation last ran Start a trip, or null if it never has:
 *   the check that the automation works. [disconnectHeardAtMs] is the same for End a trip.
 */
fun setupScreen(
    location: LocationAccess,
    precise: Boolean,
    alwaysAsked: Boolean,
    vehicle: VehicleLink?,
    connectHeardAtMs: Long?,
    disconnectHeardAtMs: Long?,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
): SetupScreen {
    fun heardText(atMs: Long): String {
        val time = formatTimeOfDay(atMs, zone, locale, !usesTwelveHourClock(locale))
        val day = localDateOf(atMs, zone)
        val today = localDateOf(nowMs, zone)
        val date = if (compareDates(day, today) == 0) "today" else formatShortDay(day, locale)
        return "Working: it last ran $date at $time."
    }
    val rows =
        listOf(
            locationRow(location, alwaysAsked),
            preciseRow(location, precise),
            vehicleRow(vehicle),
            automationRow(vehicle, connects = true, connectHeardAtMs?.let(::heardText)),
            automationRow(vehicle, connects = false, disconnectHeardAtMs?.let(::heardText)),
        )
    return SetupScreen(rows.count { it.state == SetupState.READY }, rows)
}

private fun locationRow(location: LocationAccess, alwaysAsked: Boolean): SetupRow {
    val title = "Location: Always"
    return when {
        location == LocationAccess.ALWAYS ->
            row(title, SetupState.READY, "Set to Always: a trip can start with Dually closed.")

        location == LocationAccess.WHILE_USING && !alwaysAsked ->
            row(
                title,
                SetupState.NEEDS_YOU,
                "Set to While Using the App. For a trip to start by itself with Dually closed, " +
                    "it needs Always.",
                "Change to Always",
                SetupAction.ASK_ALWAYS,
            )

        location == LocationAccess.WHILE_USING ->
            row(
                title,
                SetupState.NEEDS_YOU,
                "Set to While Using the App. For a trip to start by itself with Dually closed, " +
                    "change it in Settings: Location, then Always.",
                "Open Settings",
                SetupAction.OPEN_SETTINGS,
            )

        location == LocationAccess.NOT_ASKED ->
            row(
                title,
                SetupState.NEEDS_YOU,
                "Dually measures each trip with the GPS, so it needs your location.",
                "Allow location",
                SetupAction.ASK_LOCATION,
            )

        else ->
            row(
                title,
                SetupState.NEEDS_YOU,
                "Location is off for Dually, so no trip can be recorded. Turn it on in " +
                    "Settings: Location, then Always.",
                "Open Settings",
                SetupAction.OPEN_SETTINGS,
            )
    }
}

private fun preciseRow(location: LocationAccess, precise: Boolean): SetupRow {
    val title = "Precise location"
    return when {
        location == LocationAccess.NOT_ASKED || location == LocationAccess.OFF ->
            row(title, SetupState.WAITING, "Comes with the location above.")

        precise -> row(title, SetupState.READY, "On: each trip is measured from exact positions.")

        else ->
            row(
                title,
                SetupState.NEEDS_YOU,
                "Off: iOS gives Dually only rough positions, and distances would be wrong. Turn " +
                    "on Precise Location in Settings: Location.",
                "Open Settings",
                SetupAction.OPEN_SETTINGS,
            )
    }
}

private fun vehicleRow(vehicle: VehicleLink?): SetupRow {
    val title = "Your vehicle"
    return when (vehicle) {
        null ->
            row(
                title,
                SetupState.NEEDS_YOU,
                "How does your iPhone connect to it? A trip starts when it connects and ends " +
                    "when it disconnects.",
                null,
                SetupAction.CHOOSE_VEHICLE,
            )

        VehicleLink.CARPLAY ->
            row(
                title,
                SetupState.READY,
                "CarPlay: a trip starts when CarPlay connects and ends when it disconnects.",
                "Use Bluetooth instead",
                SetupAction.CHOOSE_VEHICLE,
            )

        VehicleLink.BLUETOOTH ->
            row(
                title,
                SetupState.READY,
                "Bluetooth: a trip starts when your iPhone connects to the vehicle's Bluetooth " +
                    "and ends when it disconnects.",
                "Use CarPlay instead",
                SetupAction.CHOOSE_VEHICLE,
            )
    }
}

/**
 * The automation that runs Start a trip ([connects]) or End a trip. It is ready once it has run:
 * Dually cannot see the user's automations, only that its action was run.
 */
private fun automationRow(vehicle: VehicleLink?, connects: Boolean, heard: String?): SetupRow {
    val action = if (connects) "Start a trip" else "End a trip"
    val title = "Automation: $action"
    if (vehicle == null) return row(title, SetupState.WAITING, "Choose your vehicle above first.")
    val test =
        when {
            vehicle == VehicleLink.CARPLAY && connects -> "start CarPlay in the vehicle"
            vehicle == VehicleLink.CARPLAY -> "leave CarPlay (turn the vehicle off)"
            connects -> "turn the vehicle on, so your iPhone connects to it"
            else -> "turn the vehicle off, so your iPhone disconnects"
        }
    val sentence =
        heard ?: "Make it in the Shortcuts app with these steps. To test it, $test: this row " +
            "turns ready when it runs."
    val state = if (heard != null) SetupState.READY else SetupState.NEEDS_YOU
    return SetupRow(
        title,
        state,
        sentence,
        automationSteps(vehicle, connects, action),
        "Open Shortcuts",
        SetupAction.OPEN_SHORTCUTS,
    )
}

private fun automationSteps(vehicle: VehicleLink, connects: Boolean, action: String): List<String> {
    val trigger =
        when (vehicle) {
            VehicleLink.CARPLAY ->
                listOf(
                    "Search for CarPlay and tap it.",
                    "Choose ${if (connects) "Connects" else "Disconnects"} only, then Run " +
                        "Immediately, then Next.",
                )

            VehicleLink.BLUETOOTH ->
                listOf(
                    "Search for Bluetooth and tap it.",
                    "Tap Choose beside Device and pick your vehicle. Choose " +
                        "${if (connects) "Is Connected" else "Is Disconnected"}, then Run " +
                        "Immediately, then Next.",
                )
        }
    return listOf("In Shortcuts, tap Automation, then New Automation (or +).") +
        trigger +
        listOf(
            "Tap New Blank Automation, then Add Action.",
            "Search for \"$action\", tap Dually's $action, then Done.",
        )
}

private fun row(
    title: String,
    state: SetupState,
    sentence: String,
    button: String? = null,
    action: SetupAction? = null,
) = SetupRow(title, state, sentence, emptyList(), button, action)
