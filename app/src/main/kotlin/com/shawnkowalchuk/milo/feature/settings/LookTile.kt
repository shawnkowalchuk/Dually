package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Segment
import com.shawnkowalchuk.milo.core.designsystem.component.SegmentedChoice
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.AppLook

/**
 * "Look": the screens dark or light (since 2026-11-12, Shawn: "is it possible to do a light
 * version?", and his answer "Light/Dark switch only"). Drawn exactly as [UnitsTile] above it:
 * a fold, the segmented control and a quiet sentence. Nothing was added to the design system.
 *
 * @param look the look in force.
 * @param onLook stores the other one. The screens and the widget follow it at once.
 */
@Composable
internal fun LookTile(look: AppLook, onLook: (AppLook) -> Unit) {
    val dark = stringResource(R.string.settings_look_dark)
    val light = stringResource(R.string.settings_look_light)
    SettingsFold(
        title = stringResource(R.string.settings_look_title),
        summary = if (look == AppLook.LIGHT) light else dark,
        gap = MiloTheme.spacing.tileGap,
    ) {
        SegmentedChoice(
            segments =
                listOf(
                    Segment(
                        label = dark,
                        selected = look == AppLook.DARK,
                        onSelect = { onLook(AppLook.DARK) },
                    ),
                    Segment(
                        label = light,
                        selected = look == AppLook.LIGHT,
                        onSelect = { onLook(AppLook.LIGHT) },
                    ),
                ),
        )
        Note(stringResource(R.string.settings_look_detail))
    }
}
