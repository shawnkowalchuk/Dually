package com.shawnkowalchuk.milo.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.settings.ReportOptions
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The Report screen's "Report options" tile (since 2026-10-16): two switches, stored in the
 * settings file so that every later report is made the same way. A ViewModel of its own, beside
 * the screen's, which is at its size limit. The report itself reads the same stored options, so
 * the PDF, the CSV and the Business tile follow a press at once.
 *
 * @param options the stored options, and again each time they change.
 * @param setListPersonal and [setBusinessShare] store one switch.
 */
class ReportOptionsViewModel(
    options: Flow<ReportOptions>,
    private val setListPersonal: suspend (Boolean) -> Unit,
    private val setBusinessShare: suspend (Boolean) -> Unit,
) : ViewModel() {
    /** Null until the settings have been read, and while they cannot be: then no tile. */
    val state: StateFlow<ReportOptions?> =
        options
            .catch { unreadable ->
                if (unreadable is IOException) emit(ReportOptions()) else throw unreadable
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    fun onListPersonal(on: Boolean) = store { setListPersonal(on) }

    fun onBusinessShare(on: Boolean) = store { setBusinessShare(on) }

    /** A switch that could not be stored stays where storage has it: the tile shows that. */
    private fun store(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (notStored: IOException) {
                // The switch follows storage, which kept the old value; nothing else to undo.
                return@launch
            }
        }
    }
}
