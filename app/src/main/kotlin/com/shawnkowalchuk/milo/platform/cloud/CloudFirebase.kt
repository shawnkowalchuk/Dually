package com.shawnkowalchuk.milo.platform.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.shawnkowalchuk.milo.BuildConfig

/**
 * Dually's way into Firebase, for the optional cloud backup (ADR-012), and the only one: nothing
 * else in the app names Firebase.
 *
 * Firebase's automatic start is removed from the manifest, so no Firebase code runs until [auth]
 * is first called, which is when the user signs in or when an account is already signed in. With
 * the backup off, Dually opens no connection.
 *
 * The four values come from the build (app/build.gradle.kts), which reads them from a file
 * outside the repository. A debug build made without that file has none: [isConfigured] is false
 * and the Settings tile says the backup is not in this build.
 */
class CloudFirebase(private val context: Context) {
    /** Whether this build carries the Firebase settings. */
    val isConfigured: Boolean =
        listOf(
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_APPLICATION_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_WEB_CLIENT_ID,
        ).none(String::isBlank)

    /** The web client of the project, which Google's sign-in issues its token for. */
    val webClientId: String = BuildConfig.FIREBASE_WEB_CLIENT_ID

    /**
     * Firebase Authentication, starting Firebase the first time it is asked for.
     *
     * @throws IllegalStateException in a build without the Firebase settings.
     */
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance(app())

    /**
     * Firestore, starting Firebase the first time it is asked for. Its cache is kept in memory
     * only: the backup reads nothing back, and a write that did not reach the server must not
     * wait on the disk to be sent later behind the ledger's back (`CloudLedger`).
     *
     * @throws IllegalStateException in a build without the Firebase settings.
     */
    fun firestore(): FirebaseFirestore = synchronized(this) {
        firestore ?: FirebaseFirestore
            .getInstance(app())
            .apply {
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                }
            }.also { firestore = it }
    }

    private var firestore: FirebaseFirestore? = null

    private fun app(): FirebaseApp = synchronized(this) {
        check(isConfigured) { "This build has no Firebase settings (app/build.gradle.kts)." }
        FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions
                    .Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build(),
            )
    }
}
