# ADR-009: MilO asks Google Play for its review card, with Google's own library
Date: 2026-10-11
Status: Accepted (built and unit tested; run on an emulator with the real library and the real Play Store app, in the debug build and in the minified release build, where Google Play was asked and showed nothing; **the card itself has not been seen anywhere,** and can only be seen in a copy installed from Google Play)

## Context

MilO is on Google Play as a second channel since 2026-10-09 (ADR-004). On 2026-10-11 Shawn asked: "also i would like three google play prompts for a review. one after 5 trips recoreded, 100 trips recorded and after submitting a report". Asked which of the two ways of recording a report as sent he meant, he chose the answer "I sent it" after the email app, not "Mark as sent".

There are two ways for an app to ask for a review on Google Play.

- **A link to its page in the Play Store.** It needs a button of MilO's ("Rate MilO"), it takes the person out of MilO, and few people who follow it come back having written anything.
- **Google Play's In-App Review API.** The app asks Google Play to show a small card of Google Play's own over the app: five stars and a line of text, sent without leaving the app. It is the only way to that card, and it needs Google's library.

What Google says about the card, in this document's words (developer.android.com, "Google Play In-App Reviews API", with its pages for Kotlin and for testing, read on 2026-10-11):

- **The app asks; Google Play decides.** Google Play limits how often a person is shown the card. Its own example: an app that asks more than once within a month may find that nothing is shown. The limit is not published and can change without notice.
- **The app learns nothing.** Nothing comes back that says whether the card was shown, or whether a review was written.
- **No question first.** The app is to ask the person nothing before the card or with it: not whether they like the app, and not whether they would rate it well.
- **No button that promises it.** The person may have reached the limit, and the button would do nothing. For a button, Google says to link to the Play Store instead.
- **The card as it is:** shown without changing its size, opacity or shape, with nothing of the app's drawn over it or round it, on the topmost layer, and never closed by the app.
- **Not too often,** and only once the person has used the app enough to have something to say about it.
- **Where it can appear:** the app must be in the account's Google Play library, which means installed from Google Play at least once by that account.

MilO has had no library of Google Play's until now, no INTERNET permission, and a promise on its website that it sends nothing by itself.

## Decision

1. **The In-App Review API, through Google's library:** `com.google.android.play:review` 2.0.2, pinned in `gradle/libs.versions.toml`. It is the one new dependency. **Without its Kotlin extensions,** `review-ktx`, which the work was first set to use: with them the release build does not get through R8 ("What the library is", below). A link to the Play Store page was not chosen: Shawn asked for prompts, and a prompt that leads out of the app is the weaker one. A link could still be added to Settings one day as a button, which is what Google recommends for a button; it would work in every copy of MilO.
2. **Three moments, each asked for once, ever** (`core/review/ReviewRules.kt`):
   - **5 recorded trips,** and **100 recorded trips.** A recorded trip is one that counts on the Trips screen (finished: not being recorded, not discarded, not deleted) and that MilO recorded itself. A trip typed in by hand is one MilO missed, and does not count. Business and Personal both count.
   - **A report recorded as sent with "I sent it",** after the email app. Not "Mark as sent".
3. **Never while he may be driving.** The two numbers of trips are acted on the next time Home is in front with no trip being recorded **and no paired vehicle connected,** and not while something else has that moment: the first start's page, Setup or any screen on top of Home, the opening greeting, the one-year party, What's new about to open, a tapped notification. The report's moment is acted on on the Report screen, a second after "I sent it" has been stored, under the same rule about driving; if it is held back there, it has passed, and comes again with the next report. Each waits until the screen has been in front for a second.
4. **Never twice in one day,** by MilO's own clock (ADR-006), whatever came of the first time. A MilO that is updated with 20 trips stored has reached "5 trips" and asks once at its next fitting moment. One with 150 has reached both numbers: it asks once, and both count as asked.
5. **Only a copy of MilO that Google Play installed asks** (`installedFromGooglePlay`, the test ADR-004 made for the coffee tile). Any other copy (the APK from GitHub, a build from the Mac) asks nothing and uses nothing up: if Google Play later updates that copy, it asks then.
6. **Launched is what counts as asked.** From the moment MilO has launched Google Play's flow, the moment counts as asked, whether or not Google Play then shows its card: MilO cannot know. If Google Play gives no flow (no Play Store, an error, no answer within 10 seconds), nothing counts, and MilO tries again at the next fitting moment, not before the next day.
7. **One line in the event log for each time MilO goes to Google Play,** and one for a moment that is reached and not asked for (`platform/review/ReviewLogText.kt`). Every such line begins "Review card:".
8. **Phone only.** Nothing on the Android Auto screen, the widget or a notification. **No switch in Settings:** it happens three times in the life of an installation.
9. **Nothing of MilO's is drawn for it.** No question before the card, no button, nothing over it. The one question that does come before the report's moment is "Did you send it?", which is about the email and was there before; the card follows its answer by a second, when the question has closed.
10. **One place in the code is kept for something to be played before the card** (`CelebrationThenCard` in `app/ReviewObjects.kt`): a short animation of the mascot is planned for the numbers of trips. Whatever is played there comes before Google Play is asked, never with the card, and must ask and promise nothing about a review (Google's rules above).

## What the library is, and what it brings into the app

- **Two small libraries:** `review` 2.0.2 (178 KB), which brings `core-common` 2.0.4 (143 KB). Everything else they name was in the build already, through the location library: Play services' `basement` and `tasks`. The release build keeps only what MilO uses of them (R8, ADR-008).
- **No permission.** The two declare none, and MilO still has no INTERNET permission: the release build's manifest lists the same 14 permissions as before. They add one entry to it: an activity of the library's, `PlayCoreDialogWrapperActivity`, not exported and see-through, which is what Google Play's card is shown through.
- **No rule for R8 was needed.** Neither library ships one, and the rules the release build is made with are line for line what they were. The activity is kept by the rule the build makes from the manifest. The minified release build was run with the library (below).
- **Not `review-ktx`, Google's Kotlin extensions for it.** They would let the library's two requests be awaited from a coroutine. With them in the build, R8 stops the release build: "Missing class com.google.android.gms.common.annotation.NoNullnessRewrite", named by one class of `review-ktx` 2.0.2 and held by no version of Play services on this Mac. The debug build and every unit test pass with them, because only the release build goes through R8: exactly the fault ADR-008 warns of. The way round it would have been a `-dontwarn` rule for a class nobody has. Instead the extensions were left out, and what MilO needed of them is fifteen lines in `platform/review/PlayReviewCard.kt` (`Task.answer`). `review` itself does not name the class.
- **The newest release, and not a new one.** 2.0.2 is the newest version on Google's Maven on 2026-10-11, and there is no beta. It was published on 2024-10-18, two years ago. It is Google's own library, its documentation page (last changed 2025-07-21) names this version, and there is no other way to the card; but it is not a library that is released often, and Dependabot will have little to offer for it.
- **Its licence is Google's "Play Core Software Development Kit Terms of Service"** (developer.android.com/guide/playcore/license), not an open-source licence. Using the library means agreeing to those terms.

## What it sends, and to whom

- **MilO's own code still sends nothing,** and has no way to: no INTERNET permission.
- **The library talks to the Play Store app on the phone,** not to the internet: it binds to that app's review service (seen on the emulator: `com.android.vending/com.google.android.finsky.inappreviewservice.InAppReviewService`) and asks for a review flow for MilO. So **Google Play learns that MilO asked.** MilO hands over nothing else: no trip, no position, no setting.
- **What a person enters on the card** (the stars, the text) goes from Google Play's own screen to Google Play, under their Google account, as it would in the Play Store. Google's own statement for the library says the same: what it collects is the rating and the text the person enters, for a review on the Play Store; the data is encrypted, becomes a public review, and can be deleted by the person in the Play Store.
- **Nothing comes back to MilO.**

The website's privacy page has a sentence for this (`website/privacy.html`), and the Play Console's data safety form may need one (README, "Publishing on Google Play").

## How it is tested

- **The rules run in unit tests,** with Google Play replaced by a stand-in (`ReviewCard` is an interface; `StandInCard` in the tests).
- **On an emulator the real library runs against the real Play Store app,** without any test code in MilO: a build installed with `adb install -i com.android.vending` counts as installed by Google Play. There the Play Store gave a flow every time, MilO launched it, and Google Play showed nothing, which is what Google's testing page says of an app that is not in the account's library. That shows everything of MilO's side: when it asks, what it counts, what it writes. It was run this way in the debug build and in the minified release build (FINDINGS_LOG, 2026-10-11).
- **Google's `FakeReviewManager` is not wired into any build.** It comes inside the library and shows no card either; the real library on the emulator showed the same and more. R8 removes it from the release build.
- **The card itself can only be seen in a copy installed from Google Play.** Google's way is the internal test track, where the limit on how often it appears is not enforced (`docs/DEVICE_TEST_CHECKLIST.md`, RV-1 to RV-9).

## How it is removed

Take `play-review` out of `gradle/libs.versions.toml` and `app/build.gradle.kts`, and delete `platform/review/PlayReviewCard.kt`, the only file that names the library. What is left is MilO's own and goes with it: `core/review/`, `platform/review/`, `feature/review/`, `app/ReviewObjects.kt`, the call of `ReviewWatch` in `MiloApp`, `container.review`, the argument `onSentByAnswer` in `MiloReportEntry` and `ReportRecords`, `data/settings/ReviewStorage.kt` with `MiloSettings.reviews`, and `TripDao.observeRecordedCount`. The four entries it leaves in a phone's settings file do no harm.

## Consequences

**Better**

- A person who uses MilO from Google Play can rate it in two taps, without leaving it, at a moment when MilO has just done something for them.

**Worse**

- **MilO has a library of Google Play's in it,** for the first time (until now ARCHITECTURE said "no Play SDK"), under Google's terms.
- **"Nothing leaves the phone unless you send it" has one more exception to explain:** Google Play is told that MilO asked.
- **MilO cannot know whether any of this works.** A moment counts as asked when the flow is launched. If Google Play shows nothing, the moment is spent all the same.
- **Google Play's own limit is longer than MilO's.** MilO never asks twice in a day; Google Play's own example is a month. A tradesman who reaches 5 trips in his first week and 100 in his third, or sends his first report soon after his fifth trip, will probably see one card and not two or three. This was known when "once a day" was decided for Shawn, and is left as it is (below).
- **The APK grows** by what the release build keeps of the library (FINDINGS_LOG, 2026-10-11, has the figure).
- **A copy that was sideloaded over one from Google Play asks nothing,** although Google Play could show the card there (the app is in the account's library). That is the price of decision 5, which keeps every other sideloaded copy from spending its three moments on a card that cannot appear.

**Locked in**

- Nothing. See "How it is removed".

**Left open, and Shawn's to decide**

- **A longer gap than a day between two requests,** to fit Google Play's limit: with 30 days, the second and third moments would wait for a time when the card can appear. It needs a second stored time (the last launch, apart from the last try) and is a change to one rule.
- **A "Rate MilO" button in Settings** that opens MilO's page in the Play Store, which Google allows where it forbids a button for the card.
- **A switch in Settings.** Not built: three requests in the life of an installation need none. If Google's review or a user asks for one, it is one stored value and one more condition in `judgeReview`.

## How it was decided

Shawn asked for the three moments and chose "I sent it". Everything else above was decided for him on 2026-10-11 and is listed in FINDINGS_LOG of that day ("What was decided for Shawn in the review card"), with the two places where the work went further than what had been set: a connected vehicle holds the card back as a trip does, and a trip typed in by hand does not count.
