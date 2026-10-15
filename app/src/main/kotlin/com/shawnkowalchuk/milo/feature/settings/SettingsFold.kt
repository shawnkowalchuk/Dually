package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ExpandableHeading
import com.shawnkowalchuk.milo.core.designsystem.component.ExpandableTile
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * A Settings tile that folds away under its title and a line that sums it up (Shawn,
 * 2026-10-14: "can we please add expandable cards on the settings screen I think it is very
 * long some of the data we dont need to look at all the time. once it is filled out it could
 * probably be hidden and reduce scrolling the settings page"). It is the design system's
 * [ExpandableTile], the one the Trips screen's days fold in, with the tile's own rows inside.
 *
 * **It starts closed, unless [unfinished]:** a tile that lacks something it needs (the name
 * on the report, say) starts open, so that what is missing can be seen. Whether it is open is
 * kept through a turn of the phone and forgotten when Settings is left.
 *
 * @param summary what the tile is set to, in one line: "Mon–Fri · 8:00 AM – 4:30 PM".
 * @param gap the room between the tile's rows, as the tile had it before it folded.
 */
@Composable
internal fun SettingsFold(
    title: String,
    summary: String,
    unfinished: Boolean = false,
    gap: Dp = MiloTheme.spacing.rowGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(unfinished) }
    ExpandableTile(
        heading =
            ExpandableHeading(
                title = title,
                line = summary,
                openLabel = stringResource(R.string.settings_fold_open),
                closeLabel = stringResource(R.string.settings_fold_close),
            ),
        expanded = open,
        onToggle = { open = !open },
    ) {
        // The folding tile leaves the room above and below rows to the rows themselves, as
        // a list's are; a Settings tile's are not, so the room is given here.
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = MiloTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}
