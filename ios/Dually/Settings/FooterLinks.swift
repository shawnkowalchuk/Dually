import SwiftUI
import UIKit

/// Where a question about the app is sent, as on Android (`SupportTile.kt`): an address at the
/// website's domain that forwards to Shawn's own, which he asked to keep out of sight.
private let supportAddress = "support@dually.top"

/// Dually's page on the App Store, opened at its review form.
private let reviewPage = URL(string: "https://apps.apple.com/app/id6834196352?action=write-review")!

// The end of Settings on the iPhone (2026-11-16), as Android's since 2026-11-12: one short list
// of the links that are not settings, Support first, since it is the one a person with a problem
// looks for. Buy me a coffee is left out, as in Android's copy from Google Play: the App Store
// takes a tip to the developer through its own purchase only (Guideline 3.1.1).
struct FooterLinks: View {
    @State private var noEmailApp = false
    @State private var noStore = false

    var body: some View {
        Tile {
            FooterLink(
                label: "Email support",
                value: supportAddress,
                problem: noEmailApp
                    ? "No app on this iPhone writes emails. You can write to the address above from another device."
                    : nil,
                hint: "write an email to support"
            ) {
                openURL(supportDraft()) { opened in noEmailApp = !opened }
            }
            Divider().padding(.vertical, 4)
            FooterLink(
                label: "Review on the App Store",
                value: nil,
                problem: noStore ? "The App Store could not be opened. Dually is on the App Store as Dually Trip Tracker." : nil,
                hint: "open Dually's page in the App Store"
            ) {
                openURL(reviewPage) { opened in noStore = !opened }
            }
        }
    }

    /// A draft in the iPhone's email app: room to write in at the top, and under it the one
    /// line about Dually and the iPhone that a question about a missed trip is answered from,
    /// as on Android. Dually sends nothing itself: the person sends the draft or does not.
    private func supportDraft() -> URL {
        let about = "Dually Trip Tracker \(InstalledVersion.name) (build \(InstalledVersion.build))"
            + " · iOS \(UIDevice.current.systemVersion) · \(modelName())"
        var parts = URLComponents()
        parts.scheme = "mailto"
        parts.path = supportAddress
        parts.queryItems = [
            URLQueryItem(name: "subject", value: "Dually Trip Tracker support"),
            URLQueryItem(name: "body", value: "\n\n\n" + about),
        ]
        return parts.url ?? URL(string: "mailto:\(supportAddress)")!
    }

    /// The iPhone's model as Apple numbers it ("iPhone17,1"): what Android's line gives as
    /// the maker and model.
    private func modelName() -> String {
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: &system.machine) { bytes in
            String(decoding: bytes.prefix(while: { $0 != 0 }), as: UTF8.self)
        }
    }

    private func openURL(_ url: URL, done: @escaping (Bool) -> Void) {
        UIApplication.shared.open(url, options: [:]) { opened in done(opened) }
    }
}

/// One row: its label with the arrowhead, an optional line under it, and the problem a press
/// ran into. The whole row is the button.
private struct FooterLink: View {
    let label: String
    let value: String?
    let problem: String?
    let hint: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(label)
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Palette.textSecondary)
                }
                if let value {
                    Text(value)
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.textSecondary)
                }
                if let problem {
                    Text(problem)
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.amber)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(hint)
    }
}
