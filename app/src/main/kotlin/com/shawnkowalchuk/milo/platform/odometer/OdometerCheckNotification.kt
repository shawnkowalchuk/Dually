package com.shawnkowalchuk.milo.platform.odometer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.util.DistanceUnit

// The ids below are stored by Android. The trip notifications use 1 to 3, the monthly reminder
// 4 and the daily check 5.
private const val ODOMETER_CHECK_NOTIFICATION_ID = 6
private const val ODOMETER_CHECK_CHANNEL_ID = "odometer_check"

/** Tells this notification's tap from the others that open the same activity (0 to 2). */
private const val OPEN_SETTINGS_REQUEST = 3

/** What the tap asks of Dually's activity: Settings, where the odometer is adjusted. */
private const val ACTION_OPEN_SETTINGS = "com.shawnkowalchuk.milo.action.OPEN_SETTINGS"

/**
 * "Ford F-150: odometer about 120,345 km?" (since 2026-10-16): posted when a trip starts in
 * another vehicle than the last one. **Silent** (low importance: no sound, no pop-up), because it
 * comes as the driver is about to drive; it waits in the shade for a stop. A tap opens Settings,
 * where the vehicle's odometer is adjusted. On a channel of its own ("Odometer check"), so that
 * it can be switched off by itself in the phone's settings.
 *
 * @param opens the activity a tap opens: Dually's one activity.
 */
class OdometerCheckNotification(private val context: Context, private val opens: Class<*>) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                ODOMETER_CHECK_CHANNEL_ID,
                context.getString(R.string.notification_channel_odometer_check),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /** Posts the question for [vehicle], whose odometer Dually has at [figure] [unit]. */
    fun show(vehicle: String, figure: String, unit: DistanceUnit) {
        val title =
            context.getString(
                R.string.notification_odometer_check_title,
                vehicle,
                figure,
                context.getString(unitShortRes(unit)),
            )
        val text = context.getString(R.string.notification_odometer_check_text)
        val notification =
            Notification
                .Builder(context, ODOMETER_CHECK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setColor(context.getColor(R.color.milo_notification_accent))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openSettings())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        manager.notify(ODOMETER_CHECK_NOTIFICATION_ID, notification)
    }

    private fun openSettings(): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_SETTINGS_REQUEST,
        Intent(context, opens)
            .setAction(ACTION_OPEN_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Whether [intent] is a tap on the odometer check, which asks for Settings. False for a start
 * from the list of recent apps, which carries the request the window was first opened with.
 */
fun settingsAskedFor(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_SETTINGS &&
    intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
