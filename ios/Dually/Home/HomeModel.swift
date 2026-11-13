import CoreLocation
import DuallyShared
import Foundation

// What Home shows and does, through the shared code (ADR-014). The figures are worked out and
// formatted in Kotlin (DuallyCore.homeFigures), so they say what Android's say; a trip is
// recorded by the shared trip rules and ledger (IosTripRecorder, phase 4b), fed by the iPhone's
// GPS (LocationRecorder).
@MainActor
final class HomeModel: ObservableObject {
    @Published private(set) var figures: HomeFigures?
    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?

    let location = LocationRecorder()
    private let core: DuallyCore

    init(core: DuallyCore) {
        self.core = core
        location.onFix = { [weak self] fix in
            Task { await self?.add(fix) }
        }
    }

    /// Reads everything again, as of now, and picks up a trip that was open when the app last
    /// stopped: its GPS starts again.
    func refresh() async {
        await run {
            self.recording = try await self.core.recorder.now()
            if self.recording != nil { self.location.start() }
            self.figures = try await self.core.homeFigures(nowMs: Self.nowMs())
        }
    }

    func start() async {
        await run {
            self.recording = try await self.core.recorder.start()
            if self.recording != nil { self.location.start() }
        }
    }

    func end() async {
        await run {
            self.recording = try await self.core.recorder.end()
            if self.recording == nil { self.location.stop() }
            self.figures = try await self.core.homeFigures(nowMs: Self.nowMs())
        }
    }

    /// The running trip's distance as Home writes it: "12.3".
    func distanceText(_ trip: RecordingNow) -> String {
        core.distanceText(metres: trip.metres)
    }

    private func add(_ fix: Fix) async {
        await run {
            self.recording = try await self.core.recorder.addFix(
                wallClockMs: fix.wallClockMs,
                elapsedMs: fix.elapsedMs,
                latitude: fix.latitude,
                longitude: fix.longitude,
                accuracyMetres: fix.accuracyMetres.map { KotlinFloat(value: $0) },
                speedMetresPerSecond: fix.speedMetresPerSecond.map { KotlinFloat(value: $0) }
            )
            if self.recording == nil { self.location.stop() }
        }
    }

    private func run(_ work: @escaping () async throws -> Void) async {
        do {
            try await work()
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
    }

    private static func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
