# ADR-018: Several vehicles on the iPhone, matched with Android's by name

Date: 2026-11-16
Status: Accepted, built 2026-11-17 on `app/multi-phone` (FINDINGS_LOG of that day), not yet proven on a phone. Its rule for `vehicles` published 2026-11-17.

## Context

On Android a vehicle is its Bluetooth address (APP_ENCYCLOPEDIA, "Several vehicles", since 2026-10-08). The first is the truck under `truck_address`, the others are in `more_vehicles`; each trip records the address of the vehicle it was in (`trips.vehicleAddress`), each odometer reading names one (`OdometerReading.vehicle`), and a trip or reading that names none counts for the first vehicle (`core/odometer/Odometer.kt`).

The iPhone cannot read a Bluetooth address, or ask which device is connected. Its trips start from the user's Shortcuts automations (ADR-015), and today they name no vehicle, so on every phone they count for the first one. With one vehicle that is right, on both phones and across a move from the iPhone to Android: nothing needs matching.

The online backup shares odometer readings between an account's phones, by vehicle address (ADR-017, `shared/odometer`), but not the vehicles' names: those are each phone's own settings. The security rules allow three shared documents only: `report`, `service`, `odometer` (`firebase/firestore.rules`).

Shawn, 2026-11-16, asked how a start on the iPhone and a later Android phone would go ("so the ios no attached vehicle when paired on a android it would than have the name if the person paired or merged the data?"), then chose **"Match by name, ask once"** over keeping one vehicle on the iPhone and over deciding after testing.

## Decision

1. **A shared list of vehicles** in the backup, `users/{uid}/shared/vehicles`: each vehicle's id, its name, the phone kind that made it, and the ids joined to it. Android's id is the Bluetooth address; an iPhone's is its own (`iphone:` and a random identifier). Every phone uploads its vehicles there, as it does the odometer, and reads the others'.
2. **On the iPhone:** Settings has a Vehicles list. A vehicle is picked from the ones the account's Android phones paired (by name), or added by name. **Each vehicle has its own pair of automations**: the App Intents "Start a trip" and "End a trip" take the vehicle as a parameter, picked in the Shortcuts action, and Setup walks through one pair for each. A trip records the vehicle its connect named; each vehicle has its own odometer on Home and in Settings, and the report prints the vehicle as Android's does. With one vehicle nothing changes.
3. **On Android, asked once:** when a vehicle is paired whose name is the name of an iPhone-only vehicle in the shared list, Android asks "Is this the Ford from your iPhone?". Yes joins them: the shared list records the iPhone's id as one of the address's, and every trip and reading that names either id counts for the one vehicle. **No trip is rewritten:** each phone's trips stay as their phone recorded them (ADR-017 keeps a trip's home folder for life), and the join is read wherever a vehicle's trips are added up. No keeps them apart, and is not asked again for that pair.
4. **A trip or reading that names no vehicle** still counts for the first one, on both phones.

## Consequences

- Better: one Ford on both phones, its odometer counting every trip whichever phone recorded it; an iPhone-only fleet keeps its vehicles apart.
- Costs: a fourth shared document and its rule (`vehicles` added to the allow-list in `firestore.rules`), which **Shawn publishes**, as every rule change; Android's question at a pairing and the join in `Odometer.kt`; on the iPhone the vehicle list, an App Intent parameter (an `AppEntity` of the vehicles), Setup's rows per vehicle, each odometer, and the report. An export file carries the first vehicle only today (`[DEBT]`, 2026-10-08); the shared list does not change that.
- Built as decided, with two corners left (`[DEBT]`, FINDINGS_LOG 2026-11-17): Setup checks one pair of automations, not one per vehicle; the iPhone's service form names no vehicle.
- Open: names that differ ("Ford" and "F-150"): Android's vehicle list could offer "This is a vehicle from your iPhone…" to join by hand. To be asked when it is built.
