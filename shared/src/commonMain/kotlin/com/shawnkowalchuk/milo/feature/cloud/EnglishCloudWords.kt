package com.shawnkowalchuk.milo.feature.cloud

import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.text.formatText

// The Online backup tile's words in English, for the iPhone (phase 4h, 2026-11-14): Android
// takes them from its string resources (strings_cloud.xml). Word for word, with Android's
// escapes; EnglishReportWordsTest holds them to the resources. Words only the iPhone says, where
// Android names Google Play, are IosCloud's own.

/** The words' string resources, by their names in strings.xml, as the files hold them. */
val ENGLISH_CLOUD_STRINGS: Map<String, String> = mapOf(
    "settings_cloud_title" to "Online backup",
    "settings_cloud_explained" to
        "Keep a copy of every trip online, in Google\\'s Firebase in Montréal, Canada, without " +
        "its GPS points. One purchase, no subscription.",
    "settings_cloud_signed_in" to
        "Signed in as %1\$s. Your trips upload by themselves after each change, when the phone " +
        "is online. The Log has a line for each upload.",
    "settings_cloud_sign_out" to "Sign out",
    "settings_cloud_locked" to
        "Signed in as %1\$s. Bought it already, on this phone or another? Restore it.",
    "settings_cloud_purchase" to "Purchase online backup",
    "settings_cloud_purchase_price" to "Purchase online backup · %1\$s",
    "settings_cloud_on" to "On",
    "settings_cloud_restore" to "Restore purchase",
    "settings_cloud_unlocked" to "Backup unlocked. Your trips start uploading now.",
    "settings_cloud_pending" to
        "Payment pending. The backup unlocks when Google Play says it is paid; press Restore " +
        "purchase then.",
    "settings_cloud_not_offered" to "Google Play cannot sell the backup on this phone right now.",
    "settings_cloud_other_account" to
        "That purchase belongs to another Dually backup account. Sign in with the Google " +
        "account you used when you bought it.",
    "settings_cloud_refunded" to "That purchase was refunded, so it no longer unlocks the backup.",
    "settings_cloud_nothing_to_restore" to
        "No purchase of the backup was found in this phone\\'s Google Play account.",
    "settings_cloud_unlock_failed" to
        "Unlocking did not work. If you paid, the purchase is kept: try Restore purchase. The " +
        "Log says why.",
    "settings_cloud_no_account" to
        "This phone has no Google account to sign in with. Add one in the phone\\'s settings, " +
        "then try again.",
    "settings_cloud_play_not_ready" to
        "Google Play has not confirmed the purchase yet. It is kept: press Restore purchase " +
        "again in a few minutes.",
    "settings_cloud_offline" to
        "Google could not be reached. Check the phone is online and try again.",
    "settings_cloud_failed" to "Signing in did not work. Try again; the Log says why.",
    "settings_cloud_unavailable" to
        "This copy of Dually was built without the cloud backup\\'s settings.",
    "settings_cloud_merge_question" to
        "Signed in as %1\$s. Trips on this phone: %2\$d. Trips from your other phones: %3\$d. " +
        "Merge them into one logbook?",
    "settings_cloud_merge" to "Merge",
    "settings_cloud_use_backup" to "Use only the backup\\'s trips",
    "settings_cloud_use_backup_title" to "Use only the backup\\'s trips?",
    "settings_cloud_use_backup_text" to
        "This phone\\'s own trips are taken off it, and your logbook shows the trips from your " +
        "other phones. A safety copy of this phone\\'s trips is made first, under Your data.",
    "settings_cloud_use_backup_confirm" to "Use the backup\\'s trips",
    "settings_cloud_using_backup" to
        "This phone now shows the trips from your online backup, and backs up from now on.",
    "settings_cloud_merged" to
        "Merged. This phone\\'s trips back up from now on, and your other phones\\' trips show " +
        "here too.",
    "settings_cloud_delete" to "Delete my online backup and account",
    "settings_cloud_delete_title" to "Delete your online backup and account?",
    "settings_cloud_delete_text" to
        "Your trips are deleted from Dually\\'s server, and your Dually account with them. The " +
        "trips on this phone stay. Your purchase stays with your Google Play account: sign in " +
        "again later and press Restore purchase.",
    "settings_cloud_delete_confirm" to "Delete",
    "settings_cloud_trip_in_progress" to
        "Not while a trip is being recorded. End the trip, then try again.",
    "settings_cloud_restore_failed" to
        "That did not work; the trips on this phone were not changed. The Log says why.",
    "settings_cloud_backup_offline" to
        "Your online backup could not be reached. Check the phone is online and try again.",
    "settings_cloud_delete_failed" to
        "Your account could not be deleted. Check the phone is online and try again.",
    "settings_cloud_deleted" to
        "Your online backup and account are deleted. The trips on this phone stay.",
)

/** The plurals, by name: the "one" form, then the "other". */
val ENGLISH_CLOUD_PLURALS: Map<String, Pair<String, String>> = mapOf()

/** The resource [name], as it reads on the screen. */
fun englishCloudText(name: String): String = unescape(ENGLISH_CLOUD_STRINGS.getValue(name))

/** The resource [name] with its placeholders filled in by [args], in [locale]. */
fun englishCloudFormat(name: String, locale: Locale, vararg args: Any?): String =
    formatText(locale, englishCloudText(name), *args)

/** The plural [name] for [count], with its other placeholders filled in by [args]. */
fun englishCloudCount(name: String, count: Int, locale: Locale, vararg args: Any?): String {
    val (one, other) = ENGLISH_CLOUD_PLURALS.getValue(name)
    return formatText(locale, unescape(if (count == 1) one else other), *args)
}

private fun unescape(raw: String): String =
    raw.replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n")
