package com.shawnkowalchuk.milo.core.report

// What a laid-out page of the PDF is made of, and the paper it is laid out on. Pure Kotlin: the
// layout pass decides where every word and rule goes, and the drawing pass (`platform/report/`)
// only carries that out, so the layout is tested without Android.
//
// Every length is in PostScript points, 1/72 of an inch, the unit Android's PdfDocument works
// in. A text size of 10 is a 10 pt font.
//
// Since 2026-10-07 the report wears the app's look (Shawn: "similar to the app design and
// beautifully designed, not some generic PDF"): the app's typefaces in the weights the screens
// use, the app's mark and its colours on rounded tiles, on white paper. Since 2026-10-13 that
// look is the brand's (ADR-010): Archivo and IBM Plex Mono in place of Sora, the "D" in place
// of the "M", and the design's light palette (Bone, Amber and Orange with dark words) in place
// of the lime. What the report says and the rules that place it did not change with it; a
// narrower typeface breaks an address onto fewer lines, and that is all that moved.

/** US Letter, upright: 8.5 by 11 inches. */
const val REPORT_PAGE_WIDTH = 612
const val REPORT_PAGE_HEIGHT = 792

// The margins are the report's own and no content rectangle is set on the page, so a coordinate here
// is a coordinate on the paper. Three quarters of an inch clears the edge no printer reaches.
private const val MARGIN = 54f

/** Where the content of a page begins and ends, from left to right and from top to bottom. */
const val CONTENT_LEFT = MARGIN
const val CONTENT_RIGHT = REPORT_PAGE_WIDTH - MARGIN
const val CONTENT_TOP = MARGIN
const val CONTENT_BOTTOM = REPORT_PAGE_HEIGHT - MARGIN

/** The footer's line stands in the bottom margin, clear of the content above it. */
const val FOOTER_BASELINE = REPORT_PAGE_HEIGHT - 30f

/** A line of text is this much higher than its font size, which keeps lines apart. */
private const val LEADING = 1.3f

/** The weights the report uses, as a font names them: those of the screens (`Type.kt`). */
private const val REGULAR = 400
private const val MEDIUM = 500
private const val SEMI_BOLD = 600
private const val EXTRA_BOLD = 800

/**
 * Which of the app's typefaces a kind of text is set in, as on the screens (`Type.kt`): running
 * text, a headline, or a figure that stands by itself. The drawing pass knows which file each
 * is (`platform/report/ReportTypefaces.kt`).
 */
enum class ReportFace {
    /** Archivo: every word of the report. */
    TEXT,

    /** Archivo, extra bold and widened: the app's name. */
    HEADLINE,

    /**
     * IBM Plex Mono: the report's numbers. Every digit is as wide as every other, so the
     * figures of a column stand under each other digit by digit. It comes in the two weights
     * the app carries, 500 and 600.
     */
    FIGURES,
}

/**
 * The kinds of text on the report, each with its size in points, its weight, from 100 to
 * 900 as a font names it, and its typeface.
 *
 * Since 2026-10-08 the sizes are a step smaller than they were (a trip's line 8
 * points, not 9), so that a month of trips takes fewer pages (Shawn: "It also should be more
 * compressed. i can see this being pages and pages."). At 8 points most addresses fit on one
 * line without being shortened, which Shawn chose over abbreviating them.
 */
enum class ReportTextStyle(
    val size: Float,
    val weight: Int,
    val face: ReportFace = ReportFace.TEXT,
) {
    /** The app's name, beside its mark at the top of the first page. */
    BRAND(14f, EXTRA_BOLD, ReportFace.HEADLINE),

    /** The quieter line under the app's name, and under the period: what this is and when. */
    BRAND_LINE(8f, REGULAR),

    /** The period, large, at the end of the top tile. */
    PERIOD(14f, SEMI_BOLD),

    /** The small label at the top of a tile of figures: "Business", "Personal". */
    TILE_LABEL(8f, SEMI_BOLD),

    /** The kilometres of a tile of figures, large. */
    FIGURE(20f, SEMI_BOLD, ReportFace.FIGURES),

    /** The unit after a large figure. */
    FIGURE_UNIT(10f, SEMI_BOLD, ReportFace.FIGURES),

    /** The line under a large figure: how many trips it adds up. */
    TILE_LINE(8f, MEDIUM),

    /** The label over a detail of the sender, such as "Name". */
    LABEL(7.5f, MEDIUM),

    /** The detail under its label. */
    VALUE(9f, SEMI_BOLD),

    /** An odometer's figure under its label: a detail that is a number. */
    VALUE_FIGURE(9f, SEMI_BOLD, ReportFace.FIGURES),

    /** A note: what the report lists, the legend, the words under the signature line. */
    NOTE(8f, REGULAR),

    /** A day's heading. */
    DAY(8.5f, SEMI_BOLD),

    /** The titles of the columns. */
    COLUMN(7f, SEMI_BOLD),

    /** A trip's addresses, and the line under them. */
    CELL(8f, REGULAR),

    /** A trip's two times and its kilometres. */
    CELL_FIGURE(8f, MEDIUM, ReportFace.FIGURES),

    /** The word before a day's subtotal. */
    SUM_LABEL(8f, SEMI_BOLD),

    /** A day's subtotal. */
    SUM(8f, SEMI_BOLD, ReportFace.FIGURES),

    /** What the period's total is of. */
    TOTAL_LABEL(10f, SEMI_BOLD),

    /** The period's total. */
    TOTAL(18f, SEMI_BOLD, ReportFace.FIGURES),

    /** The line at the bottom of every page. */
    FOOTER(7f, REGULAR),
    ;

    /** From the top of one line to the top of the next. */
    val leading: Float get() = size * LEADING
}

/**
 * The colours of the report: ink on white paper. Six are the app's own and repeat the theme's
 * tokens (`core/designsystem/theme/Color.kt`), which only Compose can read; `ReportInkTest`
 * keeps them equal. The other three are the brand's colours for a light ground, where the app
 * has none: the screens are dark, and a dark page would be a page of ink.
 *
 * @param argb the colour as Android's `Color` writes it: alpha, red, green, blue.
 */
enum class ReportInk(val argb: Int) {
    /** The app's page colour: the ink of every word on paper, and the top and total tiles. */
    DARK(0xFF15120F.toInt()),

    /** The app's accent, orange: the square of its mark, and the total on its dark tile. */
    ACCENT(0xFFFF5A12.toInt()),

    /** The dark of the letter on the app's mark. */
    MARK_LETTER(0xFF1E1A17.toInt()),

    /**
     * Amber, the brand's ground for what is to stand out without being the accent: the
     * Business tile, with dark words. On the screens it is the colour of what is waiting.
     */
    AMBER(0xFFFFB648.toInt()),

    /** The app's main text colour, for words on a dark tile. */
    ON_DARK(0xFFFFF4E8.toInt()),

    /** The app's quieter text colour, for the quieter words on a dark tile. */
    ON_DARK_QUIET(0xFFCDBFAF.toInt()),

    /** Quieter words on paper: labels, column titles, notes. 6.6 to 1 on white, 6.1 on a tile. */
    QUIET(0xFF6B5A4C.toInt()),

    /**
     * A tile on paper: Bone, the brand's light surface. It is the app's main text colour, which
     * is why the test holds it to the theme as well.
     */
    PANEL(0xFFFFF4E8.toInt()),

    /** The rules between a day's trips: the brand's line on a light ground. */
    HAIRLINE(0xFFE3D3C0.toInt()),
}

// The letter of the app's mark, as the design draws it around the point 0, 0: a stem on the
// left and half an ellipse on the right, with the same shape cut out of its middle. The
// screens draw it from a path (`APP_MARK_LETTER_PATH` in the design system); the PDF is drawn
// outside Compose, so the same numbers stand here once more, and AppMarkTest reads them out of
// that path and fails if they differ.

/** Half the letter's width and half its height: the outer half-ellipse's two radii. */
const val MARK_LETTER_HALF_WIDTH = 36f
const val MARK_LETTER_HALF_HEIGHT = 38f

/** The same for the counter, the hole in the letter. */
const val MARK_COUNTER_HALF_WIDTH = 18f
const val MARK_COUNTER_HALF_HEIGHT = 20f

/** How much of the mark's square the letter takes up, as the design draws the mark. */
const val MARK_LETTER_WIDTH_SHARE = 0.54f
const val MARK_LETTER_HEIGHT_SHARE = 0.57f

/** One thing drawn on a page. */
sealed interface PageItem {
    /** The same item [by] points further down the page. */
    fun movedDown(by: Float): PageItem

    /**
     * One line of text.
     *
     * @param x where the line starts, or where it ends if it is [rightAligned].
     * @param baseline the line the letters stand on.
     */
    data class Text(
        val text: String,
        val x: Float,
        val baseline: Float,
        val style: ReportTextStyle,
        val rightAligned: Boolean = false,
        val ink: ReportInk = ReportInk.DARK,
    ) : PageItem {
        override fun movedDown(by: Float): Text = copy(baseline = baseline + by)
    }

    /** A straight rule across the page at height [y], [thickness] points thick. */
    data class Rule(
        val fromX: Float,
        val toX: Float,
        val y: Float,
        val thickness: Float,
        val ink: ReportInk = ReportInk.DARK,
    ) : PageItem {
        override fun movedDown(by: Float): Rule = copy(y = y + by)
    }

    /**
     * The letter of the app's mark, the "D", drawn to fill the box it is given: a shape of
     * its own and no letter of a typeface (`core/designsystem/component/AppMark.kt`). The
     * square it stands on is a [Box], placed before it.
     */
    data class MarkLetter(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val ink: ReportInk = ReportInk.MARK_LETTER,
    ) : PageItem {
        override fun movedDown(by: Float): MarkLetter = copy(top = top + by, bottom = bottom + by)
    }

    /**
     * A filled rectangle with round corners: a tile, the mark's square. Drawn before what
     * stands on it, because the items of a page are drawn in their order.
     */
    data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val ink: ReportInk,
    ) : PageItem {
        override fun movedDown(by: Float): Box = copy(top = top + by, bottom = bottom + by)
    }
}

/**
 * One page, ready to be drawn.
 *
 * @param number counted from 1.
 */
data class ReportPage(val number: Int, val items: List<PageItem>)

/**
 * How wide a line of text comes out in a style, in points. The drawing pass answers with the
 * very paint it draws with, so that what was measured is what is drawn; a test answers with
 * plain arithmetic.
 */
fun interface TextMeasure {
    fun width(text: String, style: ReportTextStyle): Float
}

/**
 * Breaks [text] into lines no wider than [maxWidth], at its spaces. A single word that is wider
 * than a line by itself (a long street name without a space, a web address) is cut where the
 * line is full, so that nothing ever runs out of its column.
 *
 * @return at least one line; an empty text gives one empty line.
 */
internal fun wrap(
    text: String,
    maxWidth: Float,
    style: ReportTextStyle,
    measure: TextMeasure,
): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in text.split(' ').filter { it.isNotEmpty() }) {
        val longer = if (line.isEmpty()) word else "$line $word"
        if (measure.width(longer, style) <= maxWidth) {
            line = longer
            continue
        }
        if (line.isNotEmpty()) lines += line
        var rest = word
        while (measure.width(rest, style) > maxWidth && rest.length > 1) {
            val cut = fittingLength(rest, maxWidth, style, measure)
            lines += rest.substring(0, cut)
            rest = rest.substring(cut)
        }
        line = rest
    }
    lines += line
    return lines
}

/**
 * How many characters of [word] fit in [maxWidth]: at least one, so that a column too narrow
 * for a single letter still makes progress, and never so as to cut a character that is stored
 * as two (an emoji) in half.
 */
private fun fittingLength(
    word: String,
    maxWidth: Float,
    style: ReportTextStyle,
    measure: TextMeasure,
): Int {
    var length = 1
    while (length < word.length) {
        if (measure.width(word.substring(0, length + 1), style) > maxWidth) break
        length++
    }
    if (length < word.length && Character.isLowSurrogate(word[length])) {
        length = if (length > 1) length - 1 else length + 1
    }
    return length
}
