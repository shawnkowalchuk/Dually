package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// The app's mark since 2026-10-13 (ADR-010): a dark "D" on a rounded square of the accent, the
// brand design's "Icon B, Monogram". Until then it was an "M" on a lime square.
//
// The letter is a drawn shape and no letter of a typeface: a stem and a bowl, with the counter
// cut out of it. It is written once for the screens, here, and repeated where Compose cannot
// reach: the launcher icon (res/drawable/ic_launcher_foreground.xml), the notification icon
// (ic_stat_trip.xml), the widget's mark (widget_mark.xml), and as plain numbers for the PDF
// (core/report/ReportPage.kt). AppMarkTest fails if one of them is another shape.

/**
 * The letter, as the design gives it: in a box 72 wide and 76 high, around the point 0, 0. Two
 * closed outlines, the letter and its counter; filled by the even-odd rule, the second is a
 * hole in the first.
 */
internal const val APP_MARK_LETTER_PATH =
    "M-36,-38H0A36,38 0 0 1 0,38H-36ZM-18,-20H0A18,20 0 0 1 0,20H-18Z"

/** The box the letter is drawn in. */
internal const val APP_MARK_LETTER_WIDTH = 72f
internal const val APP_MARK_LETTER_HEIGHT = 76f

/**
 * How much of the square the letter takes up, as the design draws the mark in the interface's
 * top line: 21.6 by 22.8 on a square of 40.
 */
internal const val APP_MARK_LETTER_WIDTH_SHARE = 0.54f
internal const val APP_MARK_LETTER_HEIGHT_SHARE = 0.57f

/** How large the design draws the app's mark in the top line of a screen. */
private val MarkSize = 36.dp

/** The letter alone. Its colour is the caller's: `Icon` replaces the one written here. */
internal val AppMarkLetter: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "AppMarkLetter",
            defaultWidth = APP_MARK_LETTER_WIDTH.dp,
            defaultHeight = APP_MARK_LETTER_HEIGHT.dp,
            viewportWidth = APP_MARK_LETTER_WIDTH,
            viewportHeight = APP_MARK_LETTER_HEIGHT,
        )
        // The path is drawn around 0, 0, and a vector's own corner is 0, 0: moved by half the
        // box, the letter fills it.
        .addGroup(
            translationX = APP_MARK_LETTER_WIDTH / 2,
            translationY = APP_MARK_LETTER_HEIGHT / 2,
        ).addPath(
            pathData = addPathNodes(APP_MARK_LETTER_PATH),
            // Never seen: Icon() replaces this colour with its tint.
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ).clearGroup()
        .build()
}

/**
 * The app's mark: the letter on a rounded square of the accent. Decoration wherever it stands:
 * the app's name is written beside it, so a screen reader is told nothing.
 *
 * @param size how wide and high the square is. The letter and the corner are the design's for
 * the 36 dp of a screen's top line; no screen draws the mark at another size today.
 */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = MarkSize) {
    val colors = MiloTheme.colors.mark
    Box(
        modifier =
            modifier
                .size(size)
                .background(colors.fill, MiloTheme.shapes.appMark)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppMarkLetter,
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier.size(
                size * APP_MARK_LETTER_WIDTH_SHARE,
                size * APP_MARK_LETTER_HEIGHT_SHARE,
            ),
        )
    }
}

@Preview
@Composable
private fun AppMarkPreview() {
    MiloTheme {
        Surface { AppMark() }
    }
}
