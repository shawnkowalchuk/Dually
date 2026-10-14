package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import com.shawnkowalchuk.milo.R
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typefaces are three files that the text styles lean on in ways nothing but these tests
 * would notice breaking: each file has to be the one the owner supplied, the variable one has
 * to be able to draw every weight and width it is asked for, each entry of a family has to ask
 * its file for its own weight and width (without that, Archivo draws everything semi-bold at
 * its normal width and no build fails), and no style may ask a family for a weight it does not
 * have.
 */
class TypefaceTest {
    // Gradle runs the unit tests from the module's folder, app/.
    private val fonts = File("src/main/res/font")
    private val archivo = File(fonts, "archivo.ttf")
    private val readoutMedium = File(fonts, "ibm_plex_mono_medium.ttf")
    private val readoutSemiBold = File(fonts, "ibm_plex_mono_semibold.ttf")

    @Test
    fun `the font files are the ones the owner supplied, and the only ones`() {
        // As handed over on 2026-10-13: Archivo's variable file (weight and width) and IBM Plex
        // Mono's Medium and SemiBold. A new version of a font is a new look at its licence and
        // at every screen: change its value in that change, not to make the test pass.
        val approved =
            mapOf(
                archivo to "0e094a7d3c7c4c25cf1310c4b30014f1dae9332220b1c2c88f4fa996f0b05053",
                readoutMedium to
                    "a9b4c49bb299e05b5f6c481e7fb5e78943d2793249a0c8874ab574a2d1ea6755",
                readoutSemiBold to
                    "d3c38e55c78f5b0f28009fddba4834ec503278936a5986032424c9bd2d23aa46",
            )
        for ((file, expected) in approved) {
            assertTrue("Not found: ${file.absolutePath}", file.isFile)
            val sha256 =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
            assertEquals(file.name, expected, sha256)
        }
        // A font file nothing names is weight the download carries for nothing (Sora went on
        // 2026-10-13).
        assertEquals(approved.keys.map { it.name }.sorted(), fonts.list().orEmpty().sorted())
    }

    @Test
    fun `each font's licence is in the repository`() {
        for (name in listOf("Archivo-OFL.txt", "IBMPlexMono-OFL.txt")) {
            val licence = File("../licenses", name)
            assertTrue("Not found: ${licence.absolutePath}", licence.isFile)
            assertTrue(name, licence.readText().contains("SIL OPEN FONT LICENSE Version 1.1"))
        }
    }

    @Test
    fun `Archivo can be drawn at every weight and width the text styles ask for`() {
        val file = FontFile(archivo)
        val weights = file.axis("wght")
        val widths = file.axis("wdth")

        assertTrue("Archivo has no weight axis: it is not the variable font", weights != null)
        assertTrue("Archivo has no width axis: it is not the variable font", widths != null)
        (TextWeights + HeadlineWeight).forEach { weight ->
            assertTrue(
                "Weight ${weight.weight} is outside the file's $weights",
                weight.weight.toFloat() in weights!!,
            )
        }
        listOf(TEXT_WIDTH, HEADLINE_WIDTH).forEach { width ->
            assertTrue("Width $width is outside the file's $widths", width in widths!!)
        }
        // A headline is wider than running text: that is the whole of the "Expanded".
        assertTrue(HEADLINE_WIDTH > TEXT_WIDTH)
    }

    @Test
    fun `each entry of the text family asks Archivo for its own weight, at the text's width`() {
        val entries = (MiloFontFamily as FontListFontFamily).fonts.map { it as ResourceFont }

        assertEquals(TextWeights, entries.map { it.weight })
        entries.forEach { entry ->
            assertEquals(R.font.archivo, entry.resId)
            assertEquals("${entry.weight}", entry.weight.weight.toFloat(), entry.asked("wght"), 0f)
            assertEquals("${entry.weight}", TEXT_WIDTH, entry.asked("wdth"), 0f)
        }
    }

    @Test
    fun `the headline family is Archivo, extra bold and widened`() {
        val entry = (MiloHeadlineFontFamily as FontListFontFamily).fonts.single() as ResourceFont

        assertEquals(R.font.archivo, entry.resId)
        assertEquals(HeadlineWeight, entry.weight)
        assertEquals(HeadlineWeight.weight.toFloat(), entry.asked("wght"), 0f)
        assertEquals(HEADLINE_WIDTH, entry.asked("wdth"), 0f)
    }

    @Test
    fun `the readout family is one file for each of its two weights`() {
        val entries = (MiloReadoutFontFamily as FontListFontFamily).fonts.map { it as ResourceFont }

        assertEquals(ReadoutWeights, entries.map { it.weight })
        assertEquals(
            listOf(R.font.ibm_plex_mono_medium, R.font.ibm_plex_mono_semibold),
            entries.map { it.resId },
        )
        // Each file is drawn as it is: it has one weight, the one its entry is listed under.
        listOf(readoutMedium, readoutSemiBold).zip(ReadoutWeights).forEach { (file, weight) ->
            assertEquals(file.name, null, FontFile(file).axis("wght"))
            assertEquals(file.name, weight.weight, FontFile(file).ownWeight())
        }
    }

    @Test
    fun `no text style asks its family for a weight the family does not have`() {
        // Asked for a weight that is not listed, Compose would quietly draw one that is, and
        // the text would not be the weight its style names.
        val weightsOf =
            mapOf(
                MiloFontFamily to TextWeights,
                MiloHeadlineFontFamily to listOf(HeadlineWeight),
                MiloReadoutFontFamily to ReadoutWeights,
            )
        allStyles().forEach { (name, style) ->
            val weights = weightsOf[style.fontFamily]
            assertTrue("$name is set in a family this file does not name", weights != null)
            assertTrue("$name asks for ${style.fontWeight}", style.fontWeight in weights!!)
        }
    }

    @Test
    fun `the label's line is no lower than Archivo's own`() {
        // A line lower than the typeface asks for would bring two lines of a sentence too
        // close. Higher is what the screens were laid out on (`Type.kt` says why).
        assertEquals(TextUnitType.Em, LabelLine.type)
        assertTrue(LabelLine.value >= FontFile(archivo).ownLine())
    }

    @Test
    fun `every text style names its own line height, as a share of its text size`() {
        // A style without one takes over the line height of whatever it stands in, and one in
        // sp would not follow a size that a screen changes.
        allStyles().forEach { (name, style) ->
            assertEquals("$name: line height", TextUnitType.Em, style.lineHeight.type)
            assertEquals("$name: letter spacing", TextUnitType.Em, style.letterSpacing.type)
        }
    }

    @Test
    fun `the headlines are the large headings, the dialogs' titles and the app's name`() {
        val headlines = allStyles().filter { (_, style) ->
            style.fontFamily == MiloHeadlineFontFamily
        }

        assertEquals(listOf("headlineMedium", "titleLarge", "appName"), headlines.map { it.first })
    }

    @Test
    fun `the readouts are the figures that stand by themselves, and the event log's two`() {
        val readouts = allStyles().filter { (_, style) ->
            style.fontFamily == MiloReadoutFontFamily
        }

        assertEquals(
            listOf(
                "displayLarge",
                "displayMedium",
                "displaySmall",
                "headlineLarge",
                "headlineSmall",
                "listFigure",
                "sideFigure",
                "logTime",
                "logTag",
                "tileFigure",
                "fieldFigure",
            ),
            readouts.map { it.first },
        )
    }

    // What an entry of a family asks its file for on one axis: "wght" and "wdth" are the names
    // every variable font gives its weight and its width.
    private fun ResourceFont.asked(axis: String): Float =
        variationSettings.settings.single { it.axisName == axis }.toVariationValue(Density(1f))

    private fun allStyles(): List<Pair<String, TextStyle>> {
        val material = MiloTypography
        val own = MiloTextStyles()
        return listOf(
            "displayLarge" to material.displayLarge,
            "displayMedium" to material.displayMedium,
            "displaySmall" to material.displaySmall,
            "headlineLarge" to material.headlineLarge,
            "headlineMedium" to material.headlineMedium,
            "headlineSmall" to material.headlineSmall,
            "titleLarge" to material.titleLarge,
            "titleMedium" to material.titleMedium,
            "titleSmall" to material.titleSmall,
            "bodyLarge" to material.bodyLarge,
            "bodyMedium" to material.bodyMedium,
            "bodySmall" to material.bodySmall,
            "labelLarge" to material.labelLarge,
            "labelMedium" to material.labelMedium,
            "labelSmall" to material.labelSmall,
            "mainButton" to own.mainButton,
            "fieldText" to own.fieldText,
            "sentence" to own.sentence,
            "quietLabel" to own.quietLabel,
            "standInWords" to own.standInWords,
            "appName" to own.appName,
            "smallTileFigure" to own.smallTileFigure,
            "listFigure" to own.listFigure,
            "rowFigure" to own.rowFigure,
            "sideFigure" to own.sideFigure,
            "countWords" to own.countWords,
            "spanFigure" to own.spanFigure,
            "tileLabel" to own.tileLabel,
            "accentNote" to own.accentNote,
            "logTime" to own.logTime,
            "logTag" to own.logTag,
            "tileFigure" to own.tileFigure,
            "fieldFigure" to own.fieldFigure,
        )
    }
}

/**
 * Just enough of the TrueType file format to read four things from a font file: how many units
 * its letters are drawn in, how much room it asks for above and below a line of text, the
 * range of one of its axes if it has it, and the one weight a file without axes is drawn at. A
 * font file starts with a list of named tables, each with the place it starts at; every number
 * in it is stored with its highest byte first.
 */
private class FontFile(file: File) {
    private val bytes: ByteBuffer = ByteBuffer.wrap(file.readBytes())

    // The list of tables: a count at byte 4, then 16 bytes for each (name, checksum, start,
    // length) from byte 12 on.
    private val tables: Map<String, Int> =
        (0 until bytes.getShort(4).toInt()).associate { index ->
            val entry = 12 + 16 * index
            name(entry) to bytes.getInt(entry + 8)
        }

    /** The line the font asks for, as a share of the text size: 1.09 means 109 percent. */
    fun ownLine(): Float {
        val unitsPerEm = bytes.getShort(tables.getValue("head") + 18).toInt() and 0xFFFF
        val hhea = tables.getValue("hhea")
        val above = bytes.getShort(hhea + 4).toInt()
        val below = bytes.getShort(hhea + 6).toInt()
        val between = bytes.getShort(hhea + 8).toInt()
        return (above - below + between).toFloat() / unitsPerEm
    }

    /** The weight the file says it is, from 100 to 900: for a file that has one weight only. */
    fun ownWeight(): Int = bytes.getShort(tables.getValue("OS/2") + 4).toInt() and 0xFFFF

    /**
     * The least and the most the file can be drawn at on the axis called [tag] ("wght" for
     * its weight, "wdth" for its width), or null if it has no such axis.
     */
    fun axis(tag: String): ClosedFloatingPointRange<Float>? {
        val fvar = tables["fvar"] ?: return null
        val firstAxis = fvar + (bytes.getShort(fvar + 4).toInt() and 0xFFFF)
        val axisCount = bytes.getShort(fvar + 8).toInt() and 0xFFFF
        val axisSize = bytes.getShort(fvar + 10).toInt() and 0xFFFF
        return (0 until axisCount)
            .map { firstAxis + it * axisSize }
            .firstOrNull { axis -> name(axis) == tag }
            ?.let { axis -> fixed(axis + 4)..fixed(axis + 12) }
    }

    // The four letters a table or an axis is named by.
    private fun name(at: Int): String =
        String(ByteArray(4) { bytes.get(at + it) }, Charsets.US_ASCII)

    // A number with a fraction, stored as a whole number of 65,536ths.
    private fun fixed(at: Int): Float = bytes.getInt(at) / 65536f
}
