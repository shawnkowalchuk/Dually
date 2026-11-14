package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.core.text.sha256Hex
import kotlinx.serialization.Serializable

/**
 * The account the cloud backup is signed in to (ADR-012). Kept by each phone in a file of its
 * own (Android's `CloudAccountFile`, the iPhone's `IosCloudFiles`); shared since 2026-11-14.
 *
 * @param id Firebase's id of the account: the name of its folder in the cloud, and what the
 * ledger is kept for. It never changes for one Google or Apple account.
 * @param email what the Settings tile shows, so the user sees which account it is. Empty for a
 * Sign in with Apple that hid the address.
 * @param unlocked whether the account held the one-time unlock when the app last looked
 * (ADR-013). Uploads run only while it does; the server's rules decide in the end.
 * @param backingUp whether this phone is the one backing up to the account (since 2026-11-12,
 * `cloudStart`): true when it is, false while it waits for Restore here or Back up this phone,
 * and null before it has looked (a file written by builds 7 and 8).
 */
@Serializable
data class CloudAccount(
    val id: String,
    val email: String,
    val unlocked: Boolean = false,
    val backingUp: Boolean? = null,
)

/**
 * What an Android purchase carries to Google Play as its "obfuscated account id": the SHA-256 of
 * the account's id, in hex. The purchase check on the server computes the same and unlocks only
 * the account that made the purchase (firebase/functions/unlock.js, accountTag), without Google
 * Play seeing the account.
 */
fun cloudAccountTag(accountId: String): String = sha256Hex(accountId)

/**
 * What an iPhone purchase carries to Apple as its `appAccountToken` (ADR-016), which StoreKit
 * takes only as a UUID: the first 32 hex digits of [cloudAccountTag], written as one. The server
 * computes the same (firebase/functions/unlock.js, appleAccountToken).
 */
fun cloudAppleAccountToken(accountId: String): String {
    val hex = cloudAccountTag(accountId)
    return listOf(hex.substring(0, 8), hex.substring(8, 12), hex.substring(12, 16))
        .plus(hex.substring(16, 20))
        .plus(hex.substring(20, 32))
        .joinToString("-")
}
