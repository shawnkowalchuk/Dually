package com.shawnkowalchuk.milo.platform.cloud

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.Source
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockResult
import com.shawnkowalchuk.milo.data.cloud.CloudUnlockServer
import com.shawnkowalchuk.milo.data.cloud.unlockAnswer
import com.shawnkowalchuk.milo.data.cloud.unlockRequestBody
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The purchase check's name on the server (firebase/functions/index.js). */
private const val UNLOCK_FUNCTION = "unlockBackup"
private const val DELETE_FUNCTION = "deleteMyAccount"

/** A callable function's request with no data. */
private const val EMPTY_CALL = "{\"data\":{}}"

private const val CONNECT_TIMEOUT_MS = 15_000

/** The purchase check asks Google Play, which can take a while. */
private const val READ_TIMEOUT_MS = 60_000

/**
 * The server's side of the unlock (ADR-013): the purchase check, and whether an account holds
 * the unlock. Only the server gives one; the app asks and reads.
 *
 * The purchase check is called directly over HTTPS (`data/cloud/CloudCallable.kt` says why),
 * with the account's Firebase ID token, which is how the server knows who is asking.
 */
class CloudUnlockCheck(private val firebase: CloudFirebase) : CloudUnlockServer {
    /** Sends the purchase [token] to the purchase check, for the account signed in. */
    override suspend fun unlock(token: String): CloudUnlockResult {
        val user =
            firebase.auth().currentUser
                ?: return CloudUnlockResult.Failed("Not signed in to Firebase")
        val idToken =
            try {
                user.getIdToken(false).await().token
            } catch (offline: FirebaseNetworkException) {
                return CloudUnlockResult.Offline
            } catch (failed: FirebaseException) {
                return CloudUnlockResult.Failed("ID token: ${failed.javaClass.simpleName}")
            } ?: return CloudUnlockResult.Failed("ID token: none")
        return withContext(Dispatchers.IO) { call(idToken, token) }
    }

    /**
     * "Delete my online backup and account" (since 2026-11-12): the server deletes the backup,
     * the unlock and the sign-in account, and releases the purchase (`deleteMyAccount`).
     *
     * @return true when the server says it is done; false when it could not be reached or
     * refused, and nothing may be taken as deleted.
     */
    suspend fun deleteAccount(): Boolean {
        val user = firebase.auth().currentUser ?: return false
        val idToken =
            try {
                user.getIdToken(false).await().token
            } catch (failed: FirebaseException) {
                null
            } ?: return false
        return withContext(Dispatchers.IO) {
            val connection =
                URL(firebase.functionUrl(DELETE_FUNCTION)).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                connection.outputStream.use { it.write(EMPTY_CALL.toByteArray(Charsets.UTF_8)) }
                connection.responseCode in 200..299
            } catch (offline: IOException) {
                false
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun call(idToken: String, token: String): CloudUnlockResult {
        val connection = URL(
            firebase.functionUrl(UNLOCK_FUNCTION),
        ).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.outputStream.use {
                it.write(unlockRequestBody(token).toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val answer = if (code in 200..299) connection.inputStream else connection.errorStream
            unlockAnswer(code, answer?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (offline: IOException) {
            CloudUnlockResult.Offline
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Whether the account [accountId] holds the unlock, asked of the server itself (the rules let
     * an account read its own), or null when it cannot be reached.
     */
    suspend fun holdsUnlock(accountId: String): Boolean? = try {
        firebase
            .firestore()
            .document("unlocks/$accountId")
            .get(Source.SERVER)
            .await()
            .exists()
    } catch (unreachable: FirebaseException) {
        null
    }
}
