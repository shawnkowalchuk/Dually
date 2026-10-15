# ADR-011: Trip distances carry a built-in +2 %, and Settings adjusts them ±5 % from 0
Date: 2026-10-14
Status: Accepted. Built; unit tests only, not yet on the phone (device checks DA-1 to DA-5).

## Context

Shawn: "We need to have a way to adjust the mileage in the app. A 5% +/- slider. I think we should have a baseline adjust on the app at +2% on all trips." Asked how it should look, he answered: "i want the base line to be 0. i dont want the app to know we do a 2% adjustment. than +-5%. i dont think the usere need to know the mileage is being adjusted only that the trips match closer to there trip. if it doesnt this is where the plus/minus from 0 will help balance the trips to the odomenter."

What the +2 % rests on. GPS distance reads short: a fix every 2 seconds cuts the corner of every turn. The simulations of 2026-10-09 (`DistanceCalculatorTurnsTest`, FINDINGS_LOG) put that at about 1.8 to 2 % on a town route with a turn every 300 m, and next to nothing on a highway. On 2026-10-09 Shawn had also found a drive short of his truck's own figure. **No measurement on his truck gives the 2 %:** it is the simulations' town figure.

Before building it, Shawn was told, and chose "Hidden +2% now" over the two other choices put to him ("Measure my truck first" from his typed odometer readings, and "No hidden number"):
- a flat +2 % over-counts highway driving, where GPS loses almost nothing, by about 2 %;
- for anyone who claims business kilometres, an adjustment nobody can see is an over-claim if their odometer shows less, and an audit compares a log with the odometer;
- his typed odometer readings could give his truck's real gap.

## Decision

1. **Every new trip's distance is multiplied by 1.02** where it is stored when the trip ends and where its running figure is shown while it is recorded (`core/trip/DistanceAdjustment.kt`, `BUILT_IN_PERCENT`). No screen, report, file or Log line names it. A trip's Log line keeps the distance that was measured.
2. **Settings' "Distance adjustment" starts at 0 %** and goes from −5 % to +5 % in 0.5 % steps, as a − / + row under "Shortest trip that counts" (the Settings screen's pattern for a stepped setting, not a new slider; Shawn's "slider" is met by it). Its percentage is added to the built-in one: at −2 % a trip is stored as measured. It applies to the trip being recorded and every trip after it.
3. **New trips only.** A trip that has ended keeps its stored distance; moving the setting changes no past trip, no month and no sent report (Shawn's choice).
4. **The trip rules never see it.** A trip is kept or discarded, and closed, on its measured distance: the minimum trip distance compares the measured one. A distance typed or changed by hand is stored as typed.
5. **Both surfaces** show the adjusted figure (Shawn's choice): Home, Trips, the report, the widget, the odometer and the Android Auto screen all read the stored or running figure.
6. **No note on the PDF** (Shawn's choice).

## Consequences

- Distances on new trips are 2 % longer than before this change, on every phone, including trips where GPS was already right (highways).
- **The repository is public** (since 2026-10-08): the built-in percentage and this record can be read by anyone on GitHub, whatever the app shows.
- The stored distance is the adjusted one, with no record of the factor on the trip. The measured distance is in the trip's Log line, and can be worked out again from the GPS points while they are kept; once a sent month's points are deleted, only the Log line has it.
- An edited trip's "Restore recorded values" restores the stored, adjusted figure.
- Changing the built-in percentage later changes no stored trip; only trips after the change.
- If Shawn's odometer readings later show a different gap, the constant is one number in one file.
