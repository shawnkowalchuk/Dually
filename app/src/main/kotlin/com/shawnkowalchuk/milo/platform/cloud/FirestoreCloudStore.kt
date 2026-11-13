package com.shawnkowalchuk.milo.platform.cloud

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Source
import com.shawnkowalchuk.milo.data.cloud.CLOUD_TRIPS
import com.shawnkowalchuk.milo.data.cloud.CloudDocument
import com.shawnkowalchuk.milo.data.cloud.CloudRefusedException
import com.shawnkowalchuk.milo.data.cloud.CloudStore
import com.shawnkowalchuk.milo.data.cloud.CloudUnreachableException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** How long one batch may take before the run stops and tries again later. */
private const val BATCH_TIMEOUT_MS = 60_000L

/** The field of a backup document that holds its JSON (firebase/firestore.rules). */
private const val JSON_FIELD = "json"

/**
 * The backup's documents in Firestore, under `users/{account}/` (ADR-012). Each document is one
 * field, [JSON_FIELD], holding the JSON the app wrote, exactly as fingerprinted: what the rules
 * check and what a restore reads. A batch is written in one commit, all or nothing.
 *
 * Firestore's errors become the two the backup knows: refused (the rules said no: no unlock, or
 * not signed in) and unreachable (everything else, tried again later). Their messages carry the
 * error's code only, since they go to the event log.
 */
class FirestoreCloudStore(private val firebase: CloudFirebase) : CloudStore {
    override suspend fun apply(
        account: String,
        documents: List<CloudDocument>,
        removals: List<String>,
    ) {
        val firestore = firebase.firestore()
        val batch = firestore.batch()
        removals.forEach { batch.delete(firestore.document("users/$account/$it")) }
        documents.forEach {
            batch.set(firestore.document("users/$account/${it.path}"), mapOf(JSON_FIELD to it.json))
        }
        try {
            withTimeout(BATCH_TIMEOUT_MS) { batch.commit().await() }
        } catch (slow: TimeoutCancellationException) {
            throw CloudUnreachableException("No answer in ${BATCH_TIMEOUT_MS / 1_000} s", slow)
        } catch (failed: FirebaseFirestoreException) {
            val message = "Firestore ${failed.code}"
            throw when (failed.code) {
                Code.PERMISSION_DENIED, Code.UNAUTHENTICATED ->
                    CloudRefusedException(message, failed)

                else -> CloudUnreachableException(message, failed)
            }
        }
    }

    /**
     * Every document of the account's backup, by its path under the account's folder: the
     * trips, and the backup folder's documents (the sent reports, the settings, the owner).
     * Read from the server, never from a cache, for a restore and before each upload.
     *
     * @throws CloudUnreachableException or [CloudRefusedException], as [apply] does.
     */
    suspend fun readAll(account: String): Map<String, String> {
        val firestore = firebase.firestore()
        return try {
            withTimeout(BATCH_TIMEOUT_MS) {
                listOf(CLOUD_TRIPS, "backup").flatMap { folder ->
                    firestore
                        .collection("users/$account/$folder")
                        .get(Source.SERVER)
                        .await()
                        .documents
                        .mapNotNull { document ->
                            document.getString(JSON_FIELD)?.let { "$folder/${document.id}" to it }
                        }
                }.toMap()
            }
        } catch (slow: TimeoutCancellationException) {
            throw CloudUnreachableException("No answer in ${BATCH_TIMEOUT_MS / 1_000} s", slow)
        } catch (failed: FirebaseFirestoreException) {
            val message = "Firestore ${failed.code}"
            throw when (failed.code) {
                Code.PERMISSION_DENIED, Code.UNAUTHENTICATED ->
                    CloudRefusedException(message, failed)

                else -> CloudUnreachableException(message, failed)
            }
        }
    }
}
