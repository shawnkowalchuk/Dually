import SwiftUI

/// A screen with its top line pinned while what is under it scrolls, as Android's
/// PinnedHeaderColumn (Shawn, 2026-10-19: "the top header should be pinned on all screens").
/// The room the bottom bar takes is left free under the content by the bar itself.
struct PinnedScreen<Content: View>: View {
    /// The screen's name at the end of the top line, or nil on Home, as on Android.
    let title: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) {
            TopLine(screen: title)
                .padding(.horizontal, 16)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    content
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
        }
        .background(Palette.page.ignoresSafeArea())
    }
}
