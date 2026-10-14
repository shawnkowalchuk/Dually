package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shawnkowalchuk.milo.core.report.ReportInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report for the accountant is drawn in the app's colours, but outside Compose, so it
 * repeats six of them (`ReportInk` in `core/report/`). Nothing but this test would notice the
 * two drifting apart.
 */
class ReportInkTest {
    @Test
    fun `the report's dark, accent, amber, mark and words on dark are the theme's own`() {
        val theme = MiloColorScheme
        val own = MiloExtraColors

        assertEquals(hex(theme.background.toArgb()), hex(ReportInk.DARK.argb))
        assertEquals(hex(theme.primary.toArgb()), hex(ReportInk.ACCENT.argb))
        assertEquals(hex(own.mark.text.toArgb()), hex(ReportInk.MARK_LETTER.argb))
        assertEquals(hex(own.attentionTile.text.toArgb()), hex(ReportInk.AMBER.argb))
        assertEquals(hex(theme.onSurface.toArgb()), hex(ReportInk.ON_DARK.argb))
        assertEquals(hex(theme.onSurfaceVariant.toArgb()), hex(ReportInk.ON_DARK_QUIET.argb))
        // A tile on paper is Bone, the brand's light surface, which on the screens is the
        // colour of the words.
        assertEquals(hex(theme.onSurface.toArgb()), hex(ReportInk.PANEL.argb))
    }

    @Test
    fun `every word on the paper can be read on its ground`() {
        // The pairs the layout uses (`ReportBlocks.kt`): what is written, and on what.
        val white = 0xFFFFFFFF.toInt()
        val pairs =
            mapOf(
                "a word on paper" to (ReportInk.DARK.argb to white),
                "a quiet word on paper" to (ReportInk.QUIET.argb to white),
                "a word on a tile" to (ReportInk.DARK.argb to ReportInk.PANEL.argb),
                "a quiet word on a tile" to (ReportInk.QUIET.argb to ReportInk.PANEL.argb),
                "a word on the Business tile" to (ReportInk.DARK.argb to ReportInk.AMBER.argb),
                "a word on a dark tile" to (ReportInk.ON_DARK.argb to ReportInk.DARK.argb),
                "a quiet word on a dark tile" to
                    (ReportInk.ON_DARK_QUIET.argb to ReportInk.DARK.argb),
                "the total on its dark tile" to (ReportInk.ACCENT.argb to ReportInk.DARK.argb),
            )
        for ((name, pair) in pairs) {
            val ratio = contrast(Color(pair.first), Color(pair.second))
            assertTrue("$name: %.2f to 1, and it needs 4.5".format(ratio), ratio >= 4.5)
        }
        // The letter of the mark is a shape, held to what a mark needs.
        val mark = contrast(Color(ReportInk.MARK_LETTER.argb), Color(ReportInk.ACCENT.argb))
        assertTrue("the mark's letter: %.2f to 1".format(mark), mark >= 3.0)
    }

    /** "FF15120F": a colour a failure message can be read by. */
    private fun hex(argb: Int): String = "%08X".format(argb)
}
