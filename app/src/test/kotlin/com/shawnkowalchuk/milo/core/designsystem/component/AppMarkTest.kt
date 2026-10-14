package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloExtraColors
import com.shawnkowalchuk.milo.core.report.MARK_COUNTER_HALF_HEIGHT
import com.shawnkowalchuk.milo.core.report.MARK_COUNTER_HALF_WIDTH
import com.shawnkowalchuk.milo.core.report.MARK_LETTER_HALF_HEIGHT
import com.shawnkowalchuk.milo.core.report.MARK_LETTER_HALF_WIDTH
import com.shawnkowalchuk.milo.core.report.MARK_LETTER_HEIGHT_SHARE
import com.shawnkowalchuk.milo.core.report.MARK_LETTER_WIDTH_SHARE
import com.shawnkowalchuk.milo.core.report.ReportInk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's mark is drawn in five places, and only one of them is Compose: the top line of
 * every screen. The launcher icon, the notification icon and the widget's mark are XML that
 * Android or the home screen draws, and the PDF draws it from plain numbers. Nothing but this
 * test would notice one of them being another letter, or another colour, than the rest.
 */
class AppMarkTest {
    // Gradle runs the unit tests from the module's folder, app/.
    private val drawables = File("src/main/res/drawable")

    /** The letter as an XML drawable writes it, which is as Compose is handed it. */
    private val letterInXml = """android:pathData="$APP_MARK_LETTER_PATH""""

    @Test
    fun `the letter's path can be read`() {
        assertTrue(AppMarkLetter.root.size > 0)
        assertEquals(APP_MARK_LETTER_WIDTH, AppMarkLetter.viewportWidth, 0f)
        assertEquals(APP_MARK_LETTER_HEIGHT, AppMarkLetter.viewportHeight, 0f)
    }

    @Test
    fun `the launcher icon and the widget's mark are drawn with the same letter`() {
        for (name in listOf("ic_launcher_foreground.xml", "widget_mark.xml")) {
            val xml = File(drawables, name).readText()
            assertTrue("$name holds another letter", xml.contains(letterInXml))
            assertTrue("$name: the counter must be a hole", xml.contains("""fillType="evenOdd""""))
        }
    }

    @Test
    fun `the notification icon is the square with the same letter cut out of it`() {
        val xml = File(drawables, "ic_stat_trip.xml").readText()

        // One path: the square first, then the letter, which the even-odd rule makes a hole.
        assertTrue(xml.contains(""" $APP_MARK_LETTER_PATH""""))
        assertTrue(xml.contains("""fillType="evenOdd""""))
    }

    @Test
    fun `no drawable still holds the letter of the old name`() {
        // The "M" was sixteen straight lines, written with the letter L: the "D" has none.
        for (name in listOf("ic_launcher_foreground.xml", "ic_stat_trip.xml", "widget_mark.xml")) {
            val paths =
                Regex("""android:pathData="([^"]+)"""")
                    .findAll(File(drawables, name).readText())
                    .map { it.groupValues[1] }
                    .toList()
            assertTrue(name, paths.isNotEmpty())
            paths.forEach { assertFalse("$name: $it", it.contains('L')) }
        }
    }

    @Test
    fun `the PDF's numbers are the letter's`() {
        // Every number of the path, in the order it is written: each outline starts at its top
        // left corner, runs right to the middle, and turns through half an ellipse.
        val numbers =
            Regex("""-?\d+(\.\d+)?""").findAll(APP_MARK_LETTER_PATH).map { it.value.toFloat() }
        val (outer, counter) = numbers.toList().let { it.chunked(it.size / 2) }

        assertEquals(listOf(-MARK_LETTER_HALF_WIDTH, -MARK_LETTER_HALF_HEIGHT), outer.take(2))
        assertEquals(listOf(MARK_LETTER_HALF_WIDTH, MARK_LETTER_HALF_HEIGHT), outer.slice(3..4))
        assertEquals(listOf(-MARK_COUNTER_HALF_WIDTH, -MARK_COUNTER_HALF_HEIGHT), counter.take(2))
        assertEquals(listOf(MARK_COUNTER_HALF_WIDTH, MARK_COUNTER_HALF_HEIGHT), counter.slice(3..4))
        assertEquals(APP_MARK_LETTER_WIDTH, MARK_LETTER_HALF_WIDTH * 2, 0f)
        assertEquals(APP_MARK_LETTER_HEIGHT, MARK_LETTER_HALF_HEIGHT * 2, 0f)
    }

    @Test
    fun `the letter is as large on the PDF's mark as on a screen's`() {
        assertEquals(APP_MARK_LETTER_WIDTH_SHARE, MARK_LETTER_WIDTH_SHARE, 0f)
        assertEquals(APP_MARK_LETTER_HEIGHT_SHARE, MARK_LETTER_HEIGHT_SHARE, 0f)
    }

    @Test
    fun `the mark has the theme's two colours wherever it is drawn`() {
        val mark = MiloExtraColors.mark
        val xml = File("src/main/res/values/colors.xml").readText()
        val written =
            mapOf("milo_mark_ground" to mark.fill, "milo_mark_letter" to mark.text)
        for ((name, color) in written) {
            val value =
                Regex("""<color name="$name">#([0-9A-Fa-f]{6})</color>""")
                    .find(xml)
                    ?.groupValues
                    ?.get(1)
            assertEquals(name, hex(color), value?.uppercase())
        }
        // The launcher icon and the widget's mark take both from there, and write none.
        for (name in listOf(
            "ic_launcher_background.xml",
            "ic_launcher_foreground.xml",
            "widget_mark.xml",
        )) {
            val drawable = File(drawables, name).readText()
            assertTrue(name, drawable.contains("@color/milo_mark_"))
            assertFalse(name, Regex("""Color="#""").containsMatchIn(drawable))
        }
        assertEquals(hex(mark.fill), "%06X".format(ReportInk.ACCENT.argb and 0xFFFFFF))
        assertEquals(hex(mark.text), "%06X".format(ReportInk.MARK_LETTER.argb and 0xFFFFFF))
    }

    private fun hex(color: Color): String = "%06X".format(color.toArgb() and 0xFFFFFF)
}
