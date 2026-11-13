# Google Play data safety answers, and Apple's privacy label, with the optional online backup

> **For Shawn to enter by hand.** Nothing here is sent anywhere by itself: Play Console's Data safety form (Policy, App content, Data safety) and, later, App Store Connect's App Privacy page are filled in by you, in your browser. This file is the answer sheet. Written 2026-11-12, slice 1f of the online backup (ADR-012, ADR-013), from the code on the branch `app/cloud-backup`.
>
> **When to enter it:** with the first release that holds the online backup. The form describes the version on Google Play, so save it with that release, not before. Google asks for it again before the release can go out, because the app now has the INTERNET permission and sign-in.
>
> **Keep it true.** Anything more that is ever uploaded changes this file, the privacy page (`website/privacy.html`) and the form in the same change (ENGINEERING_STANDARDS §12).

## What the answers rest on

With the online backup **off** (the default), Dually sends nothing to Dually's server. The two ends of each trip go to the phone's own address lookup (Android's Geocoder) as before, and Android's own backup is Android's; both were how the form was answered before, and they do not change.

With the backup **bought and signed in**, these leave the phone, over HTTPS, to Firebase (Google, as Dually's processor) and Dually's purchase check:

| What | Where it comes from in the code | Play's data type |
|---|---|---|
| Each closed trip's start and end positions (latitude, longitude) and addresses | `ExportedTrip` in `data/transfer/ExportFormat.kt`, via `data/cloud/CloudDocuments.kt` | Location: **Precise location** |
| The Google account's email address (the sign-in) | Firebase Authentication | Personal info: **Email address** |
| The accountant's email address, if typed in Settings | `ExportedSettings.accountantEmail` | Personal info: **Email address** |
| The name printed on the report, if typed | `ExportedSettings.reportName` | Personal info: **Name** |
| The Firebase account id (and Google's id behind the sign-in) | Firebase Authentication; every Firestore request carries it | Personal info: **User IDs** |
| The purchase, its token's fingerprint, whether it was refunded | `firebase/functions/unlock.js` (`unlocks/`, `purchases/`) | Financial info: **Purchase history** |
| Trip labels, the company and vehicle words on the report | `ExportedTrip.label`, `ExportedSettings.reportCompany`, `reportVehicle` | App activity: **Other user-generated content** |
| The truck's Bluetooth address (and a trip's vehicle, by its Bluetooth address) | `ExportedTruck.address`, `ExportedTrip.vehicleAddress` | Device or other IDs |

Never uploaded: the GPS points along a route, the trip still being recorded, the event log, crash files, chosen sounds, odometer readings.

**Firebase's own collection**, from Google's "Data disclosure for Firebase products" page (read 2026-11-12): Firebase Authentication sends the IP address (to prevent abuse), a user-agent string and the Firebase app id; Cloud Firestore sends a user-agent string. Google says the user-agent data is not linked to a user, and Play's form has no IP-address type, so neither adds a row. Re-read that page before submitting: https://firebase.google.com/docs/android/play-data-disclosure

The last two rows (user-generated content, the Bluetooth address) are a careful reading, not something Google named: Google's examples of "Device or other IDs" include "MAC address", and a Bluetooth address is one, though of the truck and not of the phone. Declaring them costs nothing and cannot be called hiding. If you prefer to leave them out, say so and the privacy page stays as it is (it already names both).

## Play Console: Data safety form, page by page

### 1. Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS to Firebase and to Google Play) |
| Which of the following methods of account creation does your app support? | **OAuth** (Sign in with Google). The account exists only for the optional backup. |
| Delete account URL | **https://dually.top/delete-account** |
| Do you provide a way for users to request that some or all of their data is deleted, without requiring them to delete their account? | **No.** The online copy is deleted whole, with the account. (Deleting a trip on the phone marks it deleted in the backup, as on the phone; it does not remove it.) If you would rather answer Yes, the URL is the same page, and the email route on it covers a partial request. |
| Independent security review | **No** |

### 2. Data types: tick these, and nothing else

- Location: **Precise location**
- Personal info: **Name**, **Email address**, **User IDs**
- Financial info: **Purchase history**
- App activity: **Other user-generated content**
- Device or other IDs: **Device or other IDs**

### 3. For each ticked type

Every type gets the same first answers:

| Question | Answer |
|---|---|
| Is this data collected, shared, or both? | **Collected** (not shared: Google handles it as Dually's processor, and Play's form says a service provider acting on your behalf is not sharing) |
| Is this data processed ephemerally? | **No** (it is stored until deleted) |
| Is this data required for your app, or can users choose whether it's collected? | **Users can choose whether this data is collected** (only with the optional backup, which is off by default and bought) |

Then the purposes:

| Data type | Why is this user data collected? |
|---|---|
| Precise location | **App functionality** |
| Name | **App functionality** |
| Email address | **App functionality**, **Account management** |
| User IDs | **App functionality**, **Account management** |
| Purchase history | **App functionality** (it unlocks the backup and notices a refund) |
| Other user-generated content | **App functionality** |
| Device or other IDs | **App functionality** |

Never tick Analytics, Developer communications, Advertising or marketing, Fraud prevention, or Personalization.

### 4. Other answers on the same pages

- **Ads:** No (ADR-013: no ads in any version).
- **App access:** reviewers can use every feature without signing in. The online backup asks for a Google sign-in and a purchase; say so, and that a reviewer does not need it to review the app. If Google asks to test it, a licence tester account in Play Console can buy it without being charged.
- **Privacy policy:** https://dually.top/privacy (unchanged address; its words change with this release).

## Apple: App Privacy ("privacy nutrition label"), for later

For the iPhone app, once it exists (ADR-014). Apple's types are named differently; the answers say the same. Apple's iPhone sign-in will add **Sign in with Apple** beside Google (ADR-012), which collects the same kinds: an email address (possibly Apple's relay address) and a user id.

- **Do you or your third-party partners collect data from this app?** Yes.
- **Data types to declare:**
  - Location: **Precise Location**
  - Contact Info: **Name**, **Email Address**
  - Identifiers: **User ID**
  - Purchases: **Purchase History**
  - User Content: **Other User Content** (labels, the company and vehicle words)
  - Identifiers: **Device ID** only if you keep the Bluetooth-address row above; Apple defines Device ID as the device's own identifier, so leaving it out on iPhone is defensible.
- **For each type:**
  - Used for: **App Functionality** (and for Email Address and User ID, nothing else; Apple has no separate "account management" purpose).
  - **Linked to the user's identity:** Yes (it sits under their account).
  - **Used for tracking:** No.
- **Not collected:** Health, Financial Info other than purchases, Sensitive Info, Contacts, Browsing or Search History, Usage Data, Diagnostics (no crash reporting is sent), Other Data.
- **Account deletion:** Apple requires it in the app (the Online backup tile's "Delete my online backup and account"); the web page https://dually.top/delete-account covers someone without the phone.
