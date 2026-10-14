# ADR-010: MilO becomes Dually Trip Tracker: a new name, a new look, a new application ID
Date: 2026-10-13
Status: Accepted. The app's side is built on a branch and has run on an emulator; nothing is released, nothing has been on the phone, and three things it depends on are still open: the value of the new application ID, the domain, and the new entry in the Play Console ("What has to be true before a release", below).

## Context

Google Play refused the app's store listing under its **Impersonation policy**. It named three things: the app icon (a black M on a lime square), the app name ("MilO Trip Log"), and the listing's screenshots, which show the lime interface with the M mark and "MilO" in its top line. Another company's app in the same shop, "Milo Track - Mileage Tracking", has a similar name and does the same job.

Shawn decided to rebrand rather than argue: "we need to rebrand the app ... to comply", and later "the rebrand is a must. i think it will fix everything. and i actaully really like the look". He gave the name ("So the first thing we will do is change the name to Dually Trip Tracker.") and a design canvas for the look ("here is the theme and color and design ... we will start with the website and app do not change the maskot tell we review together").

Three more things were settled the same day, each after the work had begun:

- **The domain.** Shawn: "i will register a new domain name Dually.top". It was not registered yet when this was written.
- **The repository** was renamed on GitHub from `shawnkowalchuk/MilO` to `shawnkowalchuk/Dually`. GitHub sends the old address on.
- **The Play Console entry was deleted** by Shawn, to be created anew. A deleted entry's application ID cannot be counted on to be free again, so the rebranded app gets a new one. Its value is his to choose and had not been chosen when this was written.

## Decision

### 1. The name

- **"Dually Trip Tracker"** is the app's name in full. It stands where the app names itself in full: the shop's listing, the first start's page, the Version tile in Settings, the subject and the last line of the support email, and the foot of every page of the report.
- **"Dually"** is the name under the icon, in the top line of every screen, on the Android Auto screen, and in running text.
- **Nothing a user reads or hears says the old name:** no string, no notification or channel name, not the widget, the car screen, a line the app writes to its event log from now on, the PDF, the CSV, the name it suggests for a file, the email it prepares, what a screen reader is told, or the list of changes, whose older entries were reworded. A unit test holds this (`BrandNameTest`): it reads the string resources, the list of changes and every text between quotation marks in the app's Kotlin, and fails on "milo" in any spelling, which also finds the old website's address. The line in What's new that tells a person how to bring their trips across speaks of "the app under its earlier name" for that reason.
- **What was written before stays written.** A line of the event log from before the rename, and a file made then, still say MilO on the phone that holds them.

### 2. The look

The owner's brand design, followed for colours, mark and type. The layout of every screen, and everything a screen does, did not change.

- **Colours.** Hi-Vis Orange `#FF5A12` is the accent, everything that was lime, with the page's dark for words on it (white on this orange is 3.1 to 1). Amber `#FFB648` is for what is to stand out without being the accent: what is waiting for the user, a small tag, and on the report the Business tile. Asphalt `#15120F` is the page and Bone `#FFF4E8` the words. The tiles, lines and quieter words are the values of the design's two drawings of the interface. The app is dark only, as before; the report is the design's light palette on white paper.
- **Two brand colours are on no screen:** Ember (too dark for words on a tile) and Redline, which the design keeps for gauges, "never a button or a warning". **What is wrong stays the first design's light red, and the Bluetooth and Android Auto tags stay blue and violet:** the brand draws no colour for those, and they have to be told apart from an orange accent. `ColorContrastTest` measures that they are.
- **The mark** is a dark "D" on a rounded square of the orange, the design's "Icon B, Monogram": the launcher icon with its monochrome layer, the top line of every screen, the notification icon (the square with the D cut out), the widget, the launch screen and the report. It is a drawn shape, one path, held to one form in all those places by `AppMarkTest`. **Since later the same day the launcher icon is the design's "Icon A, Face"** (Shawn, of that drawing: "what about using this for the app icon"): the mascot's two dials and his grin on the same orange, written by `tools/make_launcher_icon.py` into `ic_launcher_foreground.xml`; the "D" stays as the icon's layer for themed icons (`ic_launcher_monochrome.xml`) and everywhere else the mark stands.
- **Type.** Archivo for running text; Archivo extra bold at 112 percent width for headlines and the app's name; IBM Plex Mono at weight 600 for a figure that stands by itself (the kilometres of a tile, the odometer's wheels, a figure at the end of a list's row, the report's numbers) and for the event log's time and tags. The three font files and their two licences (SIL Open Font License 1.1) are in the repository; Sora and its licence left it.

### 3. What was not renamed, and why

These keep "milo" on purpose. It is a decision, not work that is owed.

| What | Why it stays |
|---|---|
| The Gradle `namespace` and every package: `com.shawnkowalchuk.milo` | It is where the code lives. Renaming 400 files changes nothing a user sees, and would collide with every other branch |
| Class, file and resource names: `MiloTheme`, `MiloApplication`, `milo_car_accent`, `Theme.MilO` | The same. No user sees one |
| The database file `milo.db`, the settings file and every key in it, the folders the app keeps its files in, the notification channels' ids | A new name would be a new, empty one: everything stored under the old name would be gone without a word. `StoredNamesTest` holds each |
| What the export file calls itself: `"milo-export"` | A file written before the rename says this and has to be read. It is the one way a person's trips get from the old app to the new one |
| The names of intents' actions and extras: `com.shawnkowalchuk.milo.action.…` | Internal, and every intent that carries one is aimed at one of the app's own classes by name, so two installs do not hear each other |
| The signing keystore, its alias and its Keychain entry: `milo.jks`, `milo`, `milo-keystore` | They are the key itself. The new entry in the Play Console is signed with the same key |
| The Firebase project `milotriplog` and the secret `FIREBASE_SERVICE_ACCOUNT_MILOTRIPLOG` | A Firebase project's id cannot be changed, and no visitor sees it (ADR-003, addendum) |
| The folder on the Mac, `~/Documents/MilO`, and Gradle's project name | Shawn's to rename when he likes; nothing depends on it |
| Comments in the code written before 2026-10-13 | They say MilO where they mean the app. They are changed with the code they stand beside, not in one sweep of 1,600 lines that no user would see |
| The findings log and the decision records before this one | They are history. Each carries one line at its top that says so |

### 4. The application ID changes (this reverses "the application ID stays")

The first plan for the rebrand kept the application ID, so that the rebranded app would be an update of the one people have. Shawn then deleted the app's entry in the Play Console, and a deleted entry's ID cannot be counted on to be free again. So:

- **The rebranded app has a new application ID: `top.dually.triptracker`.** Shawn created the entry "Dually Trip Tracker" in the Play Console with it on 2026-10-13, and it is written into the build the same night. MilO's was `com.shawnkowalchuk.milo`.
- **It is one value, in `app/build.gradle.kts`, and nothing repeats it.** The manifest takes it as `${applicationId}`, the code asks Android for it, and the device checklist calls it `$APP`. A Gradle property, `-Pmilo.applicationId=…`, builds the app under another ID without touching the file, for trying two installs side by side on an emulator.
- **A new ID is a new app.** On a phone that has MilO it installs beside it and starts with nothing in it. Android's backup of the old app is not restored into it.
- **The trips come across with "Export all data" in the old app and "Import data" in the new one.** Every closed trip, every GPS point and every report recorded as sent come across, and thirteen settings (the truck's name, the wait after a disconnect, the shortest trip, the connect sound's switch, the work schedule and what becomes of a trip outside it, the driving alert's switch, the report's name, company and vehicle, the accountant's address, the reminder's switch and day).
- **What does not come across is set again by hand:** the permissions and the truck's pairing; the odometer's reading; the time after which a parked truck ends a trip; the unit; the daily check; which sound each of the two sounds plays; the widget and its rate. **One thing cannot be typed in again:** the odometer's earlier readings with their days. The export format was not extended for any of this in this change.
- **Then the old app is removed,** or both hear the truck connect and each records the drive.

### 5. The mascot, rebranded apart

**Rebranded later on 2026-10-13, after Shawn's review of him:** orange, the brand's "D" on his chest that turns into a grin, and smoked black soles that light up amber as he steps. His pictures were drawn again and nothing else changed: his code, his greetings, his party, his run and their sounds are as they were (ADR-007, addendum of 2026-10-13). What follows is how this record first left him.

His pictures, his code, his greetings, his party, his run and their sounds are exactly as they were: the green figure with the "M" on his chest, now over an orange interface. Shawn reviews him separately ("do not change the maskot tell we review together"). Until then he is the one place a user still sees the old colour and the old letter.

### 6. The addresses

- **The website is `https://dually.top`** and the support address **`support@dually.top`**, in the app and in the documents that describe the product as it is. The domain was chosen on 2026-10-13 and not yet registered.
- **The repository is `github.com/shawnkowalchuk/Dually`** since the same day.

## What has to be true before a release

Nothing was released by this change. Before one is:

1. ~~**The new application ID is chosen**~~ Done 2026-10-13: `top.dually.triptracker`, in `app/build.gradle.kts` and as `APP=` at the top of the device checklist.
2. **`dually.top` is registered and pointed at the hosting,** and the rebranded website is live there (ADR-003's addendum says what that needs: a merge alone would put the new pages live at the old address).
3. **`support@dually.top` forwards,** and one real email sent to it has arrived. The app's Email support tile and the listing both give that address.
4. **The app is created anew in the Play Console** as "Dually Trip Tracker" with the new ID, and **the first upload is signed with Shawn's own key** (`milo.jks`), as before: the Console makes a key of its own when an app is created, and it has to be changed to his before anything is uploaded (README, "Publishing on Google Play").
5. **The listing is made from the new brand only:** the name, the icon, a feature graphic and screenshots that show the orange interface and the D. The old screenshots are the third thing Google named. The listing's website and privacy links are the new addresses.
6. **The mascot has been reviewed,** or the release goes out knowing that the app's greeting shows a green figure with an M. **Done on 2026-10-13:** he is rebranded (section 5); seeing him on the phone is device checks MR-1 to MR-5.
7. **The move has been done on Shawn's phone** (device checks RB-1 to RB-9) and the new look seen there (RB-10 to RB-19).

## Consequences

- Better: the three things Google named are gone from the app, and a test fails if the old name comes back in anything a user reads.
- Better: the application ID, which was written in one place by habit, is now in one place by rule, and the code was checked for anything that would break under another: nothing does.
- Worse: **the rebranded app is not an update.** Everyone who has MilO, which today is Shawn and whoever downloaded the APK from GitHub, moves by hand: an export, an import, the truck paired again, a handful of settings set again. The odometer's history does not move.
- Worse: for as long as both apps are on a phone and both are paired with the truck, each records every drive.
- Worse: the code says `milo` and the product says Dually. A reader has to know that, and the standards and the architecture now say so in their first lines.
- Worse: the download is 0.3 MB larger, all of it the two typefaces (18.88 MB where it was 18.57 MB, unsigned).
- Locked into: the brand's orange as the colour of "in order" as well as of "press here", because the accent was both. Whether a tick on orange reads as "all good" at a glance is for the phone to show (device check RB-13).
- Open, and not this record's to close: ~~the README's and the website's screenshots, which still show the old look~~ (done 2026-10-13: `website/screenshots/dually-*`, from the rebranded app with made-up trips); the name of GitHub's releases and their files from here on (`Dually-0.3.1.apk`); and whether the earlier releases, which carry the old name and icon, stay up.
