package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.ReportOptions

/**
 * "Report options": what a report holds beyond the Business trips, as two switches that are
 * remembered for every later report (Shawn, 2026-10-16). Drawn like the screen's other tiles: a
 * title, its rows, and a quiet line that says the choice is kept.
 */
@Composable
internal fun ReportOptionsTile(
    options: ReportOptions?,
    onListPersonal: (Boolean) -> Unit,
    onBusinessShare: (Boolean) -> Unit,
) {
    if (options == null) return
    Tile(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap)) {
            Text(
                text = stringResource(R.string.report_options_title),
                style = MaterialTheme.typography.titleSmall,
            )
            SwitchRow(
                label = stringResource(R.string.report_options_personal),
                checked = options.listPersonal,
                onCheckedChange = onListPersonal,
            )
            SwitchRow(
                label = stringResource(R.string.report_options_share),
                checked = options.businessShare,
                onCheckedChange = onBusinessShare,
            )
            Quiet(stringResource(R.string.report_options_kept))
        }
    }
}
