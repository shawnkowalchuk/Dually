package com.shawnkowalchuk.milo.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// The purchase check is a Firebase "callable" function (firebase/functions/index.js). Firebase's
// client library for calling one brings Google's old Instance ID and Cloud Messaging with it,
// which Dually has no use for (FINDINGS_LOG, 2026-11-11), so the one call is made directly, by
// the protocol Firebase documents: a POST of {"data": ...} with the account's ID token, answered
// by {"result": ...} or {"error": {"status", "message"}}. Here is what is sent and how the answer
// reads, as plain code the tests can run; platform/cloud/CloudUnlockCheck.kt does the sending.

private val callableJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class UnlockRequest(val data: UnlockData)

@Serializable
private data class UnlockData(val purchaseToken: String)

@Serializable
private data class CallableAnswer(val error: CallableError? = null)

@Serializable
private data class CallableError(val status: String = "", val message: String = "")

/** The body of the call to the purchase check, for the purchase [token]. */
fun unlockRequestBody(token: String): String =
    callableJson.encodeToString(UnlockRequest(UnlockData(token)))

/**
 * What the purchase check's answer means: its HTTP status [code] and its [body]. The server's
 * reason for a refusal is the error's message (firebase/functions/unlock.js).
 */
fun unlockAnswer(code: Int, body: String): CloudUnlockResult {
    if (code in 200..299) return CloudUnlockResult.Unlocked
    val error =
        try {
            callableJson.decodeFromString<CallableAnswer>(body).error
        } catch (unreadable: SerializationException) {
            null
        } catch (unreadable: IllegalArgumentException) {
            null
        }
    return when {
        error?.message == "other-account" -> CloudUnlockResult.OtherAccount
        error?.message == "pending" -> CloudUnlockResult.Pending
        error?.message == "refunded" -> CloudUnlockResult.Refunded
        error?.status in setOf("UNAVAILABLE", "DEADLINE_EXCEEDED") -> CloudUnlockResult.Offline
        error == null && code >= 500 -> CloudUnlockResult.Offline
        error == null -> CloudUnlockResult.Failed("Purchase check: HTTP $code")
        else -> CloudUnlockResult.Failed("Purchase check: ${error.status}, ${error.message}")
    }
}
