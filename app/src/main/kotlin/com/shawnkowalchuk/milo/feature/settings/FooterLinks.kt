package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The end of Settings as one short list (since 2026-11-12, Shawn: "it is to busy"): Email
 * support, Rate Dually on Google Play, and Buy me a coffee where it may be shown, each a row of
 * one tile, divided as a tile's rows are. Until then each was a tile of its own, with a line
 * under it that said what it does; the rows keep the label, its arrowhead and the one line that
 * matters (the address), and say what a press does to a screen reader.
 *
 * @param rows the rows, in order: each a [FooterLink].
 */
@Composable
internal fun FooterLinks(rows: List<@Composable () -> Unit>) {
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.WIDE,
        gap = MiloTheme.spacing.textGap,
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            row()
        }
    }
}

/**
 * One row of [FooterLinks]: its label with the arrowhead, an optional line under it, and the
 * problem a press ran into, if it did. The whole row is the button.
 *
 * @param pressLabel what a screen reader says the press does, after "double tap to".
 */
@Composable
internal fun FooterLink(
    label: String,
    pressLabel: String,
    onClick: () -> Unit,
    value: String? = null,
    problem: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = pressLabel, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = MiloTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
    ) {
        TileLinkLabel(label)
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        problem?.let { StatusRow(label = it, status = RowStatus.PROBLEM) }
    }
}
