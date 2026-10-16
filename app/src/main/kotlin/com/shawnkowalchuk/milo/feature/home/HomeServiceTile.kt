package com.shawnkowalchuk.milo.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AttentionTile
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.text.serviceDueText
import com.shawnkowalchuk.milo.core.service.ServiceAlert

/**
 * Home's tile for a service item that is soon or due (since 2026-10-16; Shawn: "home screen
 * should show alerts when a service is needed"): its name and when it is due, one tile each. It
 * stays until Done. Its button is **Done** (his next request: "on the oil card it should have a
 * button to change oil that will reset the mileage"): asked first, then recorded for now at the
 * odometer as Dually has it, which starts the next interval. A cost or a note is added from the
 * Service screen's own Done. Shown with no trip being recorded only: nothing to read while
 * driving.
 */
@Composable
internal fun ServiceAlertTile(alert: ServiceAlert, format: HomeFormat, onDone: (Long) -> Unit) {
    val resources = LocalResources.current
    var asking by rememberSaveable(alert.item.id) { mutableStateOf(false) }
    val name = alert.item.name
    AttentionTile(
        title = name,
        text =
            serviceDueText(alert.item, alert.due, format.unit, format.locale) { id, args ->
                resources.getString(id, *args)
            },
        actionLabel = stringResource(R.string.home_service_done),
        onAction = { asking = true },
    )
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.home_service_done_title, name),
            text = stringResource(R.string.home_service_done_text),
            confirmLabel = stringResource(R.string.home_service_done),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                asking = false
                onDone(alert.item.id)
            },
            onDismiss = { asking = false },
        )
    }
}
