package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// This file is where the app's colour values are written.
// Screens and components ask for a colour by its role (MaterialTheme.colorScheme.primary,
// MiloTheme.colors.danger, MiloTheme.statusColors.ok), never by its value, so a colour change
// happens here once.
//
// The colours are the brand's since 2026-10-13, when the app became Dually (ADR-010): an orange
// accent on a warm near-black, in place of the lime on a cool near-black the first design drew
// (2026-10-06). The app still has one look, and it is dark: neither design has a light variant
// for the screens, so there is no second scheme and the phone's light or dark setting changes
// nothing (FINDINGS_LOG, 2026-10-06 and 2026-10-13).
//
// There are six exceptions, each explained where it is written:
//   - the app's mark outside Compose: the launcher icon (res/drawable/ic_launcher_*.xml) and
//     the widget's mark (res/drawable/widget_mark.xml), which the home screen app draws. Their
//     two colours are in res/values/colors.xml (milo_mark_ground, milo_mark_letter) and repeat
//     the mark's pair below; a unit test (AppMarkTest) keeps them equal;
//   - the notification icon (res/drawable/ic_stat_trip.xml), which Android draws itself, outside
//     Compose, and tints as it likes. The notifications' colour, which Android paints around
//     that icon in the shade, is in res/values/colors.xml: it repeats the accent, and a unit
//     test (NotificationAccentTest) keeps the two equal;
//   - the window's background (res/values/colors.xml, used by res/values/themes.xml), which
//     Android paints before Compose has drawn anything. It repeats the page colour below so that
//     a launch never flashes white, and a unit test (WindowBackgroundTest) keeps the two equal;
//   - the home-screen widget's colours (res/values/colors.xml, milo_widget_*), which the home
//     screen app draws outside Compose. They repeat the tile, the two text colours, the accent,
//     the page colour and the control fill, and a unit test (WidgetColorsTest) keeps them equal;
//   - the report's inks (core/report/ReportPage.kt, ReportInk), because the PDF is drawn outside
//     Compose; a unit test (ReportInkTest) keeps the ones that are the theme's equal;
//   - the placeholder stroke in component/MiloIcons.kt, which Icon() always replaces with a tint.

// The brand's colours, by the names its design gives them. Each value is written once, here,
// and given to one or more roles below.
//
// Hi-Vis Orange: the accent. Words on it are dark, never white: white on this orange is
// 3.1 to 1, the page's dark is 6.0 to 1.
private val Accent = Color(0xFFFF5A12)

// Amber: for what is to stand out without being the accent. In the app that is whatever is
// waiting for the user, and the small tag of a fixed fault.
private val Amber = Color(0xFFFFB648)

// Asphalt: the page. Bone: the words on it.
private val Page = Color(0xFF15120F)
private val TextMain = Color(0xFFFFF4E8)

// The dark of the letter on the app's mark, a shade lighter than the page, as drawn.
private val MarkLetter = Color(0xFF1E1A17)

// The brand has two more colours, and the app's screens use neither: Ember (B33F0D, for shading
// and for a pressed orange ground: darker than the accent, so as words on a dark tile it is
// 2.9 to 1 and cannot be a pressed link) and Redline (FF3D2E, for a gauge's red zone only). No
// screen has a gauge. Redline is never a button, an error or a warning: what is wrong is said in
// the lighter, pinker red below, which cannot be taken for the accent or for Redline.

// The dark surfaces, from the design's drawings of the interface: a card, a control or a line
// on a card, and the deepest well.
private val Tile = Color(0xFF221D18)
private val ControlFill = Color(0xFF2E2721)
private val FieldFill = Color(0xFF0D0B09)
private val IdleOutline = Color(0xFF4A3B2E)

// The design's quieter words on dark, and a softer shade of Bone for the event log's running
// text (in the design it is the hairline on a light ground; no value was made up for it).
private val TextSecondary = Color(0xFFCDBFAF)
private val TextLog = Color(0xFFE3D3C0)

// The design's quieter words on a light ground. On dark it is the grey of an idle icon.
private val IdleIcon = Color(0xFF6B5A4C)

// Three values the design does not draw, each made for one need and kept as close to the
// drawn ones as that need allows.
//
// A fill that should hardly be noticed, between a card and a control on it.
private val QuietFill = Color(0xFF28221C)

// The "to confirm" ring, Material's outline and the line around a text field: an outline that
// has to be seen to be understood. The idle icon's grey is 2.5 to 1 on a card, under the 3 to 1
// such a mark needs, so this is that grey made just light enough: 3.1 to 1 on a card, and more
// on the page and inside a field. With a field that is empty, nothing but its small label
// would otherwise show that there is a place to type in.
private val Ring = Color(0xFF7A6858)

// The dark ground of the tile for something that is waiting: Amber at about a sixth over the
// page, so that the tile is told apart from a plain card by more than its words.
private val AttentionFill = Color(0xFF3A2C17)

// What Material lays over the screen behind a sheet. The design has no such layer; black dims
// a dark page without tinting it.
private val Scrim = Color(0xFF000000)

// The accent while a link is pressed: lighter, as the design's links on dark are when a
// pointer is over them. It is Amber, which is on the screen for a moment only, under a finger.
private val AccentPressed = Amber

// The light that runs along the line between the phone and the truck while the two are
// connected: Bone at 85 percent.
private val Glint = Color(0xD9FFF4E8)

private val AttentionText = Amber
private val AttentionSecondaryText = TextSecondary

// The reds and the two cool tags are the first design's, kept on purpose (2026-10-13): the
// brand draws no colour for "wrong", and its one red is for gauges. Beside an orange accent
// these still read as what they are: a light, pinkish red for what is wrong or takes something
// away, a blue for Bluetooth, a violet for Android Auto.
private val DangerFill = Color(0xFF2A1719)
private val DangerText = Color(0xFFFF9C9C)
private val ErrorTagFill = Color(0xFF3A1C1F)
private val ErrorTagText = Color(0xFFFFB3B3)

private val BluetoothTagFill = Color(0xFF2A3350)
private val BluetoothTagText = Color(0xFFC9D6FF)
private val AndroidAutoTagFill = Color(0xFF33294A)
private val AndroidAutoTagText = Color(0xFFDCCBFF)

/**
 * Material's colour roles, given the design's colours, so that a stock Material component lands
 * on the design without being told a colour: a `Button` is the accent with dark words, a
 * `TextButton` is accent words, a `HorizontalDivider` is the design's divider line, a dialog is
 * a tile, the time picker's dial is a control on a tile.
 *
 * Every role is set explicitly. A role left out falls back to Material's purple baseline, which
 * then shows up unannounced the first time a component uses that role. Where the design has no
 * colour for a role, the role gets the nearest colour the design does have, never a new one.
 */
internal val MiloColorScheme: ColorScheme =
    darkColorScheme(
        // The accent: the main button, a link, the chosen radio button, a progress bar's fill,
        // the clock hand and the chosen day in the pickers.
        primary = Accent,
        onPrimary = Page,
        // The chosen hour or minute in the time picker. In the design whatever is chosen is the
        // accent itself, so the "container" is not a paler accent.
        primaryContainer = Accent,
        onPrimaryContainer = Page,
        // For an action on a light bar (a snackbar). The design has none; dark words read there.
        inversePrimary = Page,
        // The light fill with dark words: the chosen filter chip.
        secondary = TextMain,
        onSecondary = Page,
        // A control sitting on a tile: a secondary button, a progress bar's track.
        secondaryContainer = ControlFill,
        onSecondaryContainer = TextMain,
        // The "something is waiting for you" tile and its button: Amber on a dark amber ground.
        tertiary = AttentionText,
        onTertiary = AttentionFill,
        tertiaryContainer = AttentionFill,
        onTertiaryContainer = AttentionText,
        background = Page,
        onBackground = TextMain,
        // A bare Surface is the page. A tile asks for surfaceContainer.
        surface = Page,
        onSurface = TextMain,
        surfaceVariant = ControlFill,
        // Secondary text: the quieter second line of a row, a tile's label, an idle icon.
        onSurfaceVariant = TextSecondary,
        // Material mixes this into a surface that is "raised". The design is flat, so the only
        // thing raising may ever do is move the page colour towards the tile colour.
        surfaceTint = Tile,
        inverseSurface = TextMain,
        inverseOnSurface = Page,
        error = DangerText,
        onError = Page,
        errorContainer = DangerFill,
        onErrorContainer = DangerText,
        // An outline that has to be seen to be understood (the ring of an unchosen control).
        outline = Ring,
        // A line that only separates: the divider between two rows of a tile.
        outlineVariant = ControlFill,
        scrim = Scrim,
        surfaceBright = ControlFill,
        surfaceDim = Page,
        // From the well a text field is typed into, up to a control on a tile. The three in the
        // middle are all the tile: Material uses them for bars, menus, sheets and dialogs, and
        // in this design each of those is a tile.
        surfaceContainerLowest = FieldFill,
        surfaceContainerLow = Tile,
        surfaceContainer = Tile,
        surfaceContainerHigh = Tile,
        surfaceContainerHighest = ControlFill,
        // The "fixed" roles are meant to be the same in a light and a dark scheme. The app has
        // one scheme, so they repeat the pairs above; no component it uses reads them.
        primaryFixed = AccentPressed,
        primaryFixedDim = Accent,
        onPrimaryFixed = Page,
        onPrimaryFixedVariant = Page,
        secondaryFixed = TextMain,
        secondaryFixedDim = TextLog,
        onSecondaryFixed = Page,
        onSecondaryFixedVariant = Page,
        tertiaryFixed = AttentionText,
        tertiaryFixedDim = AttentionSecondaryText,
        onTertiaryFixed = AttentionFill,
        onTertiaryFixedVariant = AttentionFill,
    )

/**
 * The colours behind every "is this working?" dot in the app.
 *
 * Material has a role for "error" but none for "all good" or "your turn", so the set lives here
 * rather than borrowing unrelated Material roles. Reach it through `MiloTheme.statusColors`.
 *
 * The four states never differ by colour alone: each dot has its own mark as well (a tick, an
 * exclamation mark, a question mark, or an empty ring), for someone who cannot tell the colours
 * apart, and for sunlight that washes them out.
 *
 * @param ok met: the accent.
 * @param problem not met: the design's red.
 * @param toConfirm the user has to do or confirm something MilO cannot check. An empty ring, so
 * this is a line's colour and not a fill's.
 * @param unknown MilO could not find out. Deliberately colourless: the grey of secondary text.
 * @param onDot the mark drawn on a filled dot.
 */
@Immutable
data class MiloStatusColors(
    val ok: Color,
    val problem: Color,
    val toConfirm: Color,
    val unknown: Color,
    val onDot: Color,
)

internal val MiloStatus =
    MiloStatusColors(
        ok = Accent,
        problem = DangerText,
        toConfirm = Ring,
        unknown = TextSecondary,
        onDot = Page,
    )

/** A fill and the colour of what is written or drawn on it. The two are only ever used together. */
@Immutable
data class FillAndText(val fill: Color, val text: Color)

/**
 * The fill and text of the small tag that names a line's kind on the Log screen. Each kind has
 * its own pair so that a column of lines can be scanned by colour.
 *
 * In use since the Log was laid out as the design draws it: the `Tag` on each of its lines.
 *
 * @param service also the pair for any kind that has no colour of its own.
 */
@Immutable
data class MiloLogTagColors(
    val trip: FillAndText,
    val bluetooth: FillAndText,
    val androidAuto: FillAndText,
    val service: FillAndText,
    val error: FillAndText,
)

/**
 * The fill and text of the small tag that names the kind of a change on the What's new screen
 * (since 2026-10-08): GopherForms' four kinds, in its order of colours (blue for new there,
 * teal, amber, red), taken from the colours the theme already has. No design draws such a
 * list, so no colour was added for it. Since 2026-10-13 "Fixed" is Amber with dark words, as
 * the brand's design draws a small tag, and "New" is the orange accent.
 */
@Immutable
data class MiloChangeTagColors(
    val new: FillAndText,
    val improved: FillAndText,
    val fixed: FillAndText,
    val security: FillAndText,
)

/**
 * The fill and digit of the cells of a row of wheels (since 2026-10-10, Home's odometer, which
 * the owner chose from three drawings). The drawing has the dark cells and one in the accent.
 * No colour was added for either.
 *
 * @param whole a digit of the figure: a window in the page's colour, darker than the tile it is
 * cut into, with a light digit.
 * @param counting the last digit's cell while a trip is counted and its wheel turns: the accent
 * with a dark digit.
 */
@Immutable
data class MiloWheelColors(val whole: FillAndText, val counting: FillAndText)

/**
 * The design's colours that Material has no role for, or no role with a name that says what
 * they are for. Reach them through `MiloTheme.colors`.
 *
 * **In use today:** [control], [fieldFill], [fieldBorder], [chipSelected] and [chip], and since
 * Home was laid out as the design draws it, [quietFill], [idleOutline], [idleIcon], [linkGlint]
 * and the amber tile's three ([attentionTile], [attentionSecondaryText], [attentionButton]),
 * since Trips was, [danger], since the Log was, [logText] and [logTags], since the What's
 * new screen (2026-10-08), [changeTags], since Home's odometer became a row of wheels
 * (2026-10-10), [wheels], since the Report screen's layout, [accentPressed], and since the
 * app's mark became a drawn letter (2026-10-13), [mark].
 *
 * @param control a control sitting on a tile: a small button, a stepper's button, a switch's
 * track when it is off.
 * @param quietFill a fill that should hardly be noticed: the circle behind an idle icon.
 * @param fieldFill the inside of a text field, darker than the tile it sits on.
 * @param fieldBorder the hairline around a text field while it is neither typed in nor wrong.
 * Not the design's value, which could not be seen on a tile: the grey of an outline that has to
 * be seen (the comment at the palette above says why).
 * @param idleOutline a dashed or idle outline: decoration, not something to be read.
 * @param idleIcon an icon that is idle, beside something that is switched off. Too faint to be
 * the only sign of a button that cannot be pressed (2.2 to 1 on a control), which is why
 * `SquareIconButton` greys its icon with the colour of secondary text instead. The one place it
 * greys a button is the title's "next" square on the page, as drawn on Trips: there it is
 * 2.8 to 1, and a screen reader is told that the button is switched off.
 * @param logText running text in the event log, a little softer than the main text.
 * @param accentPressed the accent while a link is pressed: lighter than the accent.
 * @param linkGlint the light that runs along the line between the phone and the truck while
 * they are connected. Decoration: nothing is read from it.
 * @param attentionTile the tile for something that is waiting for the user: a dark amber
 * ground, and its title in Amber.
 * @param attentionSecondaryText the quieter line on that tile.
 * @param attentionButton the button on that tile.
 * @param danger an action that takes something away ("Delete").
 * @param chipSelected the filter chip that is in force.
 * @param chip every other filter chip. It is the tile's colour: chips stand on the page.
 * @param changeTags the tags of the What's new screen: see [MiloChangeTagColors].
 * @param wheels the cells of a row of wheels: see [MiloWheelColors].
 * @param mark the app's mark: the accent's square and the dark of the letter on it. The
 * launcher icon and the widget's mark repeat the pair in XML (the top of this file says where).
 */
@Immutable
data class MiloColors(
    val control: FillAndText,
    val quietFill: Color,
    val fieldFill: Color,
    val fieldBorder: Color,
    val idleOutline: Color,
    val idleIcon: Color,
    val logText: Color,
    val accentPressed: Color,
    val linkGlint: Color,
    val attentionTile: FillAndText,
    val attentionSecondaryText: Color,
    val attentionButton: FillAndText,
    val danger: FillAndText,
    val chipSelected: FillAndText,
    val chip: FillAndText,
    val logTags: MiloLogTagColors,
    val changeTags: MiloChangeTagColors,
    val wheels: MiloWheelColors,
    val mark: FillAndText,
)

internal val MiloExtraColors =
    MiloColors(
        control = FillAndText(fill = ControlFill, text = TextMain),
        quietFill = QuietFill,
        fieldFill = FieldFill,
        fieldBorder = Ring,
        idleOutline = IdleOutline,
        idleIcon = IdleIcon,
        logText = TextLog,
        accentPressed = AccentPressed,
        linkGlint = Glint,
        attentionTile = FillAndText(fill = AttentionFill, text = AttentionText),
        attentionSecondaryText = AttentionSecondaryText,
        attentionButton = FillAndText(fill = AttentionText, text = AttentionFill),
        danger = FillAndText(fill = DangerFill, text = DangerText),
        chipSelected = FillAndText(fill = TextMain, text = Page),
        chip = FillAndText(fill = Tile, text = TextMain),
        logTags =
            MiloLogTagColors(
                trip = FillAndText(fill = Accent, text = Page),
                bluetooth = FillAndText(fill = BluetoothTagFill, text = BluetoothTagText),
                androidAuto = FillAndText(fill = AndroidAutoTagFill, text = AndroidAutoTagText),
                service = FillAndText(fill = ControlFill, text = TextLog),
                error = FillAndText(fill = ErrorTagFill, text = ErrorTagText),
            ),
        changeTags =
            MiloChangeTagColors(
                new = FillAndText(fill = Accent, text = Page),
                improved = FillAndText(fill = BluetoothTagFill, text = BluetoothTagText),
                fixed = FillAndText(fill = Amber, text = Page),
                security = FillAndText(fill = ErrorTagFill, text = ErrorTagText),
            ),
        wheels =
            MiloWheelColors(
                whole = FillAndText(fill = Page, text = TextMain),
                counting = FillAndText(fill = Accent, text = Page),
            ),
        mark = FillAndText(fill = Accent, text = MarkLetter),
    )
