package com.shawnkowalchuk.milo.feature.tripgraph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.trip.TripRepository
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * What the graph screen shows.
 *
 * @param figures null while the year's trips are being read.
 * @param selected the month whose point was pressed, or null.
 */
data class TripGraphState(
    val year: Int,
    val canStepForward: Boolean,
    val figures: List<MonthFigure>?,
    val selected: Int?,
    val unit: DistanceUnit,
)

/**
 * The Trips screen's graph (since 2026-10-16): a year's months, Business and Personal. Only the
 * year on screen is read from storage, by its span of time, as Trips reads a month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripGraphViewModel(
    private val trips: TripRepository,
    unit: StateFlow<DistanceUnit>,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private data class Choice(val year: Int, val current: YearMonth, val selected: Int?)

    private val choice = MutableStateFlow(opening())

    val state: StateFlow<TripGraphState> =
        choice
            .flatMapLatest { chosen ->
                val zoneNow = zone()
                val from = monthSpan(YearMonth.of(chosen.year, Month.JANUARY), zoneNow).fromMs
                val until = monthSpan(YearMonth.of(chosen.year, Month.DECEMBER), zoneNow).untilMs
                combine(trips.observeTripsStartedBetween(from, until), unit) { read, shownIn ->
                    TripGraphState(
                        year = chosen.year,
                        canStepForward = chosen.year < chosen.current.year,
                        figures = yearFigures(read, chosen.year, chosen.current, zoneNow, shownIn),
                        selected = chosen.selected,
                        unit = shownIn,
                    )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
                choice.value.let { TripGraphState(it.year, false, null, null, unit.value) },
            )

    fun onPreviousYear() = choice.update { it.copy(year = it.year - 1, selected = null) }

    fun onNextYear() = choice.update {
        if (it.year < it.current.year) it.copy(year = it.year + 1, selected = null) else it
    }

    /** A month's point was pressed: it is picked, or put down again if it was. */
    fun onSelect(index: Int) = choice.update {
        it.copy(
            selected = index.takeIf { i ->
                i !=
                    it.selected
            },
        )
    }

    private fun opening(): Choice {
        val current = YearMonth.from(localDateOf(clock(), zone()))
        return Choice(current.year, current, selected = current.monthValue - 1)
    }
}
