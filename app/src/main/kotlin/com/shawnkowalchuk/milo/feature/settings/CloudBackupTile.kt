package com.shawnkowalchuk.milo.feature.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow

/**
 * The Cloud backup tile, above the one for backup, export and import (ADR-012). Drawn like the
 * tiles beside it: a fold with a one-line summary, notes, and a row of buttons.
 *
 * TODO(debt): Google's branding rules ask for their "G" mark on a "Sign in with Google" button.
 * This first slice uses Dually's own button with Google's words; the tile is finished in slice
 * 1e (FINDINGS_LOG, 2026-11-11).
 */
@Composable
internal fun CloudBackupTile(viewModel: CloudBackupViewModel<Context>) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val account = state.account
    SettingsFold(
        title = stringResource(R.string.settings_cloud_title),
        summary =
            when {
                !state.available -> stringResource(R.string.settings_cloud_line_unavailable)

                account != null -> stringResource(
                    R.string.settings_cloud_line_signed_in,
                    account.email,
                )

                else -> stringResource(R.string.settings_cloud_line_off)
            },
        unfinished = state.working || state.note != null,
    ) {
        when {
            !state.available -> Note(stringResource(R.string.settings_cloud_unavailable))

            account != null -> {
                Note(stringResource(R.string.settings_cloud_signed_in, account.email))
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label = stringResource(R.string.settings_cloud_sign_out),
                                kind = ActionKind.PLAIN,
                                onClick = { viewModel.onSignOut(context) },
                                enabled = !state.working,
                            ),
                        ),
                )
            }

            else -> {
                Note(stringResource(R.string.settings_cloud_explained))
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label = stringResource(R.string.settings_cloud_sign_in),
                                kind = ActionKind.ACCENT,
                                onClick = { viewModel.onSignIn(context) },
                                enabled = state.read && !state.working,
                            ),
                        ),
                )
            }
        }
        when (state.note) {
            null -> Unit

            CloudSignInNote.NO_GOOGLE_ACCOUNT ->
                StatusRow(stringResource(R.string.settings_cloud_no_account), RowStatus.PROBLEM)

            CloudSignInNote.OFFLINE ->
                StatusRow(stringResource(R.string.settings_cloud_offline), RowStatus.PROBLEM)

            CloudSignInNote.FAILED ->
                StatusRow(stringResource(R.string.settings_cloud_failed), RowStatus.PROBLEM)
        }
    }
}
