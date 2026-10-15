package com.shawnkowalchuk.milo.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.point.HeldPoints
import com.shawnkowalchuk.milo.data.point.PointCleanup
import com.shawnkowalchuk.milo.data.point.offersCleanup
import com.shawnkowalchuk.milo.data.report.SentReport
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/** What the Report screen's tile for a sent month's GPS points shows. */
sealed interface MonthPointsState {
    /** No tile: not a sent month that has ended, its points kept, or none left. */
    data object Hidden : MonthPointsState

    /** The question: delete the month's GPS points? [failed] after a delete that did not work. */
    data class Offered(val month: YearMonth, val held: HeldPoints, val failed: Boolean = false) :
        MonthPointsState

    data class Deleting(val month: YearMonth) : MonthPointsState

    /** The month's points were deleted: about [bytes] of them. */
    data class Deleted(val month: YearMonth, val bytes: Long) : MonthPointsState
}

/**
 * The Report screen's tile that offers to delete a sent month's GPS points (2026-10-14). It has
 * a ViewModel of its own, beside the screen's, because that one is at its size limit and the
 * tile shares nothing with it but the month on the screen.
 *
 * Shawn chose a tile over a question that pops up: "I sent it" is followed by the mascot's run
 * and, from Google Play, a review card, and a tile is in the way of neither. It is offered for a
 * whole month whose report was sent, once the month has ended, until he deletes the points or
 * says to keep them.
 *
 * @param sent the list of sent reports, and again each time it changes.
 * @param kept the months whose points Shawn said to keep, and again each time that changes.
 * @param keep stores that [YearMonth]'s points are kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthPointsViewModel(
    private val cleanup: PointCleanup,
    sent: Flow<List<SentReport>>,
    kept: Flow<Set<YearMonth>>,
    private val keep: suspend (YearMonth) -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private val shown = MutableStateFlow<YearMonth?>(null)

    /** What a press on the tile led to, for the month it was pressed for. */
    private val pressed = MutableStateFlow<MonthPointsState?>(null)

    /** Counted up after a delete, so that the points are counted again. */
    private val deletes = MutableStateFlow(0)

    private val offer: Flow<MonthPointsState> =
        combine(shown, sent, kept, deletes) { month, sentNow, keptNow, _ ->
            Triple(month, sentNow, keptNow)
        }.mapLatest { (month, sentNow, keptNow) ->
            val today = localDateOf(clock(), zone())
            if (month == null || !offersCleanup(month, sentNow, keptNow, today)) {
                MonthPointsState.Hidden
            } else {
                offerFor(month)
            }
        }

    val state: StateFlow<MonthPointsState> =
        combine(offer, pressed, shown) { offered, press, month ->
            press?.takeIf { it.month == month } ?: offered
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            MonthPointsState.Hidden,
        )

    /** The month the Report screen shows, or null while it shows a date range. */
    fun onMonthShown(month: YearMonth?) {
        shown.value = month
    }

    fun onDelete() {
        val offered = state.value as? MonthPointsState.Offered ?: return
        pressed.value = MonthPointsState.Deleting(offered.month)
        viewModelScope.launch {
            val deleted = cleanup.delete(offered.held, "on the Report screen")
            pressed.value =
                if (deleted) {
                    MonthPointsState.Deleted(offered.month, offered.held.bytes)
                } else {
                    offered.copy(failed = true)
                }
            deletes.update { it + 1 }
        }
    }

    fun onKeep() {
        val offered = state.value as? MonthPointsState.Offered ?: return
        viewModelScope.launch { keep(offered.month) }
    }

    /**
     * The tile for [month], or none. A failure to read is no tile, never a thrown exception:
     * this runs in the process the trip service runs in.
     */
    private suspend fun offerFor(month: YearMonth): MonthPointsState = try {
        val held = cleanup.heldIn(listOf(month), zone())
        if (held.points > 0) MonthPointsState.Offered(month, held) else MonthPointsState.Hidden
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        MonthPointsState.Hidden
    }
}

private val MonthPointsState.month: YearMonth?
    get() = when (this) {
        MonthPointsState.Hidden -> null
        is MonthPointsState.Offered -> month
        is MonthPointsState.Deleting -> month
        is MonthPointsState.Deleted -> month
    }
