package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text colour of the theme against every fill it is written on, held to the contrast the
 * accessibility guidelines (WCAG 2) ask for. The colours are the owner's design and are changed
 * in one file; this is what says so when a change makes something hard to read, before it is
 * found in sunlight on the dashboard.
 *
 * Since 2026-10-13 the accent is an orange, where it was a lime (ADR-010). Every pair that was
 * held before is still held, to the same measure; the tags of the What's new screen and the
 * app's mark were added, which no test held; and two tests at the end are new, for what a
 * warm accent puts at risk: that what is waiting (amber) and what is wrong (red) could be
 * taken for the accent, or the error red for the brand's own red, which is for gauges.
 */
class ColorContrastTest {
    private val scheme = MiloColorScheme
    private val colors = MiloExtraColors
    private val status = MiloStatus

    /** Every fill that text or a mark is put on, by the name used in a failure message. */
    private val fills =
        mapOf(
            "the page" to scheme.background,
            "a tile" to scheme.surfaceContainer,
            "a control" to colors.control.fill,
            "a text field" to colors.fieldFill,
            "a quiet fill" to colors.quietFill,
        )

    @Test
    fun `ordinary text can be read on every fill`() {
        val texts =
            mapOf(
                "main text" to scheme.onSurface,
                "secondary text" to scheme.onSurfaceVariant,
                "log text" to colors.logText,
                "a link in the accent" to scheme.primary,
                "a pressed link" to colors.accentPressed,
                "red text" to scheme.error,
            )
        for ((textName, text) in texts) {
            for ((fillName, fill) in fills) {
                assertReadable("$textName on $fillName", text, fill, TEXT)
            }
        }
    }

    @Test
    fun `the words on a coloured fill can be read`() {
        val pairs =
            mapOf(
                "the main button" to FillAndText(scheme.primary, scheme.onPrimary),
                "a control" to colors.control,
                "the attention tile's title" to colors.attentionTile,
                "the attention tile's second line" to
                    FillAndText(colors.attentionTile.fill, colors.attentionSecondaryText),
                "the attention tile's button" to colors.attentionButton,
                "a button that takes something away" to colors.danger,
                "the chosen chip" to colors.chipSelected,
                "any other chip" to colors.chip,
                "the log's trip tag" to colors.logTags.trip,
                "the log's Bluetooth tag" to colors.logTags.bluetooth,
                "the log's Android Auto tag" to colors.logTags.androidAuto,
                "the log's service tag" to colors.logTags.service,
                "the log's error tag" to colors.logTags.error,
                "a wheel's digit" to colors.wheels.whole,
                "the last wheel's digit while it counts" to colors.wheels.counting,
                "the tag of something new" to colors.changeTags.new,
                "the tag of something improved" to colors.changeTags.improved,
                "the tag of something fixed" to colors.changeTags.fixed,
                "the tag of a security fix" to colors.changeTags.security,
            )
        for ((name, pair) in pairs) {
            assertReadable(name, pair.text, pair.fill, TEXT)
        }
    }

    @Test
    fun `Material's own pairs can be read, for the stock components that use them`() {
        val pairs =
            mapOf(
                "primary" to FillAndText(scheme.primary, scheme.onPrimary),
                "primaryContainer" to
                    FillAndText(scheme.primaryContainer, scheme.onPrimaryContainer),
                "secondary" to FillAndText(scheme.secondary, scheme.onSecondary),
                "secondaryContainer" to
                    FillAndText(scheme.secondaryContainer, scheme.onSecondaryContainer),
                "tertiary" to FillAndText(scheme.tertiary, scheme.onTertiary),
                "tertiaryContainer" to
                    FillAndText(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                "error" to FillAndText(scheme.error, scheme.onError),
                "errorContainer" to FillAndText(scheme.errorContainer, scheme.onErrorContainer),
                "background" to FillAndText(scheme.background, scheme.onBackground),
                "surface" to FillAndText(scheme.surface, scheme.onSurface),
                "surfaceVariant" to FillAndText(scheme.surfaceVariant, scheme.onSurfaceVariant),
                "inverseSurface" to FillAndText(scheme.inverseSurface, scheme.inverseOnSurface),
                "inversePrimary" to FillAndText(scheme.inverseSurface, scheme.inversePrimary),
            )
        for ((name, pair) in pairs) {
            assertReadable(name, pair.text, pair.fill, TEXT)
        }
    }

    @Test
    fun `a status dot stands out from a tile and from the page, and its mark from the dot`() {
        val dots =
            mapOf(
                "ok" to status.ok,
                "problem" to status.problem,
                "unknown" to status.unknown,
                "to confirm" to status.toConfirm,
            )
        for ((name, dot) in dots) {
            assertReadable("the $name dot on a tile", dot, scheme.surfaceContainer, MARK)
            assertReadable("the $name dot on the page", dot, scheme.background, MARK)
        }
        // The fourth dot is an empty ring and has no mark.
        for (name in listOf("ok", "problem", "unknown")) {
            assertReadable("the mark on the $name dot", status.onDot, dots.getValue(name), MARK)
        }
    }

    @Test
    fun `the icons of the bottom bar and the switch's thumb can be made out`() {
        val bar = scheme.surfaceContainer
        assertReadable("the bar's current icon", scheme.primary, bar, MARK)
        assertReadable("the bar's other icons", scheme.onSurfaceVariant, bar, MARK)
        assertReadable("the thumb of a switch that is on", scheme.onPrimary, scheme.primary, MARK)
        assertReadable(
            "the thumb of a switch that is off",
            scheme.onSurfaceVariant,
            colors.control.fill,
            MARK,
        )
        assertReadable("an outline that carries meaning", scheme.outline, bar, MARK)
    }

    @Test
    fun `the wheel that counts is told apart from the tile it stands on`() {
        // Its fill is what says "this digit is turning", so the fill itself is held to the
        // measure of a mark. The other cells are not: they only frame their digits.
        val tile = scheme.surfaceContainer
        assertReadable("the last cell counting", colors.wheels.counting.fill, tile, MARK)
        assertReadable("the dot of the side that is down", scheme.outline, tile, MARK)
    }

    @Test
    fun `the line around a text field can be made out, whatever state the field is in`() {
        // The line is what shows where a field is, most of all an empty one, so it is held to
        // the same measure as a mark: against the field's own fill inside it, and against the
        // tile or the page outside it.
        val lines =
            mapOf(
                "the line around an idle field" to colors.fieldBorder,
                "the line around the field being typed in" to scheme.primary,
                "the line around a field in error" to scheme.error,
            )
        val grounds =
            mapOf(
                "the field's own fill" to colors.fieldFill,
                "a tile" to scheme.surfaceContainer,
                "the page" to scheme.background,
            )
        for ((lineName, line) in lines) {
            for ((groundName, ground) in grounds) {
                assertReadable("$lineName against $groundName", line, ground, MARK)
            }
        }
    }

    @Test
    fun `the letter of the app's mark stands out from its square, and the square from the page`() {
        assertReadable("the mark's letter", colors.mark.text, colors.mark.fill, MARK)
        assertReadable("the mark's square on the page", colors.mark.fill, scheme.background, MARK)
        assertReadable(
            "the mark's square on a tile",
            colors.mark.fill,
            scheme.surfaceContainer,
            MARK,
        )
    }

    @Test
    fun `white words are never put on the accent`() {
        // The brand's own rule: white on this orange is too faint for words, so every pair that
        // has the accent as its fill has the page's dark as its words.
        assertFalse(contrast(Color.White, scheme.primary) >= TEXT)
        val onAccent =
            listOf(
                scheme.onPrimary,
                scheme.onPrimaryContainer,
                colors.logTags.trip.text,
                colors.changeTags.new.text,
                colors.wheels.counting.text,
            )
        onAccent.forEach { assertTrue(it == scheme.background) }
    }

    @Test
    fun `what is told apart by its colour is still told apart beside the orange accent`() {
        // Each of these is on a screen beside something in the accent, and says something else
        // than the accent does: a tick's dot beside a problem's, the tile that starts a trip
        // above the tile for what is waiting, the tags of the log in one column.
        val accent = scheme.primary
        val others =
            mapOf(
                "what is waiting" to colors.attentionTile.text,
                "what is wrong" to scheme.error,
                "what takes something away" to colors.danger.text,
                "the Bluetooth tag" to colors.logTags.bluetooth.text,
                "the Android Auto tag" to colors.logTags.androidAuto.text,
                "what could not be found out" to status.unknown,
                "what is to confirm" to status.toConfirm,
            )
        for ((name, other) in others) {
            assertApart("the accent and $name", accent, other)
        }
        assertApart("what is waiting and what is wrong", colors.attentionTile.text, scheme.error)
        assertApart(
            "the tag of something new and of something fixed",
            colors.changeTags.new.fill,
            colors.changeTags.fixed.fill,
        )
    }

    @Test
    fun `the brand's red for gauges is no colour of a screen, and no error looks like it`() {
        // Redline is the red zone of a gauge and nothing else: never a button, an error or a
        // warning. Beside the orange accent it could hardly be told from it, which is why what
        // is wrong is said in another red, lighter and pinker.
        val redline = Color(0xFFFF3D2E)
        assertFalse(difference(scheme.primary, redline) >= APART)
        val reds =
            listOf(
                scheme.error,
                scheme.errorContainer,
                status.problem,
                colors.danger.text,
                colors.danger.fill,
                colors.logTags.error.text,
                colors.logTags.error.fill,
                colors.changeTags.security.text,
                colors.changeTags.security.fill,
            )
        reds.forEach { assertApart("a red of the app and the brand's Redline", it, redline) }
    }

    @Test
    fun `the measure itself agrees with the guidelines' own examples`() {
        // Black on white is 21 to 1, the most there is, and a colour on itself is 1 to 1.
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrast(Color.White, Color.Black) in 20.99..21.01)
        assertTrue(contrast(scheme.primary, scheme.primary) in 0.99..1.01)
        // The grey 767676 is the well-known lightest grey that passes on white: 4.54 to 1.
        assertTrue(contrast(Color(0xFF767676), Color.White) in 4.53..4.55)
    }

    private fun assertReadable(name: String, mark: Color, fill: Color, needed: Double) {
        val ratio = contrast(mark, fill)
        assertTrue("$name: %.2f to 1, and it needs $needed".format(ratio), ratio >= needed)
    }

    private fun assertApart(name: String, one: Color, other: Color) {
        val apart = difference(one, other)
        assertTrue("$name are %.1f apart, and need $APART".format(apart), apart >= APART)
    }

    private companion object {
        /** What text needs. The guidelines let large text off with less; MilO does not. */
        const val TEXT = 4.5

        /** What an icon, a dot or an outline needs when it carries meaning. */
        const val MARK = 3.0

        /** How far apart two colours have to be to be told apart at a glance (`difference`). */
        const val APART = 40.0
    }
}
