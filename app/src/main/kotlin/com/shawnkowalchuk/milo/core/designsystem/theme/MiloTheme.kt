package com.shawnkowalchuk.milo.core.designsystem.theme

import android.graphics.Color.TRANSPARENT
import androidx.activity.SystemBarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.shawnkowalchuk.milo.core.util.AppLook

// Spacing, shapes and the extra text styles are the same wherever they are asked for, so a
// default is safe and lets a component be previewed on its own.
private val LocalMiloSpacing = staticCompositionLocalOf { MiloSpacing() }
private val LocalMiloShapes = staticCompositionLocalOf { MiloShapes() }
private val LocalMiloTextStyles = staticCompositionLocalOf { MiloTextStyles() }

// The colours have no default on purpose. A screen that forgot its theme would draw MilO's own
// colours beside Material's purple baseline for everything else, and nobody would notice before
// it is on the phone, so this fails loudly instead.
private val LocalMiloColors =
    staticCompositionLocalOf<MiloColors> {
        error("No MiloTheme found. Wrap the screen or preview in MiloTheme { }.")
    }
private val LocalMiloStatusColors =
    staticCompositionLocalOf<MiloStatusColors> {
        error("No MiloTheme found. Wrap the screen or preview in MiloTheme { }.")
    }
private val LocalMiloChartColors =
    staticCompositionLocalOf<ChartColors> {
        error("No MiloTheme found. Wrap the screen or preview in MiloTheme { }.")
    }

/** The palette of [look]. */
fun miloPalette(look: AppLook): MiloPalette =
    if (look == AppLook.LIGHT) MiloLightPalette else MiloDarkPalette

/**
 * The app's theme. Every screen and every preview is wrapped in it exactly once.
 *
 * **Two looks, chosen in Settings** (since 2026-11-12): dark, as the design the owner chose on
 * 2026-10-06 and rebranded on 2026-10-13 draws it, and light, the brand's light page that the
 * report already uses, approved from a mock-up. It does not follow the phone's light or dark
 * setting (his choice). Material You wallpaper colours are not used either: the status dots
 * have to mean the same thing on every screen, and a wallpaper-derived palette can sit too
 * close to any of them.
 *
 * @param look the look chosen in Settings. Dark for a preview that names none.
 */
@Composable
fun MiloTheme(look: AppLook = AppLook.DARK, content: @Composable () -> Unit) {
    val palette = miloPalette(look)
    CompositionLocalProvider(
        LocalMiloColors provides palette.colors,
        LocalMiloStatusColors provides palette.status,
        LocalMiloChartColors provides palette.chart,
    ) {
        MaterialTheme(
            colorScheme = palette.scheme,
            typography = MiloTypography,
            shapes = MiloMaterialShapes,
            content = content,
        )
    }
}

/**
 * Access to the tokens Material does not have a slot for. What Material does cover (its colour
 * roles and its fifteen text styles) is read from `MaterialTheme` as usual.
 */
object MiloTheme {
    val spacing: MiloSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloSpacing.current

    val shapes: MiloShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloShapes.current

    val textStyles: MiloTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloTextStyles.current

    val colors: MiloColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloColors.current

    val statusColors: MiloStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloStatusColors.current

    /** The chart's lines and grid, in the look in force (since 2026-11-12). */
    val chartColors: ChartColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloChartColors.current
}

/**
 * How Android's own two bars are drawn over Dually, at the top (clock, battery) and at the bottom
 * (the gesture line or the three buttons): see-through, with icons that suit the look chosen in
 * Settings, light icons on the dark look and dark ones on the light look.
 *
 * Left to itself Android picks the icons by the phone's light or dark setting, which says
 * nothing about Dually's look, so the bars are told. `MainActivity` hands this to
 * `enableEdgeToEdge` for both bars, again whenever the look changes.
 */
fun miloSystemBarStyle(look: AppLook): SystemBarStyle = if (look == AppLook.LIGHT) {
    SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
} else {
    SystemBarStyle.dark(TRANSPARENT)
}
