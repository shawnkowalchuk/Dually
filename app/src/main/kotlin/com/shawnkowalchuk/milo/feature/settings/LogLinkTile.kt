package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The tile that opens the Log, a button of the bottom bar until 2026-10-16, when Service took its
 * place (Shawn: "can we stick the log menu under settings"). Drawn as the Version tile is: the
 * label with its arrowhead, a line under it, and the whole tile one button.
 */
@Composable
internal fun LogLinkTile(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Tile(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        press = TilePress(label = stringResource(R.string.settings_log_open), onClick = onOpen),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        TileLinkLabel(stringResource(R.string.settings_log_label))
        Text(
            text = stringResource(R.string.settings_log_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
