package com.shawnkowalchuk.milo.feature.greeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.designsystem.component.MascotGreetingKind
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setGreetingsShown
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which greeting this start gets, and what the greeting tells the Log (ADR-007).
 *
 * **The turn.** MilO's two greetings take turns (`GreetingTurn.kt`). When it is built, this
 * reads how many greetings the phone has been shown and says which is [due]. **That answer
 * holds for the whole start:** it is read once, so the greeting that is showing is not changed
 * under itself when its own turn is stored. A greeting counts as shown from the moment the
 * mascot is first drawn ([onShown]), also if it is cut short a moment later; one that never drew
 * him (the phone's animations are off, another screen had the moment) leaves the turn as it was.
 *
 * **A settings file that cannot be read** gets the wave, at every start: nothing can be
 * remembered. The first start's ViewModel writes the Log's line about the file.
 *
 * **The pictures.** Each greeting is two pictures the app carries, read by Android. If the
 * moonwalk's cannot be read, the wave plays in its place; if the wave's cannot be read either,
 * that start goes without a greeting. MilO works as ever, the Log says why, and nothing is
 * tried again until MilO is next opened.
 *
 * @param clock wall-clock milliseconds, for the event log.
 */
class GreetingViewModel(
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /** How many greetings the phone counted as shown when this start read it, or null until then. */
    private val counted = MutableStateFlow<Int?>(null)

    /** The greeting this start gets if it gets one, or null while the settings are being read. */
    val due: StateFlow<MascotGreetingKind?> =
        counted
            .map { shown -> shown?.let(::greetingDue) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val stored =
                try {
                    settings.current()
                } catch (_: IOException) {
                    null
                }
            val shown = stored?.let { greetingsCounted(it.greetingsShown, it.firstRunStage) } ?: 0
            counted.value = shown
            // A new phone's count begins here, in its first start (greetingsCounted says why).
            val newPhone =
                stored?.run { greetingsShown == null && firstRunStage != FirstRunStage.DONE }
            if (newPhone == true) store(shown)
        }
    }

    /** The mascot is about to be drawn: this start has had its greeting, and the turn passes on. */
    fun onShown() {
        val before = counted.value ?: return
        viewModelScope.launch { store(before + 1) }
    }

    /** The pictures of [kind] could not be read. */
    fun onUnreadable(kind: MascotGreetingKind, why: IOException) {
        viewModelScope.launch {
            val what =
                when (kind) {
                    MascotGreetingKind.WAVE ->
                        "Dually did not greet: a picture of its mascot could not be read"

                    MascotGreetingKind.MOONWALK ->
                        "Dually's mascot did not moonwalk: a picture of him could not be read. " +
                            "Dually shows the wave instead"
                }
            eventLog.add(clock(), EventCategory.ERROR, what, why.stackTraceToString())
        }
    }

    private suspend fun store(shown: Int) {
        try {
            settings.setGreetingsShown(shown)
        } catch (notStored: IOException) {
            val what = "Dually could not store how many greetings it has shown ($shown)"
            eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
        }
    }
}
