import DuallyShared
import Foundation
import UserNotifications

// The monthly reminder and the daily check on the iPhone (2026-11-15), as Android's, planned as
// notifications by the shared rules (IosReminders): what would be shown from now on, as things
// stand. Planned again whenever Dually runs, since an iPhone app is not woken at a set time:
// at its start, when it comes to the front, when a trip starts or ends, when a report is sent,
// and when one of their settings changes. Each plan takes the place of the last.
@MainActor
enum Reminders {
    static let reminderPrefix = "monthly-reminder-"
    static let dailyCheckPrefix = "daily-check-"

    /// The month a tap on a reminder opens, in its notification's details.
    private static let yearKey = "year"
    private static let monthKey = "month"

    static func plan(core: DuallyCore) async {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        guard let reminder = try? await core.reminders.reminderPlan(nowMs: now),
              let check = try? await core.reminders.dailyCheckPlan(nowMs: now) else { return }
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier)
        center.removePendingNotificationRequests(
            withIdentifiers: pending.filter { $0.hasPrefix(reminderPrefix) || $0.hasPrefix(dailyCheckPrefix) }
        )
        for fire in reminder.fires {
            await add(
                id: reminderPrefix + "\(fire.int64Value)",
                at: fire.int64Value,
                title: reminder.title,
                text: reminder.text,
                info: [yearKey: Int(reminder.year), monthKey: Int(reminder.month)]
            )
        }
        for fire in check.fires {
            await add(id: dailyCheckPrefix + "\(fire.int64Value)", at: fire.int64Value, title: check.title, text: check.text)
        }
    }

    /// The month a tapped reminder is about, or nil for any other notification.
    static func month(of response: UNNotificationResponse) -> (year: Int, month: Int)? {
        guard response.notification.request.identifier.hasPrefix(reminderPrefix) else { return nil }
        let info = response.notification.request.content.userInfo
        guard let year = info[yearKey] as? Int, let month = info[monthKey] as? Int else { return nil }
        return (year, month)
    }

    private static func add(id: String, at ms: Int64, title: String, text: String, info: [String: Int] = [:]) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = text
        content.sound = .default
        content.userInfo = info
        let date = Date(timeIntervalSince1970: Double(ms) / 1000)
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}
