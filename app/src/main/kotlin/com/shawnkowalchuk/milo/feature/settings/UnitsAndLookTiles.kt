package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.AppLook
import com.shawnkowalchuk.milo.core.util.DistanceUnit

/**
 * "Units" and "Look" side by side, each a small tile that is one button: it names the choice in
 * force, and a tap switches to the other (since 2026-11-12, Shawn: "can you please have the units
 * and look outside as just clickable. i dont want everything in a expandable drawer. also make
 * sure they are side by side"). Until then each was a fold with a segmented control in it.
 *
 * Drawn like the Truck and Driving alert pair at the top of the screen (`TilePair`, `Tile` with
 * a press), so nothing was added to the design system.
 *
 * @param onUnit and [onLook] store the other choice. Every screen, the widget, the Android Auto
 * screen and the notifications follow the stored settings, so the change shows at once.
 */
@Composable
internal fun UnitsAndLookTiles(
    unit: DistanceUnit,
    look: AppLook,
    onUnit: (DistanceUnit) -> Unit,
    onLook: (AppLook) -> Unit,
) {
    val miles = unit == DistanceUnit.MILES
    val light = look == AppLook.LIGHT
    val unitWord = if (miles) R.string.settings_units_miles else R.string.settings_units_kilometres
    val unitHint =
        if (miles) R.string.settings_units_tap_kilometres else R.string.settings_units_tap_miles
    val lookWord = if (light) R.string.settings_look_light else R.string.settings_look_dark
    val lookHint = if (light) R.string.settings_look_tap_dark else R.string.settings_look_tap_light
    TilePair(
        first = { half ->
            SwitchingTile(
                label = stringResource(R.string.settings_units_title),
                chosen = stringResource(unitWord),
                hint = stringResource(unitHint),
                onClick = { onUnit(if (miles) DistanceUnit.KILOMETRES else DistanceUnit.MILES) },
                modifier = half,
            )
        },
        second = { half ->
            SwitchingTile(
                label = stringResource(R.string.settings_look_title),
                chosen = stringResource(lookWord),
                hint = stringResource(lookHint),
                onClick = { onLook(if (light) AppLook.DARK else AppLook.LIGHT) },
                modifier = half,
            )
        },
    )
}

/**
 * A small tile that is one button: its label, the choice in force, and in quiet words what a tap
 * changes it to. A screen reader reads the three as one button, and is told the hint as what the
 * press does.
 */
@Composable
private fun SwitchingTile(
    label: String,
    chosen: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Tile(
        modifier = modifier,
        padding = TilePadding.EVEN,
        press = TilePress(label = hint, onClick = onClick),
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileHeading(label)
        Text(text = chosen, style = MaterialTheme.typography.titleSmall)
        Note(hint)
    }
}
