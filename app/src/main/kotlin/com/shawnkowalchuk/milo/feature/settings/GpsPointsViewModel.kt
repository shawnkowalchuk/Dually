package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.point.HeldPoints
import com.shawnkowalchuk.milo.data.point.PointCleanup
import com.shawnkowalchuk.milo.data.point.sentMonthsEnded
import com.shawnkowalchuk.milo.data.report.SentReport
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

/** What the last press of "Delete" came to. */
enum class GpsPointsOutcome { DELETED, FAILED }

/**
 * The Settings tile for the GPS points.
 *
 * @param storedBytes about how much room every stored fix takes.
 * @param sent the points of the months whose report was sent and that have ended, kept ones
 * included.
 * @param deletedBytes how much the last delete freed, for [GpsPointsOutcome.DELETED].
 */
data class GpsPointsCardState(
    val storedBytes: Long,
    val sent: HeldPoints,
    val working: Boolean,
    val outcome: GpsPointsOutcome?,
    val deletedBytes: Long,
)

/**
 * The Settings tile that says how much room the GPS points take and deletes those of every
 * sent month (2026-10-14): the months whose tile on the Report screen Shawn answered "Keep",
 * and those sent before the tile existed. It has a ViewModel of its own, like the other tiles
 * with a state of their own.
 *
 * [state] is null until storage has been read, and when it cannot be: then there is no tile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpsPointsViewModel(
    private val cleanup: PointCleanup,
    sent: Flow<List<SentReport>>,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    private data class Passing(
        val working: Boolean = false,
        val outcome: GpsPointsOutcome? = null,
        val deletedBytes: Long = 0,
    )

    private val passing = MutableStateFlow(Passing())

    /** Counted up after a delete, so that the points are counted again. */
    private val deletes = MutableStateFlow(0)

    private val stored: Flow<Pair<Long, HeldPoints>?> =
        combine(sent, deletes) { sentNow, _ -> sentNow }.mapLatest(::read)

    val state: StateFlow<GpsPointsCardState?> =
        combine(stored, passing) { read, now ->
            read?.let { (bytes, held) ->
                GpsPointsCardState(bytes, held, now.working, now.outcome, now.deletedBytes)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /** "Delete", once the tile has asked and Shawn has said yes. */
    fun onDelete() {
        val shown = state.value ?: return
        if (shown.working || shown.sent.points == 0L) return
        passing.value = Passing(working = true)
        viewModelScope.launch {
            val deleted = cleanup.delete(shown.sent, "in Settings")
            passing.value =
                if (deleted) {
                    Passing(outcome = GpsPointsOutcome.DELETED, deletedBytes = shown.sent.bytes)
                } else {
                    Passing(outcome = GpsPointsOutcome.FAILED)
                }
            deletes.update { it + 1 }
        }
    }

    /** Storage as it is now, or null if it cannot be read: this runs in the trip service's process. */
    private suspend fun read(sentNow: List<SentReport>): Pair<Long, HeldPoints>? = try {
        val months = sentMonthsEnded(sentNow, localDateOf(clock(), zone()))
        cleanup.storedBytes() to cleanup.heldIn(months, zone())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        null
    }
}
