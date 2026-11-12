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
 * The Cloud backup tile, above the one for backup, export and import (ADR-012, ADR-013). Drawn
 * like the tiles beside it: a fold with a one-line summary, notes, and a row of buttons.
 *
 * TODO(debt): Google's branding rules ask for their "G" mark on a "Sign in with Google" button.
 * This uses Dually's own button with Google's words; the tile is finished in slice 1e
 * (FINDINGS_LOG, 2026-11-11).
 */
@Composable
internal fun CloudBackupTile(viewModel: CloudBackupViewModel<Context>) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val account = state.account
    val idle = state.read && !state.working
    SettingsFold(
        title = stringResource(R.string.settings_cloud_title),
        summary =
            when {
                !state.available -> stringResource(R.string.settings_cloud_line_unavailable)
                account == null -> stringResource(R.string.settings_cloud_line_off)
                account.unlocked -> stringResource(R.string.settings_cloud_line_on, account.email)
                else -> stringResource(R.string.settings_cloud_line_signed_in, account.email)
            },
        unfinished = state.working || state.note != null,
    ) {
        val signOut =
            ActionButton(
                label = stringResource(R.string.settings_cloud_sign_out),
                kind = ActionKind.PLAIN,
                onClick = { viewModel.onSignOut(context) },
                enabled = idle,
            )
        when {
            !state.available -> Note(stringResource(R.string.settings_cloud_unavailable))

            account == null -> {
                Note(stringResource(R.string.settings_cloud_explained))
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label = stringResource(R.string.settings_cloud_sign_in),
                                kind = ActionKind.ACCENT,
                                onClick = { viewModel.onSignIn(context) },
                                enabled = idle,
                            ),
                        ),
                )
            }

            account.unlocked -> {
                Note(stringResource(R.string.settings_cloud_signed_in, account.email))
                ActionButtonRow(actions = listOf(signOut))
            }

            else -> {
                Note(stringResource(R.string.settings_cloud_locked, account.email))
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label =
                                    state.price?.let {
                                        stringResource(R.string.settings_cloud_unlock_price, it)
                                    } ?: stringResource(R.string.settings_cloud_unlock),
                                kind = ActionKind.ACCENT,
                                onClick = { viewModel.onUnlock(context) },
                                enabled = idle,
                            ),
                            ActionButton(
                                label = stringResource(R.string.settings_cloud_restore),
                                kind = ActionKind.PLAIN,
                                onClick = viewModel::onRestore,
                                enabled = idle,
                            ),
                        ),
                )
                ActionButtonRow(actions = listOf(signOut))
            }
        }
        state.note?.let { CloudNoteRow(it) }
    }
}

@Composable
private fun CloudNoteRow(note: CloudNote) {
    val (text, status) =
        when (note) {
            CloudNote.UNLOCKED -> R.string.settings_cloud_unlocked to RowStatus.OK

            CloudNote.NO_GOOGLE_ACCOUNT -> R.string.settings_cloud_no_account to RowStatus.PROBLEM

            CloudNote.OFFLINE -> R.string.settings_cloud_offline to RowStatus.PROBLEM

            CloudNote.SIGN_IN_FAILED -> R.string.settings_cloud_failed to RowStatus.PROBLEM

            CloudNote.PENDING -> R.string.settings_cloud_pending to RowStatus.PROBLEM

            CloudNote.NOT_OFFERED -> R.string.settings_cloud_not_offered to RowStatus.PROBLEM

            CloudNote.OTHER_ACCOUNT -> R.string.settings_cloud_other_account to RowStatus.PROBLEM

            CloudNote.REFUNDED -> R.string.settings_cloud_refunded to RowStatus.PROBLEM

            CloudNote.NOTHING_TO_RESTORE ->
                R.string.settings_cloud_nothing_to_restore to
                    RowStatus.PROBLEM

            CloudNote.UNLOCK_FAILED -> R.string.settings_cloud_unlock_failed to RowStatus.PROBLEM
        }
    StatusRow(stringResource(text), status)
}
