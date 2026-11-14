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
    }

    func stepGrace(longer: Bool) async { await apply { try await self.core.settings.stepGrace(longer: longer) } }
    func stepParked(longer: Bool) async { await apply { try await self.core.settings.stepParkedLimit(longer: longer) } }
    func stepShortest(longer: Bool) async { await apply { try await self.core.settings.stepShortestTrip(longer: longer) } }
    func stepAdjustment(up: Bool) async { await apply { try await self.core.settings.stepAdjustment(up: up) } }
    func setMiles(_ miles: Bool) async { await apply { try await self.core.settings.setMiles(miles: miles) } }
    func setDrivingAlert(_ on: Bool) async { await apply { try await self.core.settings.setDrivingAlert(on: on) } }
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

    private func apply(_ change: @escaping () async throws -> SettingsText) async {
        if let changed = try? await change() { text = changed }
    }
}
