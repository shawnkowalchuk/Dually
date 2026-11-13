# ADR-015: How the iPhone starts a trip

Date: 2026-11-11
Status: Proposed. Shawn's choices of 2026-11-13 are below ("Shawn's choices"). To be proven by real drives with Shawn's iPhone in his truck before the iPhone app is released. Not built.

## Context

Reliable automatic trip start is Dually's number one requirement (ARCHITECTURE §1). On Android it rests on CompanionDeviceManager: Android wakes Dually when the paired truck's Bluetooth connects, and a foreground service records the drive (ADR-002).

The iPhone has none of that:

- **No app can watch for a classic Bluetooth connection in the background.** A truck's Bluetooth is classic (hands-free and audio); Core Bluetooth sees only Bluetooth Low Energy accessories. While Dually is running it can read the current audio route and see the truck's name there, but it is not woken when that route appears.
- **No foreground service.** An app records location in the background only once it has started doing so, with "Always" location permission and the background location mode.
- **What can wake an app:** location (significant-change updates, leaving a monitored region, Core Location's monitor of conditions), CarPlay connecting while the app is on CarPlay, and **Shortcuts personal automations**: "When my iPhone connects to [a Bluetooth device]" and "When CarPlay connects" can run an app's App Intent without asking, and the same for disconnecting.
- **Core Motion** reports the "automotive" activity, and keeps a history of it, but does not wake an app.

Shawn chose CarPlay at launch (ADR-014), and his truck has CarPlay.

## Decision

Three layers, from the one that starts the trip to the ones that catch what it missed. The same design as Android's: a reliable trigger, and a safety net with a manual button and a driving alert ([Safety net](../APP_ENCYCLOPEDIA.md#safety-net-manual-button-and-driving-alert)).

1. **The trigger: the truck's Bluetooth or CarPlay, through a Shortcuts automation.** Dually provides App Intents "Start a trip" and "End a trip". First-start onboarding walks the user through making two personal automations in the Shortcuts app ("When connected to [truck]" → Start a trip, "When disconnected" → End a trip, set to run immediately), with a check afterwards that both fire. With CarPlay, "When CarPlay connects" is offered as well or instead. The intent starts background location, a Live Activity on the Lock Screen, and the trip in the shared rules. Dually cannot make the automations itself; the user's own setup is the cost of this layer.
2. **The safety net: driving noticed without a trip.** With "Always" location, Dually monitors the region around where the truck last parked and receives significant-change updates. When woken by movement, it checks Core Motion's automotive activity and the current audio route (is the truck's Bluetooth or CarPlay connected?). Driving with the truck connected and no trip open starts a trip, starting where the truck was parked, as Android does since 2026-10-07. Driving without the truck sends the driving alert ("Driving? Start a trip") instead.
3. **The manual buttons:** Start and Stop on the phone, in the Live Activity, in the widget, and on CarPlay.

Once a trip is open, the shared rules decide its end, its parked time and its distance (ADR-014). Disconnecting the truck ends a trip the same way on both phones.

**How it is proven.** A series of real drives with Shawn's iPhone, as the Android trips were proven: each start and end logged with the layer that caught it, and the time from engine on to the first fix. The release waits until layer 1 starts every trip of a working week and layer 2 catches a trip with the automation switched off. The results go into FINDINGS_LOG and the iPhone's device checks, and this record becomes Accepted, with what was learned, or is changed.

## Shawn's choices (2026-11-13)

Asked the four open questions of this record, Shawn chose:

- **The trigger: the user picks it in setup,** CarPlay connecting or the truck's Bluetooth connecting, over Bluetooth only or CarPlay only. CarPlay suits a truck that has it, as his does; Bluetooth works in any truck.
- **Setup walks the user through making the automation,** and checks that it fires, over leaving it optional or skipping it.
- **Driving noticed with no trip open: the same as Android.** With the truck's Bluetooth or CarPlay connected, the trip starts by itself, from where the truck was parked; without it, the "Driving? Start a trip" alert. Asked with it whether a trip's distance starts at the parked place when the drive-off sound comes about 30 seconds into a drive: it does, on Android and so on the iPhone (APP_ENCYCLOPEDIA, "Waiting beside the parked truck": the trip-start sound waits for a fix that shows the truck away at 15 km/h or more, and the trip's first point is the parked place).
- **The Live Activity on the Lock Screen and in the Dynamic Island at launch,** with the kilometres and a Stop button: the iPhone's form of Android's trip notification.

## Consequences

- Better: on the iPhone, a trip can start at least as reliably as Android's, if the automations are set up. Shortcuts' Bluetooth trigger is the closest the iPhone has to Android's companion device.
- Worse: it depends on the user making two automations. Some will not, so onboarding must make it short and check it, and layer 2 must be good enough on its own to keep most trips.
- Worse: "Always" location permission, which iPhone users are asked to confirm again from time to time, and which the App Store review asks to be justified (the reason is the safety net).
- Worse: layer 2 wakes on movement, which starts late: a significant-change update needs a few hundred metres. A trip it catches starts at the parked place by rule, but its first stretch has no fixes and is measured short.
- Locked into nothing yet. If the drives show a layer failing, this record changes before the release.
- Unknown until tried: whether the automation runs the intent with the phone locked in a pocket; how long iOS takes from connection to intent; how the audio route reads while CarPlay is connected; whether App Store review accepts the Shortcuts onboarding as it is.
