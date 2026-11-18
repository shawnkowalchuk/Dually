import DuallyShared
import Foundation

// The Settings screen's state (phase 4g). The values, their wording and their ranges are the
// shared code's (IosSettings: Android's own settings file, choices and steps); this passes the
// user's presses on and shows what came back.
@MainActor
final class SettingsModel: ObservableObject {
    @Published private(set) var text: SettingsText?
    /// Why the last change of the hours was not stored, or nil.
    @Published private(set) var hoursRefused: String?

    private let core: DuallyCore

    init(core: DuallyCore) {
        self.core = core
    }

    func load() async {
        text = try? await core.settings.read()
        if let text { Look.shared.follow(text) }
    }

    func stepGrace(longer: Bool) async { await apply { try await self.core.settings.stepGrace(longer: longer) } }
    func stepParked(longer: Bool) async { await apply { try await self.core.settings.stepParkedLimit(longer: longer) } }
    func stepShortest(longer: Bool) async { await apply { try await self.core.settings.stepShortestTrip(longer: longer) } }
    func stepAdjustment(up: Bool) async { await apply { try await self.core.settings.stepAdjustment(up: up) } }
    func setMiles(_ miles: Bool) async { await apply { try await self.core.settings.setMiles(miles: miles) } }
    func setLight(_ light: Bool) async { await apply { try await self.core.settings.setLight(light: light) } }
    func setDrivingAlert(_ on: Bool) async { await apply { try await self.core.settings.setDrivingAlert(on: on) } }
    func setReminder(_ on: Bool) async { await apply { try await self.core.settings.setReminder(on: on) } }
    func stepReminderDay(later: Bool) async { await apply { try await self.core.settings.stepReminderDay(later: later) } }
    func setDailyCheck(_ on: Bool) async { await apply { try await self.core.settings.setDailyCheck(on: on) } }
    func setDailyCheckTime(_ minute: Int) async {
        await apply { try await self.core.settings.setDailyCheckTime(minute: Int32(minute)) }
    }

    /// The widget's switch: the widget is drawn again at once.
    func setWidget(_ on: Bool) async {
        await apply { try await self.core.settings.setWidget(on: on) }
        await TripSession.shared.updateWidget(force: true)
    }

    /// The widget's rate as typed: false, storing nothing, for what is not a rate in its limits.
    func setWidgetRate(_ typed: String) async -> Bool {
        let stored = (try? await core.settings.setWidgetRate(typed: typed))?.boolValue ?? false
        await load()
        if stored { await TripSession.shared.updateWidget(force: true) }
        return stored
    }

    /// One day's own hours, in minutes from midnight; Android's words when they are refused.
    func setDayHours(_ index: Int, start: Int, end: Int) async {
        let stored = (try? await core.settings.setDayHours(
            dayIndex: Int32(index), startMinute: Int32(start), endMinute: Int32(end)
        ))?.boolValue ?? false
        hoursRefused = stored
            ? nil
            : "A day's end must be later than its start. Nothing was changed. To move a whole day later, change its end first."
        await load()
        await Reminders.plan(core: core)
    }
    func setIgnoreOutside(_ ignore: Bool) async {
        await apply { try await self.core.settings.setIgnoreOutsideHours(ignore: ignore) }
    }

    func setWorkDay(_ index: Int, tracked: Bool) async {
        await apply { try await self.core.settings.setWorkDay(dayIndex: Int32(index), tracked: tracked) }
    }

    /// New hours for every day, in minutes from midnight. Android's words when they are refused.
    func setHours(start: Int, end: Int) async {
        let stored = (try? await core.settings.setHours(startMinute: Int32(start), endMinute: Int32(end)))?.boolValue ?? false
        hoursRefused = stored
            ? nil
            : "A day's end must be later than its start. Nothing was changed. To move a whole day later, change its end first."
        await load()
    }

    /// One of the report's details, stored as it is typed ("name", "company", "vehicle").
    func setReportText(_ field: String, _ typed: String) async {
        _ = try? await core.settings.setReportText(field: field, typed: typed)
    }

    /// The accountant's address: stored once it is a whole address. Returns whether it was.
    func setAccountantEmail(_ typed: String) async -> Bool {
        (try? await core.settings.setAccountantEmail(typed: typed))?.boolValue ?? false
    }

    private func apply(_ change: @escaping () async throws -> SettingsText) async {
        if let changed = try? await change() {
            text = changed
            Look.shared.follow(changed)
            await Reminders.plan(core: core)
        }
    }
}
