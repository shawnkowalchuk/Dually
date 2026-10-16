package com.shawnkowalchuk.milo.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AttentionTile
import com.shawnkowalchuk.milo.core.designsystem.text.serviceDueText
import com.shawnkowalchuk.milo.core.service.ServiceAlert

/**
 * Home's tile for service that is soon or due (since 2026-10-16; Shawn: "home screen should show
 * alerts when a service is needed"): one item by its name and when it is due, or several, a line
 * each. It stays until Done is pressed on the Service screen, which its button opens. Shown with
 * no trip being recorded only: nothing to read while driving.
 */
@Composable
internal fun ServiceAlertTile(alerts: List<ServiceAlert>, format: HomeFormat, onOpen: () -> Unit) {
    val resources = LocalResources.current
    val lines =
        alerts.map {
            it.item.name to
                serviceDueText(it.item, it.due, format.unit, format.locale) { id, args ->
                    resources.getString(id, *args)
                }
        }
    val single = lines.singleOrNull()
    AttentionTile(
        title =
            single?.first
                ?: pluralStringResource(
                    R.plurals.notification_service_title_many,
                    alerts.size,
                    alerts.size,
                ),
        text =
            single?.second
                ?: lines.joinToString("\n") { (name, due) ->
                    resources.getString(R.string.notification_service_line, name, due)
                },
        actionLabel = stringResource(R.string.home_service_action),
        onAction = onOpen,
    )
}
