package com.shawnkowalchuk.milo.ios

import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/** One notice of its kind at a time: a later one takes the earlier one's place. */
private const val REPLACED_ID = "backup-replaced"

/**
 * "A change of yours was replaced" (ADR-017, 2026-11-14), as Android's notification on its
 * "Online backup" channel. Dually asks for notifications for its trips and service reminders;
 * without them, nothing shows, and the trip still offers "Put back".
 */
internal fun postReplacedNotice(title: String, text: String) {
    val content = UNMutableNotificationContent()
    content.setTitle(title)
    content.setBody(text)
    content.setSound(UNNotificationSound.defaultSound)
    val request = UNNotificationRequest.requestWithIdentifier(REPLACED_ID, content, null)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
}
