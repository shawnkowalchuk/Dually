package com.shawnkowalchuk.milo.core.designsystem.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher icon since 2026-10-13: the brand design's face (the mascot's dials and his
 * grin) on the mark's orange, with the letter alone as the layer for themed icons. The launcher
 * draws all of it outside the app, from XML that `tools/make_launcher_icon.py` writes, so
 * nothing but this test would notice the face losing its tie to the app's mark, a colour being
 * written into the drawing, or the letter of the old name coming back.
 */
class LauncherIconTest {
    // Gradle runs the unit tests from the module's folder, app/.
    private val res = File("src/main/res")
    private val face = File(res, "drawable/ic_launcher_foreground.xml").readText()
    private val icon = File(res, "mipmap-anydpi/ic_launcher.xml").readText()

    @Test
    fun `the icon is the face on the mark's ground, with the letter for themed icons`() {
        assertTrue(
            icon.contains("""<background android:drawable="@drawable/ic_launcher_background""""),
        )
        assertTrue(
            icon.contains("""<foreground android:drawable="@drawable/ic_launcher_foreground""""),
        )
        assertTrue(
            icon.contains("""<monochrome android:drawable="@drawable/ic_launcher_monochrome""""),
        )
    }

    @Test
    fun `the mouth is the letter of the app's mark, turned a quarter turn`() {
        // The letter's outer outline is the first of the mark's two outlines.
        val outline = APP_MARK_LETTER_PATH.substringBefore("Z") + "Z"

        assertTrue(face.contains("""android:pathData="$outline""""))
        assertTrue(face.contains("""android:rotation="90""""))
        // The letter itself, with its counter, would be the mark and no mouth.
        assertFalse(face.contains(APP_MARK_LETTER_PATH))
    }

    @Test
    fun `the face writes no colour of its own, and every colour it names exists`() {
        assertFalse(Regex("""Color="#""").containsMatchIn(face))

        val named = Regex("""@color/(\w+)""").findAll(face).map { it.groupValues[1] }.toSet()
        val written = File(res, "values/colors.xml").readText()
        assertTrue(named.isNotEmpty())
        for (name in named) {
            assertTrue(name, written.contains("""<color name="$name">"""))
        }
        // The line round the mouth is the mark's letter colour, so the two never part.
        assertTrue(named.contains("milo_mark_letter"))
    }

    @Test
    fun `the face stays inside what every launcher shape shows`() {
        // The design's square of 150 is the middle 72 of the canvas of 108.
        assertTrue(face.contains("""android:scaleX="0.48""""))
        assertTrue(face.contains("""android:translateX="18""""))
        assertEquals(
            108,
            Regex("""viewportWidth="(\d+)"""").find(face)?.groupValues?.get(1)?.toInt(),
        )
    }
}
