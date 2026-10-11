package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.core.designsystem.component.MascotParty
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.partyPopHeard
import com.shawnkowalchuk.milo.feature.party.PartyViewModel
import com.shawnkowalchuk.milo.feature.party.homeIsDrawn
import com.shawnkowalchuk.milo.platform.party.OneYearParty
import com.shawnkowalchuk.milo.platform.trip.TripSoundPlayer
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * The long-lived objects of MilO's one-year party (2026-10-10): the mascot's party over Home on
 * the day of the first recorded trip, a year on and every year after, with the pop of a party
 * popper at every trip's start that day.
 *
 * Like [CheckObjects] and the others, it is a part of the [AppContainer], which creates it once
 * and through which everything reaches it (`container.party`); it is a class of its own only
 * because the container is at its size limit, and it follows the container's rules.
 *
 * @param container where the storage these objects read is made.
 */
class PartyObjects(private val appContext: Context, private val container: AppContainer) {
    /**
     * Stands between the trip service, which asks it at every trip's start whether today is the
     * day, and Home, which plays the party. It is handed one read of the stored trips, the
     * settings, the event log and the crash files, and neither the trip controller nor a way to
     * write a trip: it cannot start, end or change one.
     */
    val oneYear: OneYearParty by lazy {
        OneYearParty(
            tripStarts = container.tripRepository::findTripStarts,
            settings = container.settingsStore,
            eventLog = container.eventLogRepository,
            crashFileStore = container.crashFileStore,
            clock = container.clock,
            zone = ZoneId::systemDefault,
        )
    }

    /**
     * Plays the pop when the party's own popper goes off over Home, with the same player a
     * trip uses, so it sounds there as it does at a trip's start. The trip service has a player
     * of its own; this one marks its log lines as the party's.
     */
    val popPlayer: TripSoundPlayer by lazy {
        TripSoundPlayer(appContext, waitTurn = false) { note ->
            container.applicationScope.launch {
                val what = "The mascot's party over Home. $note"
                container.eventLogRepository.add(container.clock(), EventCategory.SERVICE, what)
            }
        }
    }
}

/** How [MiloApp] gets the party's ViewModel. */
internal fun partyViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            PartyViewModel(
                party = container.party.oneYear,
                popHeard = { container.settingsStore.current().partyPopHeard },
                playPop = container.party.popPlayer::playPartyPop,
                eventLog = container.eventLogRepository,
                clock = container.clock,
                zone = ZoneId::systemDefault,
            )
        }
    }

/**
 * Tells the party whether Home is in front and can show it: the trip service asks in the moment
 * a trip starts, to know whether the pop is the popper's to make. And has MilO go to Home when
 * the party is asked for on demand.
 *
 * - **MilO on the screen** is said by the activity's own life, as it happens and not at the next
 *   drawing of the screen: MilO is paused the moment something else takes the screen.
 * - **With the phone's animations switched off there is no party,** only its pop at the trip's
 *   start. The setting is followed while MilO runs: the day's party, which is owed, plays when
 *   they are switched on again that day. **One asked for on demand is dropped instead,** as it
 *   reaches Home: it was a wish to look at it now, and a pop some minutes later, when the
 *   animations come back, would be a fright.
 *
 * @param onHome true while Home is the screen on top.
 * @param firstRun how far the first start has got ([homeIsDrawn]).
 * @param unsaved whether the screen on top holds something typed and not saved: [goHome] is
 * tried again when that changes, as a tapped notification's way is.
 * @param goHome leaves the screen on top for Home, by [MiloApp]'s gate for every way out.
 * Called only while another screen is on top.
 */
@Composable
internal fun PartyWatch(
    party: PartyViewModel,
    onHome: Boolean,
    firstRun: FirstRunStage?,
    unsaved: Boolean,
    goHome: () -> Unit,
) {
    LifecycleResumeEffect(party) {
        party.onInFront(true)
        onPauseOrDispose { party.onInFront(false) }
    }
    val drawn = homeIsDrawn(onHome, firstRun)
    LaunchedEffect(party, drawn) {
        val scale = coroutineContext[MotionDurationScale]
        snapshotFlow { scale?.scaleFactor != 0f }.collect { animationsOn ->
            party.onHomeReady(drawn && animationsOn)
            if (drawn && !animationsOn) party.onAskedDropped()
        }
    }
    val asked by party.asked.collectAsState()
    LaunchedEffect(asked, unsaved) { if (asked && !onHome) goHome() }
}

/**
 * The party over the screen, drawn after it, while one plays. It takes no press from what is
 * under it.
 */
@Composable
internal fun PartyOverHome(party: PartyViewModel) {
    val showing by party.showing.collectAsState()
    if (showing != null) {
        MascotParty(
            onDone = party::onDone,
            onShown = party::onShown,
            onPop = party::onPop,
            onUnreadable = party::onUnreadable,
        )
    }
}
