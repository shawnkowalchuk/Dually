package com.shawnkowalchuk.milo.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButton
import com.shawnkowalchuk.milo.core.designsystem.component.ActionButtonRow
import com.shawnkowalchuk.milo.core.designsystem.component.ActionKind
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.cloud.CloudStartResult

/**
 * The Online backup tile (ADR-012, ADR-013), on the main Settings screen and always open (since
 * 2026-11-12, Shawn: "the backup shouldnt be hidden. also it should probably state something
 * like purchase online backup"). Until then it was the "Cloud backup" fold on the Your data page,
 * and its first button was "Sign in with Google".
 *
 * Before the backup is bought, one sentence says what it is, and **Purchase online backup**,
 * with Google Play's price, does the whole of it: Google's account sheet first where nobody is
 * signed in, then Google Play's purchase sheet. **Restore purchase** beside it signs in the same
 * way. Bought, it says which account the trips go to, with Sign out. Since nothing here is a
 * Google-branded sign-in button any more (the sheet is Google's own), Google's "G" mark is not
 * needed.
 */
@Composable
internal fun CloudBackupTile(viewModel: CloudBackupViewModel<Context>) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val account = state.account
    val idle = state.read && !state.working
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileHeading(stringResource(R.string.settings_cloud_title))
        when {
            !state.available -> Note(stringResource(R.string.settings_cloud_unavailable))

            account?.unlocked == true -> UnlockedRows(viewModel, state, account.email, idle)

            else -> {
                Note(
                    if (account == null) {
                        stringResource(R.string.settings_cloud_explained)
                    } else {
                        stringResource(R.string.settings_cloud_locked, account.email)
                    },
                )
                ActionButtonRow(
                    actions =
                        listOf(
                            ActionButton(
                                label =
                                    state.price?.let {
                                        stringResource(R.string.settings_cloud_purchase_price, it)
                                    } ?: stringResource(R.string.settings_cloud_purchase),
                                kind = ActionKind.ACCENT,
                                onClick = { viewModel.onPurchase(context) },
                                enabled = idle,
                            ),
                        ),
                )
                ActionButtonRow(
                    actions =
                        buildList {
                            add(
                                ActionButton(
                                    label = stringResource(R.string.settings_cloud_restore),
                                    kind = ActionKind.PLAIN,
                                    onClick = { viewModel.onRestore(context) },
                                    enabled = idle,
                                ),
                            )
                            if (account != null) {
                                add(
                                    ActionButton(
                                        label = stringResource(R.string.settings_cloud_sign_out),
                                        kind = ActionKind.PLAIN,
                                        onClick = { viewModel.onSignOut(context) },
                                        enabled = idle,
                                    ),
                                )
                            }
                        },
                )
            }
        }
        state.note?.let { CloudNoteRow(it) }
    }
}

/**
 * The tile once the backup is bought: on, from this phone; or another phone's trips are in the
 * backup, and the user chooses Restore my trips here or Back up this phone instead (slice 1e,
 * 2026-11-12). Each choice that replaces something is asked first. Sign out and Delete my online
 * backup and account are always there.
 */
@Composable
private fun UnlockedRows(
    viewModel: CloudBackupViewModel<Context>,
    state: CloudBackupCardState,
    email: String,
    idle: Boolean,
) {
    val context = LocalContext.current
    var asking by rememberSaveable { mutableStateOf<CloudQuestion?>(null) }
    val merge = state.standing as? CloudStartResult.Merge
    if (merge == null) {
        Text(
            text = stringResource(R.string.settings_cloud_on),
            style = MaterialTheme.typography.titleSmall,
        )
        Note(stringResource(R.string.settings_cloud_signed_in, email))
    } else {
        // ADR-017: a phone with trips of its own asks once before it joins the logbook.
        Note(stringResource(R.string.settings_cloud_merge_question, email, merge.own, merge.others))
        ActionButtonRow(
            actions =
                listOf(
                    ActionButton(
                        label = stringResource(R.string.settings_cloud_merge),
                        kind = ActionKind.ACCENT,
                        onClick = { viewModel.onMerge() },
                        enabled = idle,
                    ),
                ),
        )
        ActionButtonRow(
            actions =
                listOf(
                    ActionButton(
                        label = stringResource(R.string.settings_cloud_use_backup),
                        kind = ActionKind.PLAIN,
                        onClick = { asking = CloudQuestion.USE_BACKUP },
                        enabled = idle,
                    ),
                ),
        )
    }
    ActionButtonRow(
        actions =
            listOf(
                ActionButton(
                    label = stringResource(R.string.settings_cloud_sign_out),
                    kind = ActionKind.PLAIN,
                    onClick = { viewModel.onSignOut(context) },
                    enabled = idle,
                ),
                ActionButton(
                    label = stringResource(R.string.settings_cloud_delete),
                    kind = ActionKind.DANGER,
                    onClick = { asking = CloudQuestion.DELETE },
                    enabled = idle,
                ),
            ),
    )
    asking?.let { question ->
        ConfirmDialog(
            title = stringResource(question.titleRes),
            text =
                when (question) {
                    CloudQuestion.DELETE, CloudQuestion.USE_BACKUP -> stringResource(
                        question.textRes,
                    )
                },
            confirmLabel = stringResource(question.confirmRes),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                asking = null
                when (question) {
                    CloudQuestion.USE_BACKUP -> viewModel.onUseBackupOnly()
                    CloudQuestion.DELETE -> viewModel.onDeleteAccount(context)
                }
            },
            onDismiss = { asking = null },
        )
    }
}

/** The two questions of the bought backup, each before something is replaced or deleted. */
private enum class CloudQuestion(val titleRes: Int, val textRes: Int, val confirmRes: Int) {
    USE_BACKUP(
        R.string.settings_cloud_use_backup_title,
        R.string.settings_cloud_use_backup_text,
        R.string.settings_cloud_use_backup_confirm,
    ),
    DELETE(
        R.string.settings_cloud_delete_title,
        R.string.settings_cloud_delete_text,
        R.string.settings_cloud_delete_confirm,
    ),
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

            CloudNote.PLAY_NOT_READY ->
                R.string.settings_cloud_play_not_ready to RowStatus.PROBLEM

            CloudNote.USING_BACKUP -> R.string.settings_cloud_using_backup to RowStatus.OK

            CloudNote.TRIP_IN_PROGRESS ->
                R.string.settings_cloud_trip_in_progress to
                    RowStatus.PROBLEM

            CloudNote.RESTORE_FAILED -> R.string.settings_cloud_restore_failed to RowStatus.PROBLEM

            CloudNote.MERGED -> R.string.settings_cloud_merged to RowStatus.OK

            CloudNote.BACKUP_OFFLINE -> R.string.settings_cloud_backup_offline to RowStatus.PROBLEM

            CloudNote.DELETE_FAILED -> R.string.settings_cloud_delete_failed to RowStatus.PROBLEM

            CloudNote.DELETED -> R.string.settings_cloud_deleted to RowStatus.OK
        }
    StatusRow(stringResource(text), status)
}
