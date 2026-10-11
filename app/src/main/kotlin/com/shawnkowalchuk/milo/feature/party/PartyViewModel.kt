package com.shawnkowalchuk.milo.feature.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MilO's one-year party as the screens see it: whether one plays over Home, and what the
 * playing one does to the rest of MilO. The day itself, and who plays the pop at a trip's
 * start, is `platform/party`'s business; which party plays is decided by [settled].
 *
 * **The day's party** plays once for the trips that started today, the next time Home is in
 * front; when the mascot is first drawn it is owed no longer. **A party on demand** plays once
 * and stores nothing.
 *
 * **The pop** is played by the party itself on the frame the popper goes off ([onPop]), in every
 * showing: silent, on the day, if both of MilO's sounds are switched off; always heard on
 * demand, as the Settings screen's Play is. If the trip service left the pop of a trip's start
 * to this showing and the showing ends before its popper went off, the pop is played as it ends:
 * a trip's start is not left without it.
 *
 * @param popHeard whether the day's pop is heard, as the settings have it right now.
 * @param playPop plays the pop. Called on the main thread.
 * @param clock MilO's own clock (ADR-006).
 * @param zone the zone the phone is in now.
 */
class PartyViewModel(
    private val party: OneYearParty,
    private val popHeard: suspend () -> Boolean,
    private val playPop: () -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private val facts = MutableStateFlow(PartyFacts())

    /** The party that plays over Home, or null. */
    val showing: StateFlow<PartyShowing?> =
        facts.map { it.showing }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** [PartyFacts.hasTheScreen], for the greeting: null until the settings have been read. */
    val hasTheScreen: StateFlow<Boolean?> =
        facts.map { it.hasTheScreen }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True while a party on demand is waiting for Home. */
    val asked: StateFlow<Boolean> = party.askedFor

    /** MilO is on the screen, and Home is ready to show a party: the two halves of "watching". */
    private var inFront = false
    private var homeReady = false

    /** The party whose mascot has been drawn and that has not ended, or null. */
    private var playing: PartyShowing? = null

    /** Whether the party that is playing was left the pop of a trip's start (`PopHandover`). */
    private var owesStartPop = false

    init {
        viewModelScope.launch {
            party.owedSinceMs.collect { owed -> change { copy(read = true, owedSinceMs = owed) } }
        }
        viewModelScope.launch { party.askedFor.collect { asked -> change { copy(asked = asked) } } }
    }

    /** MilO came to the front, or left it. */
    fun onInFront(inFront: Boolean) {
        this.inFront = inFront
        change { copy(watching = inFront && homeReady) }
    }

    /**
     * @param ready whether Home is the screen on top, with the first start over and the phone's
     * animations not switched off.
     */
    fun onHomeReady(ready: Boolean) {
        homeReady = ready
        change { copy(watching = inFront && homeReady) }
    }

    /** The mascot is about to be drawn: this party has been shown. */
    fun onShown() {
        playing = facts.value.showing
        if (playing != PartyShowing.THE_DAY) return
        val forMs = facts.value.owedSinceMs
        owesStartPop = party.takePop()
        change { copy(shownForMs = forMs) }
        if (forMs != null) viewModelScope.launch { party.shown(forMs) }
    }

    /** The popper goes off. */
    fun onPop() {
        owesStartPop = false
        pop(always = playing == PartyShowing.ON_DEMAND)
    }

    /**
     * The party is over: it played to its end, was cut short, or never began.
     *
     * @param popped whether its popper had gone off.
     */
    fun onDone(popped: Boolean) {
        if (owesStartPop && !popped) pop(always = false)
        owesStartPop = false
        playing = null
        // Still showing means that Home is still watching: the party ended by itself. It is not
        // tried again by this start of MilO, whether or not anything of it was drawn: otherwise
        // a party that cannot play would begin again at once, for ever.
        val was = facts.value
        when (was.showing) {
            PartyShowing.THE_DAY -> change { copy(showing = null, shownForMs = was.owedSinceMs) }
            PartyShowing.ON_DEMAND -> change { copy(showing = null, asked = false) }
            null -> Unit
        }
    }

    /** The party's pictures could not be read. Nothing was drawn, and nothing is tried again. */
    fun onUnreadable(why: IOException) {
        val what = "MilO's mascot did not hold his party: a picture of him could not be read"
        viewModelScope.launch {
            eventLog.add(clock(), EventCategory.ERROR, what, why.stackTraceToString())
        }
        val forMs = facts.value.owedSinceMs
        if (facts.value.showing == PartyShowing.THE_DAY && forMs != null) {
            viewModelScope.launch { party.shown(forMs) }
        }
    }

    /** A request on demand that cannot be met: something else kept the screen. */
    fun onAskedDropped() = party.askedDealtWith()

    private fun pop(always: Boolean) {
        viewModelScope.launch { if (always || popHeard()) playPop() }
    }

    private fun change(how: PartyFacts.() -> PartyFacts) {
        val before = facts.value.showing
        facts.update { it.how().settled(clock(), zone()) }
        val after = facts.value
        // Told at once: the trip service asks in the moment a trip starts.
        party.homeWatching = after.watching
        // A party on demand that has stopped playing, however it stopped, is not played again.
        if (before == PartyShowing.ON_DEMAND && after.showing == null) party.askedDealtWith()
    }

    override fun onCleared() {
        party.homeWatching = false
    }
}
