package com.shawnkowalchuk.milo.platform.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.serviceDueText
import com.shawnkowalchuk.milo.core.service.ServiceAlert
import com.shawnkowalchuk.milo.core.util.DistanceUnit

// The ids below are stored by Android. The trip notifications use 1 to 3, the monthly reminder
// 4, the daily check 5 and the odometer check 6.
private const val SERVICE_NOTIFICATION_ID = 7
private const val SERVICE_CHANNEL_ID = "service"

/** Tells this notification's tap from the others that open the same activity (0 to 3). */
private const val OPEN_SERVICE_REQUEST = 4

/** What the tap asks of Dually's activity: the Service screen. */
private const val ACTION_OPEN_SERVICE = "com.shawnkowalchuk.milo.action.OPEN_SERVICE"

/**
 * "Oil change: due in 400 km" (since 2026-10-16), or "2 service items need you" with a line for
 * each: posted when a trip has ended, never during one. A tap opens the Service screen. On a
 * channel of its own ("Service reminders"), so that it can be switched off by itself.
 *
 * @param opens the activity a tap opens: Dually's one activity.
 */
class ServiceNotification(private val context: Context, private val opens: Class<*>) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /** Posts [alerts], at least one, with distances in [unit]. */
    fun show(alerts: List<ServiceAlert>, unit: DistanceUnit) {
        val locale = context.resources.configuration.locales[0]
        val lines =
            alerts.map {
                val due = serviceDueText(it.item, it.due, unit, locale) { id, args ->
                    context.getString(id, *args)
                }
                context.getString(R.string.notification_service_line, it.item.name, due)
            }
        val title =
            if (alerts.size == 1) {
                lines.single()
            } else {
                context.resources.getQuantityString(
                    R.plurals.notification_service_title_many,
                    alerts.size,
                    alerts.size,
                )
            }
        val text =
            if (alerts.size == 1) {
                context.getString(R.string.notification_service_text)
            } else {
                lines.joinToString("\n")
            }
        val notification =
            Notification
                .Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setColor(context.getColor(R.color.milo_notification_accent))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openService())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        manager.notify(SERVICE_NOTIFICATION_ID, notification)
    }

    private fun openService(): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_SERVICE_REQUEST,
        Intent(context, opens)
            .setAction(ACTION_OPEN_SERVICE)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Whether [intent] is a tap on the service reminder, which asks for the Service screen. False
 * for a start from the list of recent apps, which carries the request the window was opened with.
 */
fun serviceAskedFor(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_SERVICE &&
    intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
