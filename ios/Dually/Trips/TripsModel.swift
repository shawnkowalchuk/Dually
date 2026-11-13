import DuallyShared
import Foundation

// The Trips screen's state, one month at a time, as Android's (phase 4c). The month itself is
// worked out and worded by the shared code (IosTrips.month); this keeps which month is shown
// and passes the user's changes back to it.
@MainActor
final class TripsModel: ObservableObject {
    @Published private(set) var month: MonthText?
    @Published private(set) var failure: String?
    @Published var showLeftOut = false {
        didSet { Task { await load() } }
    }
    @Published private(set) var year: Int
    @Published private(set) var monthNumber: Int

    private let core: DuallyCore

    init(core: DuallyCore) {
        self.core = core
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        year = now.year ?? 2026
        monthNumber = now.month ?? 1
    }

    /// True while a later month than the shown one has begun: the next arrow then works.
    var canGoForward: Bool {
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        return (year, monthNumber) < (now.year ?? year, now.month ?? monthNumber)
    }

    func load() async {
        await run {
            let live = try await self.core.recorder.now()
            self.month = try await self.core.trips.month(
                year: Int32(self.year),
                month: Int32(self.monthNumber),
                showLeftOut: self.showLeftOut,
                liveTripId: live.map { KotlinLong(value: $0.tripId) },
                liveMetres: live.map { KotlinDouble(value: $0.metres) }
            )
        }
    }

    func step(by months: Int) async {
        var index = year * 12 + (monthNumber - 1) + months
        if months > 0 && !canGoForward { return }
        index = max(index, 0)
        year = index / 12
        monthNumber = index % 12 + 1
        await load()
    }

    func mark(_ trip: TripText, business: Bool) async {
        await run { _ = try await self.core.trips.mark(tripId: trip.tripId, business: business) }
        await load()
    }

    func correct(_ trip: TripText) async {
        guard let correction = trip.correction else { return }
        await run { _ = try await self.core.trips.correct(tripId: trip.tripId, correction: correction) }
        await load()
    }

    func labelChoices(for trip: TripText) async -> [String] {
        (try? await core.trips.labelChoicesFor(tripId: trip.tripId)) ?? []
    }

    func setLabel(_ trip: TripText, to label: String) async {
        await run { _ = try await self.core.trips.setLabel(tripId: trip.tripId, label: label) }
        await load()
    }

    private func run(_ work: @escaping () async throws -> Void) async {
        do {
            try await work()
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
    }
}
