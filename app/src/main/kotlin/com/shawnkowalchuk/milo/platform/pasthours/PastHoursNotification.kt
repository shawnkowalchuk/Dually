package com.shawnkowalchuk.milo.platform.pasthours

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import java.time.ZoneId

// The ids below are stored by Android. The trip notifications use 1 to 3, the monthly reminder
// 4, the daily check 5, the odometer check 6 and the service reminder 7.
private const val PAST_HOURS_NOTIFICATION_ID = 8
private const val PAST_HOURS_CHANNEL_ID = "work_hours"

/** Tell the two buttons' broadcasts apart, and from MilO's other ones. */
private const val MARK_PERSONAL_REQUEST = 8
private const val KEEP_BUSINESS_REQUEST = 9

internal const val ACTION_MARK_PERSONAL = "com.shawnkowalchuk.milo.action.PAST_HOURS_PERSONAL"
internal const val ACTION_KEEP_BUSINESS = "com.shawnkowalchuk.milo.action.PAST_HOURS_BUSINESS"
internal const val EXTRA_TRIP_ID = "trip_id"

/**
 * The work-hours notification (since 2026-11-03; Shawn's answers: "Both", "Buttons on the
 * notification", "Phone and Android Auto"). For a trip saved as Business by the work hours that
 * runs past them, it asks whether to mark it Personal, with two buttons, **Mark Personal** and
 * **Keep Business** ([PastHoursReceiver]):
 *
 * - **During the drive**, when the hours end ([showDuringTrip]): silent, it waits in the shade
 *   and on the car's screen for a stop. Its answer is the trip's choice, as Home's button.
 * - **When the trip has ended** ([showAfterTrip]), if it was not answered: the same question,
 *   with a sound, about the trip as saved. It replaces the first.
 *
 * Both carry Android Auto's extender, so that the car's screen shows them with the same buttons.
 * On a channel of its own ("Work hours"), which can be switched off by itself.
 *
 * @param opens the activity a tap on the notification itself opens: Dually's one activity.
 */
class PastHoursNotification(private val context: Context, private val opens: Class<*>) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                PAST_HOURS_CHANNEL_ID,
                context.getString(R.string.notification_channel_work_hours),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /** The heads-up while [tripId] is being recorded: the hours ended at [hoursEndMs]. */
    fun showDuringTrip(tripId: Long, hoursEndMs: Long) {
        val title = context.getString(R.string.notification_work_hours_ended, time(hoursEndMs))
        val text = context.getString(R.string.notification_work_hours_during)
        post(tripId, title, text, silent = true)
    }

    /** The question for [tripId], which ran from [startedAtMs] to [endedAtMs]. */
    fun showAfterTrip(tripId: Long, startedAtMs: Long, endedAtMs: Long) {
        val title =
            context.getString(
                R.string.notification_work_hours_trip,
                time(startedAtMs),
                time(endedAtMs),
            )
        val text = context.getString(R.string.notification_work_hours_after)
        post(tripId, title, text, silent = false)
    }

    fun cancel() {
        manager.cancel(PAST_HOURS_NOTIFICATION_ID)
    }

    // Notifications are allowed by the Setup checklist; without that permission Android drops
    // the notification, which is the user's choice.
    @android.annotation.SuppressLint("MissingPermission")
    private fun post(tripId: Long, title: String, text: String, silent: Boolean) {
        val personal = context.getString(R.string.notification_work_hours_mark_personal)
        val business = context.getString(R.string.notification_work_hours_keep_business)
        val markPersonal = answer(tripId, TripCategory.PERSONAL)
        val keepBusiness = answer(tripId, TripCategory.BUSINESS)
        val car =
            CarAppExtender
                .Builder()
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setImportance(
                    if (silent) {
                        NotificationManagerCompat.IMPORTANCE_LOW
                    } else {
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                    },
                ).addAction(R.drawable.ic_stat_trip, personal, markPersonal)
                .addAction(R.drawable.ic_stat_trip, business, keepBusiness)
                .build()
        val notification =
            NotificationCompat
                .Builder(context, PAST_HOURS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setColor(context.getColor(R.color.milo_notification_accent))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setSilent(silent)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(0, personal, markPersonal)
                .addAction(0, business, keepBusiness)
                .extend(car)
                .build()
        manager.notify(PAST_HOURS_NOTIFICATION_ID, notification)
    }

    private fun time(epochMs: Long): String = formatTimeOfDay(
        epochMs,
        ZoneId.systemDefault(),
        context.resources.configuration.locales[0],
        DateFormat.is24HourFormat(context),
    )

    private fun answer(tripId: Long, category: TripCategory): PendingIntent {
        val personal = category == TripCategory.PERSONAL
        return PendingIntent.getBroadcast(
            context,
            if (personal) MARK_PERSONAL_REQUEST else KEEP_BUSINESS_REQUEST,
            Intent(context, PastHoursReceiver::class.java)
                .setAction(if (personal) ACTION_MARK_PERSONAL else ACTION_KEEP_BUSINESS)
                .putExtra(EXTRA_TRIP_ID, tripId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(
            context,
            opens,
        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
