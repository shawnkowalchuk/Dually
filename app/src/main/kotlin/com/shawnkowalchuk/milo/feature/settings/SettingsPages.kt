package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// Settings without drawers (since 2026-11-12, Shawn: "it is to busy. to many drawers", and,
// shown the mock-up, "do all of it"). The main screen keeps the settings that are changed in
// passing (Units and Look, the truck and the driving alert, the odometer) and gives every other
// group one tile that sums it up and opens a page of its own. On a page the group's tiles are
// the ones the main screen had, drawn open: no fold, no "About" line to tap.

/**
 * The pages Settings opens, each with the tiles it holds.
 *
 * @param titleRes the page's title, which is also the name of the tile that opens it.
 */
enum class SettingsPage(val titleRes: Int) {
    /** The work schedule, what becomes of a trip outside it, and the daily check. */
    WORK_HOURS(R.string.settings_page_work_hours),

    /** Who the report is from and to, the rate, and the monthly reminder. */
    REPORT(R.string.settings_page_report),

    /** The four numbers the trip rules run with. */
    TRIPS(R.string.settings_page_trips),

    /** The connect sound and the trip-start sound. */
    SOUNDS(R.string.settings_sounds_title),

    /** The home-screen widget. */
    WIDGET(R.string.settings_page_widget),

    /** Android's backup, export and import, and the GPS points. */
    DATA(R.string.settings_page_data),
}

/**
 * True inside a page: [SettingsFold] then draws its tile open, with no fold, and [SettingsAbout]
 * its sentences as they are. False on the main screen, where both fold as they did.
 */
internal val LocalSettingsOnPage = staticCompositionLocalOf { false }

/**
 * What a tile says about itself beyond its settings ("About the reminder"). On a page the
 * sentences stand at the end of the tile as they are; elsewhere they fold behind [label].
 */
@Composable
internal fun SettingsAbout(label: String, content: @Composable ColumnScope.() -> Unit) {
    if (LocalSettingsOnPage.current) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            content = content,
        )
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    QuietExpander(label = label, expanded = open, onToggle = { open = !open }, content = content)
}

/**
 * A tile of the main screen that sums a page up and opens it: the page's name with the
 * arrowhead every link tile has, what it is set to, and a quieter line under that. The whole
 * tile is one button, read out as the three lines.
 *
 * @param line what the page is set to: "Mon–Fri · 5 days · 42.5 h a week".
 * @param second a quieter line under it, or null.
 */
@Composable
internal fun SettingsLinkTile(
    title: String,
    line: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    second: String? = null,
) {
    Tile(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        padding = TilePadding.EVEN,
        press = TilePress(label = title, onClick = onOpen),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        TileLinkLabel(title)
        Text(text = line, style = MaterialTheme.typography.titleSmall)
        second?.let { Note(it) }
    }
}
