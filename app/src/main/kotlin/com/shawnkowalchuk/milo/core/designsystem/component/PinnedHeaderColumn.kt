package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The top line of a screen ([AppHeader]) where it stays: pinned at the top while what is under it
 * scrolls (since 2026-10-19; Shawn: "the top header should be pinned on all screens"). It keeps
 * the room it always had: the small gap above it, and the design's 16 under it before the first
 * tile. For a screen whose list is lazy: put it above the list in a column, and give the list
 * the rest of the height.
 */
@Composable
fun PinnedHeader(modifier: Modifier = Modifier, header: @Composable () -> Unit) {
    val spacing = MiloTheme.spacing
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter)
                .padding(top = spacing.small, bottom = spacing.extraSmall + spacing.tileGap),
    ) {
        header()
    }
}

/**
 * A screen of tiles under its pinned top line ([PinnedHeader]): the tiles are laid out as in
 * [TileColumn] and scroll, and the top line stays. With a [button], that main button stays in
 * view at the bottom too, however far the tiles are scrolled (since 2026-10-09: the welcome
 * page's OK and Setup's "Done, go to Settings"; Shawn: "pin the bottom where it says ...
 * continue so it shows all the time"). Nothing is drawn behind either: the tiles end at them.
 *
 * The column is as high as [modifier] makes it, which should be the whole screen
 * (`fillMaxSize`), or the button is not at the bottom.
 *
 * @param header the top line, an [AppHeader].
 * @param scrollState for a screen that scrolls the tiles itself (to a field that needs
 * attention); otherwise its own.
 * @param button the main button, usually a [PrimaryButton] with `fillMaxWidth`, or null.
 */
@Composable
fun PinnedHeaderColumn(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    button: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiloTheme.spacing
    Column(modifier = modifier) {
        PinnedHeader(header = header)
        TileColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // A large font or a small window scrolls the tiles; the top line stays.
                    .verticalScroll(scrollState)
                    .padding(bottom = if (button == null) spacing.small else 0.dp),
            content = content,
        )
        if (button != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.gutter)
                        .padding(top = spacing.tileGap, bottom = spacing.small),
            ) {
                button()
            }
        }
    }
}
