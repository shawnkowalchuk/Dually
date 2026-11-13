# ADR-014: An iPhone app with CarPlay: shared Kotlin logic, SwiftUI screens

Date: 2026-11-11
Status: Accepted. Not built (phases 3 and 4 of the iPhone and cloud backup plan). Versions are chosen when each piece is added.

## Context

Shawn, 2026-11-11: "i want to make a ios version of the app native to ios". Asked how, he chose **"Shared logic, native look"** over a fully separate Swift app, and **"Phone and CarPlay together"** at launch over CarPlay later. He has an iPhone to test on, an Apple Developer account, a truck with CarPlay, and Xcode on the Mac.

What the code allows, counted on 2026-11-11:

- `core/` outside the design system is **57 Kotlin files, none of which imports Android.** Trip rules, the parked rule, distance, schedule, the clock, the odometer, the report's figures: plain Kotlin already. 26 of them use `java.time` (dates and zones), which does not exist on iPhone.
- `data/` is 79 files; 45 import Android or AndroidX, mostly Room's annotations and Android's file and content APIs. **Room 3 on bundled SQLite and DataStore are both Kotlin Multiplatform libraries**, so the database and the settings can move with little change. The export format uses kotlinx.serialization, which is multiplatform.
- `core/designsystem/` is Jetpack Compose, Android only.
- `platform/` (the trip service, CompanionDeviceManager, location, Android Auto, alarms, the widget) is Android by nature.

The rule this protects: logic lives once, so surfaces cannot drift (CLAUDE.md, ARCHITECTURE §4). Two copies of the trip rules would drift.

## Decision

### Shared logic in Kotlin Multiplatform

- A new Gradle module, **`:shared`**, a Kotlin Multiplatform module with an Android target and iOS targets (device and simulator). It holds what is not about one platform's APIs: `core/` without the design system, the database (Room 3, its entities, DAOs and migrations), the settings store, the export format and its checks, the import, and the backup's outbox and mapping (ADR-012).
- **`java.time` is replaced by kotlinx-datetime** inside `:shared`. `java.util.Locale` and `java.io` give way to small `expect`/`actual` pieces where a platform must answer (the phone's zone, its language, a file).
- **`:app` depends on `:shared`** and keeps Android's own code: the design system, every screen, `platform/`, Android Auto, the widget.
- **The move is phase 3, before any iPhone screen,** and it is a move, not a rewrite: the same code under a new module, its tests moved with it and run on both targets. Android must behave exactly as before, checked by the full test suite and a release check on Shawn's phone. The database's file and version do not change because of the move.
- The iPhone app uses the shared code as a framework that Xcode builds through Gradle.

### The iPhone app: SwiftUI

- **`ios/`** holds an Xcode project, "Dually", written in **Swift with SwiftUI**: native screens, native navigation, native share sheet for the PDF and CSV, Sign in with Apple.
- **Bundle ID `top.dually.triptracker`,** the same as Android's application ID.
- **The look is Dually's design written again in SwiftUI:** the same colour tokens, corners and spacing, and the same typefaces (Archivo and IBM Plex Mono, bundled with their licences). One list of tokens, `ios/Dually/DesignSystem/`, mirrors `core/designsystem/`; a value changed in one is changed in the other in the same pull request. The phone screens follow iPhone conventions where Android's differ (back swipe, tab bar, sheets), not a copy of Android's.
- **Not chosen: Compose Multiplatform** for the iPhone screens. It would share the screens too, but it does not look or behave like an iPhone app, and Shawn asked for native.
- **iOS version: iOS 16 and later** (decided 2026-11-12). Shawn: "I think we should go back as far back as we can and make it Most compatible and reasonable." iOS 16 is as far back as the app can go without losing a part of it: CarPlay's Driving Task category, the App Intents that a Shortcuts automation runs to start a trip (ADR-015), and the Live Activity on the Lock Screen all need iOS 16 (the Live Activity 16.1; on 16.0 it is left out). iOS 16 runs on the iPhone 8 and later. Until 2026-11-12 this record said the version on Shawn's iPhone, as on Android.
- **iPhone surfaces:** the phone screens, **CarPlay**, a Home Screen widget (WidgetKit) like Android's, and a Live Activity on the Lock Screen while a trip records.

### CarPlay

- **Granted 2026-11-12** (Shawn: "Apple granted the permission for CarPlay"): Dually's App ID `top.dually.triptracker` shows "CarPlay Driving Task App" as Assigned under Capability Requests in the developer account.
- CarPlay needs an **entitlement Apple grants on request**. Dually most likely fits Apple's **Driving Task** category (apps that help with a task done while driving, with simple templates only). The request is sent early, in phase 0, because Apple's answer can take weeks and the launch waits on it.
- Driving Task apps get a small set of templates: a trip being recorded, start and stop, today's trips. The same scope as the Android Auto screen, whose rules come from the same shared code.
- If Apple refuses the entitlement, the iPhone app launches without CarPlay, and the request is made again with Apple's reasons answered. Recorded as a risk, not built around.

### Surfaces, from now on

Dually has four surfaces on two platforms: Android's phone UI and Android Auto, and the iPhone's phone UI and CarPlay. The kickoff question "Which surfaces?" (CLAUDE.md) becomes "which platforms, and which surfaces on each". ARCHITECTURE §4 and CLAUDE.md are updated when the iPhone app is first built.

### What is not shared, and why

- **Trip detection's start** is platform code: iPhone has no CompanionDeviceManager and no free Bluetooth watch. ADR-015 decides how the iPhone starts a trip. Once a trip has started, its rules (parked, ending, distance, schedule) are the shared ones.
- **Screens and design tokens** are written twice, by Shawn's choice of a native look. Kept apart from the logic so that writing them twice is all that is duplicated.

## Consequences

- Better: one set of trip rules, one database, one export format and one backup, for both phones. A fix to a rule reaches both.
- Better: the move in phase 3 also lets the rules run in tests without Android at all.
- Worse: phase 3 is a large change with nothing new to see. It touches most of `core/` and `data/`, and every date in the rules moves to kotlinx-datetime. It needs its own branch, the full suite and a release check on the phone.
- Worse: two UI codebases and two design-token lists to keep alike, and a second toolchain (Xcode, Swift, Apple's signing and App Store review).
- Worse: the Mac's disk. Xcode and an iOS simulator take tens of GB. On 2026-11-12 the disk had about 140 GB free, which Shawn judged enough ("I don't think that should be a problem").
- Locked into: Kotlin Multiplatform for the shared logic. It is Google's recommended way to share Kotlin with iOS, and Room, DataStore, coroutines and kotlinx.serialization all support it.
- Unknown until tried: CarPlay's entitlement; the build time of the shared framework in Xcode; the iPhone version on Shawn's phone.
