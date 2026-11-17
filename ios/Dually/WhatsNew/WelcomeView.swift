import SwiftUI

// The first page of the first start on the iPhone (2026-11-16), as Android's (2026-10-08, Shawn:
// "What the app does and how it works. than a ok."): one page that scrolls, the accent tile with
// what Dually is and what it costs, a tile for each thing to know, and OK at the bottom, which
// opens Setup with its Done button. Shown once, on a fresh install, before any other screen and
// without the bottom bar. Android's words, told as the iPhone does it: the Shortcuts automation
// starts a trip, Mail sends the report, iCloud backs the iPhone up.
struct WelcomeView: View {
    let ok: () -> Void

    private let sections: [(String, String)] = [
        ("How a trip starts and ends",
         "A Shortcuts automation that you make in Setup tells Dually when your iPhone connects to CarPlay or the truck's Bluetooth: a trip starts by itself and Dually records the drive with GPS. It ends when the truck disconnects, or once the truck has stood still for a while. You can also press Start trip and End trip yourself."),
        ("Business or Personal",
         "Trips in your work hours are saved as Business, the others as Personal. You set the hours in Settings, and you can change any trip on the Trips screen."),
        ("Each month",
         "From the day you pick, Dually reminds you to send last month's report. It opens Mail with the PDF attached, and you press send."),
        ("Your trips stay on this iPhone",
         "Nothing leaves it except files you send or save yourself, your own iCloud backup and, only if you buy the optional online backup, a copy of your trips in Google's Firebase, in Montréal, Canada."),
        ("Next",
         "Setup asks for the permissions Dually needs and walks you through the Shortcuts automations that start and end your trips. Then Settings, for your work hours, your name and the accountant's email address."),
    ]

    var body: some View {
        VStack(spacing: 0) {
            TopLine(screen: "Welcome")
                .padding(.horizontal, 16)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Tile(ground: Palette.accentTile) {
                        Text("Dually Trip Tracker")
                            .font(Typography.display(26))
                            .foregroundStyle(Palette.markLetter)
                            .accessibilityAddTraits(.isHeader)
                        Text("It logs your business driving by itself: every drive in your truck, saved as Business or Personal, and the monthly report for your accountant.")
                            .font(Typography.body(16))
                            .foregroundStyle(Palette.markLetter)
                            .fixedSize(horizontal: false, vertical: true)
                        // What it costs, said first, as on Android since 2026-11-12.
                        Text("Free, with every feature and no ads. The only purchase is the optional online backup.")
                            .font(Typography.body(16, weight: .semibold))
                            .foregroundStyle(Palette.markLetter)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    ForEach(sections, id: \.0) { title, text in
                        Tile {
                            Text(title)
                                .font(Typography.body(17, weight: .semibold))
                                .foregroundStyle(Palette.text)
                                .accessibilityAddTraits(.isHeader)
                            Text(text)
                                .font(Typography.body(15))
                                .foregroundStyle(Palette.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .padding(.bottom, 16)
            }
            // OK stays at the bottom while the tiles scroll, as on Android since 2026-10-09.
            WideButton(title: "OK", filled: true, action: ok)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
        }
        .background(Palette.page.ignoresSafeArea())
    }
}
