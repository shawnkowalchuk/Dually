package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shawnkowalchuk.milo.R

// The app's typefaces are the brand's since 2026-10-13 (ADR-010), and this is the one place
// they are named: every text style below is built on one of the three families here, so a
// change of typeface is a change here and of nothing else. (The PDF report is drawn outside
// Compose and names the same three files once more, in platform/report/ReportTypefaces.kt.)
//
//   - Archivo, for all running text: res/font/archivo.ttf;
//   - Archivo again, extra bold and widened, for headlines and the app's name;
//   - IBM Plex Mono, for readouts: a figure that stands by itself (the kilometres of a tile, the
//     odometer's wheels, the figure at the end of a row of a list) and the event log's time and
//     tags: res/font/ibm_plex_mono_medium.ttf and ibm_plex_mono_semibold.ttf. A figure inside a
//     sentence is running text and is not set in it.
//
// The files are as the owner supplied them on 2026-10-13, unchanged (TypefaceTest holds each to
// its SHA-256). Both typefaces are under the SIL Open Font License 1.1: licenses/Archivo-OFL.txt
// and licenses/IBMPlexMono-OFL.txt in this repository, which have to stay there for as long as
// the fonts do. Until that day the app was set in Sora (FINDINGS_LOG, 2026-10-06).
//
// Archivo is a variable font: one file that holds every weight from 100 to 900 and every width
// from 62 to 125 percent, where most typefaces come as one file for each. These four are the
// weights running text uses, and the only ones a text style built on MiloFontFamily may ask for
// (TypefaceTest holds them to that).
internal val TextWeights: List<FontWeight> =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

// The file's own width, at which running text is set. It is asked for by name all the same,
// like the weight: what a variable font is not told, it draws at the file's default.
internal const val TEXT_WIDTH = 100f

// A headline is one weight at one width: the design's "Archivo Expanded ExtraBold". The design
// draws the app's name in the interface's top line and its headlines at 112 percent, and
// keeps its widest, 125, for the name at poster size, which the app never shows.
internal val HeadlineWeight: FontWeight = FontWeight.ExtraBold
internal const val HEADLINE_WIDTH = 112f

// The two weights of IBM Plex Mono the app carries, one file each: this typeface is not a
// variable font.
internal val ReadoutWeights: List<FontWeight> = listOf(FontWeight.Medium, FontWeight.SemiBold)

// Each entry is the same file, asked for at one weight and one width. `weight` tells Compose
// which entry to pick for a text style. `variationSettings` tells the font how to draw itself:
// without it the file is drawn at its own default in every entry, and Archivo's default is
// weight 600, so regular text would look semi-bold and a headline would be neither extra bold
// nor wide. (Font() called without `variationSettings` is another function of the same name,
// and that one sets none.) A screenshot is the only place the mistake shows, so TypefaceTest
// reads both settings back from every entry.
private fun archivo(weight: FontWeight, width: Float): Font = Font(
    resId = R.font.archivo,
    weight = weight,
    variationSettings =
        FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(width)),
)

internal val MiloFontFamily: FontFamily =
    FontFamily(TextWeights.map { weight -> archivo(weight, TEXT_WIDTH) })

internal val MiloHeadlineFontFamily: FontFamily =
    FontFamily(archivo(HeadlineWeight, HEADLINE_WIDTH))

internal val MiloReadoutFontFamily: FontFamily =
    FontFamily(
        Font(resId = R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
        Font(resId = R.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold),
    )

// Every style starts from one of Material's own, for the two settings Material gives all its
// text and a bare TextStyle does not have: no extra padding above and below the letters, and a
// line whose text sits in the middle of its height.
private val Base: TextStyle = Typography().bodyLarge

// The design pulls the letters of its headlines together a little: by a fiftieth of the text
// size, and the app's name by half of that. Written in em, a share of the text size, as the
// design does, so the pull grows with the text.
private val HeadlineTracking = (-0.02).em
private val NameTracking = (-0.01).em
private val NoTracking = 0.em

// A large readout is pulled together too, as the first design pulled its large figures: every
// character of a monospace has a full cell, the decimal point included, and at 26 sp and more
// the point then stands in a gap as wide as a digit ("0 . 5", seen on an emulator, 2026-10-13).
// The pull is the same for every character, so the digits still stand under each other and a
// figure still changes without moving. A small readout is left as its typeface spaces it.
private val LargeReadoutTracking = (-0.06).em
private const val LARGE_READOUT_FROM_SP = 26

// How high a line of text is. Written in em as well, so a line grows with its text, also when
// the phone's font size is turned up.
//
// A label, a title and a row's main line stand on a line 1.26 times the text size. That is the
// line the screens were laid out on (it was Sora's own, 2026-10-06), and it is kept with the
// brand's typefaces so that no tile changed its height with them: Archivo by itself asks for
// 1.09, less than this, so its letters stand in the middle of the line with air above and below,
// and nothing is cut off (TypefaceTest reads the file's own line and holds this one to at least
// that). The number is written out, and not left for Android to work out, because a text style
// that names no line height takes over the one of whatever it stands in (a dialog, a button),
// and the same row would be higher in one place than in another.
internal val LabelLine: TextUnit = 1.26.em

// A sentence that runs over several lines needs more air between them than a label does. The
// design gives its 13 sp sentences 1.4 and its 12 sp sentences 1.5.
private val SentenceLine = 1.4.em
private val NoteLine = 1.5.em

// The three largest figures stand on a line exactly as high as the figure, as drawn. A digit
// has nothing that hangs under the line it stands on and is lower than the line is high, so
// nothing of it is cut off. It suits one line of digits, not a sentence.
private val FigureLine = 1.em

// What it takes for a line of text to really be lower than its typeface asks for. Told only the
// line height, Compose keeps the room above the first line and under the last one as the
// typeface has it, so that no letter is cut off there: a figure of one line then stands in a box
// as high as its typeface's own line (1.3 times its size for IBM Plex Mono; 1.26 with Sora,
// measured on an emulator, 2026-10-07, on Setup's count), whatever its line height says. The
// mode "Tight", with the trimming of both ends, takes that room away as well. Only the smallest
// of the three largest figures asks for it today, since Setup was laid out. The other two say a
// line of 1 and still take up the typeface's line. The middle one is held to its figure's
// height where it is used (`FigureText`); the largest was on Home's tile of a trip being
// recorded until 2026-10-10 and is on no screen of the app's own now.
private val TightLine =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
        mode = LineHeightStyle.Mode.Tight,
    )

private fun tight(style: TextStyle): TextStyle = style.copy(lineHeightStyle = TightLine)

private fun style(
    size: Int,
    weight: FontWeight,
    line: TextUnit = LabelLine,
    tracking: TextUnit = NoTracking,
    family: FontFamily = MiloFontFamily,
) = Base.copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line,
    letterSpacing = tracking,
)

/** A headline: Archivo, extra bold and widened. */
private fun headline(size: Int, tracking: TextUnit = HeadlineTracking) =
    style(size, HeadlineWeight, tracking = tracking, family = MiloHeadlineFontFamily)

/** A readout: a figure that stands by itself, in IBM Plex Mono. */
private fun readout(
    size: Int,
    weight: FontWeight = FontWeight.SemiBold,
    line: TextUnit = LabelLine,
) = style(
    size = size,
    weight = weight,
    line = line,
    tracking = if (size >= LARGE_READOUT_FROM_SP) LargeReadoutTracking else NoTracking,
    family = MiloReadoutFontFamily,
)

/**
 * The app's text styles in Material's fifteen roles. Screens pick a role
 * (`MaterialTheme.typography.bodyLarge`); sizes and weights are only ever changed here. The
 * sizes are the first design's (2026-10-06), in sp, so they grow with the phone's font size
 * setting; the typefaces are the brand's (2026-10-13).
 *
 * **Which typeface a role is set in.** The five that only ever hold a figure are readouts, in
 * IBM Plex Mono: the three `display` roles, `headlineLarge` and `headlineSmall`. The two that
 * are headlines, `headlineMedium` and `titleLarge`, are Archivo extra bold and widened. The
 * other eight are running text, in Archivo.
 *
 * Which role is which part of the design:
 * - `displayLarge`, `displayMedium`, `displaySmall`: the three largest figures (in the design
 *   the kilometres of the trip being recorded, a month's total, the count on Setup). Their line
 *   is as high as the figure itself, as drawn, so they are for one line of digits. Trips' month
 *   and, since 2026-10-10, Home's trip being recorded use the middle one, and Setup the
 *   smallest. The largest is on no screen of the app's own since Home's tile was made lower
 *   that day; Material's own clock dialog uses it, for its two large numbers.
 * - `headlineLarge` and `headlineSmall`: a figure in a tile, larger and smaller (Material's
 *   calendar also sets the chosen date at its top in the larger). The smaller is also the
 *   digit on a wheel of the odometer.
 *   `headlineMedium`: a large heading (the month on Trips, the first start's first sentence),
 *   and the few words of a hero tile.
 * - `titleLarge`: the title of a screen opened from another, and of a dialog.
 *   `titleMedium`: a value that is chosen (a stepper's value, a time). What is typed has a
 *   style of its own, `fieldText` below.
 *   `titleSmall`: a tile's own title. (Until 2026-10-13 also the figure at the end of a row,
 *   which is a readout now: `listFigure` below.)
 * - `bodyLarge`: the main line of a row. `bodyMedium`: the quieter second line, a tile's
 *   label, a note. `bodySmall`: a caption, such as the label above a text field.
 * - `labelLarge`: the words on a small button or a link, which is also what Material gives
 *   its own buttons. `labelMedium`: a chip, a button inside a row, in the design a pill, and
 *   the vehicle's name at the end of the first line of the truck's tile.
 *   `labelSmall`: a caption that has to stand out (nothing uses it today).
 *
 * **Why the main line of a row is semi-bold.** The screens set a row's main line in `bodyLarge`
 * and its second line in `bodyMedium`, so those two roles carry the design's 14 at weight 600
 * and 12 at weight 400, and the rows land on the design without a screen being edited. The
 * price: everything else the screens set in `bodyLarge` is semi-bold too, a plain note such as
 * "Reading…" included, until the screens themselves are restyled.
 *
 * A unit after a figure ("km") is set smaller than the figure and, on a tile, in the colour of
 * secondary text. It has no style of its own, because the design's units are sizes that exist:
 * 18, 16, 14 and 13, each at weight 600 like its figure, which are `rowFigure` (below),
 * `titleMedium`, `bodyLarge` and `labelLarge`. `FigureText` sets a unit in its figure's
 * typeface, as the brand's design writes "km" after a readout.
 *
 * **Line heights** are the first design's (the values are above `style`). Most text stands at
 * the label's line. The three largest figures stand on a line as high as themselves. `bodyMedium` has
 * the design's 1.5 for a sentence at 12, because that role is what the screens set their notes
 * and explaining sentences in; the price is that a second line of one line is about 3 sp
 * higher than drawn.
 */
internal val MiloTypography: Typography =
    Typography(
        displayLarge = readout(56, line = FigureLine),
        displayMedium = readout(44, line = FigureLine),
        displaySmall = tight(readout(36, line = FigureLine)),
        headlineLarge = readout(30),
        headlineMedium = headline(26),
        headlineSmall = readout(26),
        titleLarge = headline(22),
        titleMedium = style(16, FontWeight.SemiBold),
        titleSmall = style(15, FontWeight.SemiBold),
        bodyLarge = style(14, FontWeight.SemiBold),
        bodyMedium = style(12, FontWeight.Normal, NoteLine),
        bodySmall = style(11, FontWeight.Normal),
        labelLarge = style(13, FontWeight.SemiBold),
        labelMedium = style(12, FontWeight.SemiBold),
        labelSmall = style(11, FontWeight.SemiBold),
    )

/**
 * The design's text styles that Material's fifteen roles have no room for. Reach them through
 * `MiloTheme.textStyles`.
 *
 * **In use today:** [mainButton], [fieldText], [sentence], since Home was laid out as the
 * design draws it, [appName], [rowFigure], [tileLabel] and [accentNote], since Setup and the
 * Log were, [countWords], [logTime] and [logTag], since Settings was, [spanFigure], and since
 * the brand's typefaces (2026-10-13), [smallTileFigure] and [listFigure]. One, [sideFigure],
 * was Home's until 2026-10-10 and waits again. (Until 2026-10-13 there was one more,
 * `markLetter`, for the letter on the app's mark. The mark's letter is a drawn shape now.)
 *
 * **Which typeface.** [appName] is a headline. The readouts are [listFigure], [sideFigure],
 * [tileFigure], [fieldFigure], [logTime] and [logTag]. The others are running text.
 *
 * Two more came with the Trips screen's own layout, and are in use: [quietLabel] and
 * [standInWords].
 *
 * Two more came with the Report screen's and the edit screen's own layouts, and are in use:
 * [tileFigure] and [fieldFigure].
 *
 * @param mainButton the words on the one main button of a screen.
 * @param fieldText what is typed into a text field.
 * @param sentence a sentence that stands by itself in a tile or a dialog, a little larger and
 * firmer than a note. Also the line under the figures of the tile for the trip being recorded.
 * @param quietLabel the label of a switch that stands on the page, between two tiles: small and
 * plain, and set in the grey of secondary text.
 * @param standInWords the words that stand where an address is missing, inside the line that
 * says where a trip went ("looking up the address…"): lighter than the address beside them.
 * @param appName the app's name at the start of every screen's top line, and the screen's
 * name at its end: the design's wordmark, as its drawing of the interface sets it.
 * @param smallTileFigure the figure of a small tile that is a short sentence ("2.9 km · 2
 * trips"): running text, as large as the app's name. Until 2026-10-13 it was set in [appName].
 * @param listFigure the figure at the end of a row of a list: a trip's kilometres.
 * @param rowFigure a figure or a status word that stands alone at the end of a tile's row. In
 * the design also the unit after the largest figure, which no screen shows since 2026-10-10.
 * @param sideFigure a second figure beside a large one: in the design the minutes beside the
 * kilometres of the trip being recorded. It waits again since 2026-10-10: on the low tile the
 * owner chose that day, the minutes are a small line under the time the trip started.
 * @param countWords the words after a large count, which say what it counts: "of 14 ready" on
 * Setup. Lighter than a unit after a figure, as drawn.
 * @param spanFigure a span written out in a tile, such as the hours of a work day.
 * @param tileLabel a tile's small grey label, and the quieter line under a tile's title. The
 * same size as `bodyMedium`, but at the label's line, as the design draws a label: `bodyMedium`
 * keeps the taller line of a sentence that runs over several lines, and a tile set in it
 * would be a few dp higher than drawn for every label it holds.
 * @param accentNote the small lines on an accent tile: under its title, and under a figure.
 * @param logTime the time of a line of the event log, in monospace so that times line up.
 * @param logTag the tag that names a line's kind in the event log, and the kind of a change
 * on the What's new screen: semi-bold, as the brand's design draws a small tag.
 * @param tileFigure the figure of a tile that holds a line under it as well: the kilometres on
 * the Report screen.
 * @param fieldFigure a figure that is typed into a field of its own: the distance on the edit
 * screen.
 */
@Immutable
data class MiloTextStyles(
    val mainButton: TextStyle = style(16, FontWeight.Bold),
    val fieldText: TextStyle = style(14, FontWeight.Normal),
    val sentence: TextStyle = style(13, FontWeight.Medium, SentenceLine),
    val quietLabel: TextStyle = style(13, FontWeight.Normal),
    val standInWords: TextStyle = style(14, FontWeight.Medium),
    val appName: TextStyle = headline(17, NameTracking),
    val smallTileFigure: TextStyle = style(17, FontWeight.SemiBold),
    val listFigure: TextStyle = readout(15),
    val rowFigure: TextStyle = style(18, FontWeight.SemiBold),
    val sideFigure: TextStyle = readout(20),
    val countWords: TextStyle = style(16, FontWeight.Medium),
    val spanFigure: TextStyle = style(22, FontWeight.SemiBold, tracking = HeadlineTracking),
    val tileLabel: TextStyle = style(12, FontWeight.Normal),
    val accentNote: TextStyle = style(12, FontWeight.Medium),
    val logTime: TextStyle = readout(11, FontWeight.Medium),
    val logTag: TextStyle = readout(10),
    val tileFigure: TextStyle = readout(28),
    val fieldFigure: TextStyle = readout(20),
)
