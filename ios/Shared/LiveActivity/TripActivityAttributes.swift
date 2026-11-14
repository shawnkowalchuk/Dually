import ActivityKit
import Foundation

/// The trip being recorded, as its Live Activity shows it on the Lock Screen and in the Dynamic
/// Island (phase 4e): the iPhone's form of Android's trip notification. The app starts, updates
/// and ends it (TripLiveActivity.swift); the widget extension draws it. Shared by both, so the
/// two agree on what is sent.
@available(iOS 16.1, *)
struct TripActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// The distance so far, written as Home writes it: "12.3".
        var distance: String
        /// "km" or "mi", as Settings has it.
        var unit: String
        /// The grace period runs: the truck has gone, and the trip ends unless it comes back.
        var waitingForTruck: Bool
    }

    /// The trip, so the app finds its own Live Activity again after a restart.
    var tripId: Int64
    /// When the trip started: the time shown counts up from it by itself.
    var startedAt: Date
}
