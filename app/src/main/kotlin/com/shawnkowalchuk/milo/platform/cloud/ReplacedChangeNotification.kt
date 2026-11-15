package com.shawnkowalchuk.milo.platform.cloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.data.cloud.ReplacedNotice
import java.time.ZoneId

// The ids below are stored by Android. The trip notifications use 1 to 3, the monthly reminder
// 4, the daily check 5, the odometer check 6 and the service reminder 7.
private const val REPLACED_NOTIFICATION_ID = 8
private const val REPLACED_CHANNEL_ID = "backup_replaced"

/** Tells this notification's tap from the others that open the same activity. */
private const val OPEN_TRIPS_REQUEST = 5

/**
 * "A change of yours was replaced" (ADR-017, 2026-11-14): another phone changed the same trip
 * before this phone's change reached it, and its change, uploaded later, was kept. The trip's
 * history holds this phone's, and the trip offers to put it back. A tap opens Dually. On a
 * channel of its own ("Online backup").
 *
 * @param opens the activity a tap opens: Dually's one activity.
 */
class ReplacedChangeNotification(private val context: Context, private val opens: Class<*>) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                REPLACED_CHANNEL_ID,
                context.getString(R.string.notification_channel_backup),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /** Posts [notices], at least one. */
    fun show(notices: List<ReplacedNotice>) {
        val locale = context.resources.configuration.locales[0]
        val text =
            notices.joinToString("\n") {
                context.getString(
                    R.string.settings_cloud_replaced_text,
                    formatDate(it.tripStartedAtMs, ZoneId.systemDefault(), locale),
                    it.keptOn,
                )
            }
        val notification =
            Notification
                .Builder(context, REPLACED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setColor(context.getColor(R.color.milo_notification_accent))
                .setContentTitle(context.getString(R.string.settings_cloud_replaced_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        OPEN_TRIPS_REQUEST,
                        Intent(context, opens)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setAutoCancel(true)
                .build()
        manager.notify(REPLACED_NOTIFICATION_ID, notification)
    }
}
