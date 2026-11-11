# ADR-013: Cloud backup is unlocked by a one-time purchase of $19.99; no ads

Date: 2026-11-11
Status: Accepted. Not built (phase 0 of the iPhone and cloud backup plan). The currency of the base price is to be confirmed.

## Context

Shawn, 2026-11-11: "i want to still make the app free. but when they select to upload to the server as a backup. They will than be served ads to make revenue." Told that a mileage tracker is opened rarely (it runs by itself in the truck), so banner ads would earn a few cents a user a month, and that ads would bring an ad library, consent screens and Apple's tracking prompt, he chose instead: "lets go no ads and just a paid option a one time fee 19.99? I think this is a fair price."

What weighs on the price:

- The backup holds no GPS points (ADR-012), so a user's data grows by well under 1 MB a year and its storage costs pennies a year. A one-time fee pays once for a cost that keeps going; that is safe only because the cost per user is this small.
- Google Play and the App Store require their own billing for a digital feature unlocked in the app. Both take 15 % at Dually's size: Google on the first US$1M of earnings a year without asking, Apple through its Small Business Program, which has to be joined.
- Most mileage trackers charge a subscription. "Pay once, no subscription, no ads" is a selling point of its own.
- ADR-004 answered the Play Console's ads question "No". That answer stays true.

## Decision

- **Dually stays free.** Every feature it has today stays free: trip detection, the trip log, reports, export and import, Android Auto, the widget.
- **Cloud backup is the one paid feature:** a one-time, non-consumable purchase of **$19.99**, named "Cloud backup", for the life of the app. The store pages and the app say "for the life of the app", never "forever".
- **No ads, anywhere, in any version.** No ad library is added.
- **Android:** Google Play Billing Library. **iPhone:** StoreKit 2. Each at its current stable version when it is added.
- **The purchase belongs to the account,** not to the phone or the store. A Cloud Function checks the purchase with the store that sold it (Google Play's Developer API; Apple's App Store Server API) and writes the unlock to the user's account. Firestore's security rules accept backup writes only from an account that holds the unlock (ADR-012). Someone who buys on Android and moves to an iPhone signs in and has backup there without paying again; Apple's rules allow that for a service bought elsewhere, as long as it can also be bought in the iPhone app.
- **Refunds:** the stores' notifications (Google Play's real-time developer notifications, Apple's App Store Server Notifications) reach a Cloud Function that removes the unlock. A refunded user's phone keeps every trip; uploading stops, and the cloud copy is deleted after 30 days with a notice in the app.
- **"Restore purchase"** is a button in the Cloud backup tile (Apple requires one).
- **Price per country:** set in each store from the base price, with the stores' own conversion. Sales tax is collected by the stores where they are the seller; what 2795748 Alberta Ltd. reports is a question for the accountant before launch.
- **Apple's Small Business Program** is joined before the first sale.

## Consequences

- Better: the app's privacy promise mostly stands: no ads, no analytics, no tracking. The one change is a backup people choose to buy.
- Better: one product, one price, simple to explain on the store pages and the website.
- Worse: revenue arrives once per user while server costs continue. Watched by the Firebase budget alert (ADR-012) and checked against real usage a few months after launch. If the cost per user ever outgrows the price, the price for new buyers can rise without touching anyone who has paid.
- Worse: two billing systems and the Cloud Functions that check them, each with its own testing (Play's licence testers, Apple's sandbox and StoreKit test files).
- Worse: the Play listing and the App Store listing show "In-app purchases".
- Locked into: a lifetime promise to buyers. Ending the backup service one day means notice, an export for every user, and possibly refunds.
