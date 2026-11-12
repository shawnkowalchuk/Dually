package com.shawnkowalchuk.milo.platform.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.GoogleAuthProvider
import com.shawnkowalchuk.milo.data.cloud.CloudAccount
import com.shawnkowalchuk.milo.data.cloud.CloudAccountFile
import com.shawnkowalchuk.milo.data.cloud.CloudSignInResult
import java.io.IOException
import kotlinx.coroutines.tasks.await

/**
 * Signs the cloud backup in to a Google account, and out again (ADR-012).
 *
 * Android's Credential Manager shows Google's own "Sign in with Google" sheet, which hands back
 * a token Google signed for Dually's Firebase project; Firebase Authentication takes that token
 * and answers with the account's Firebase id. Dually never sees a password. The account is then
 * kept in [accountFile], which is how the app knows, at its next start, that Firebase is wanted.
 *
 * Google accepts the sheet's answer only for an app signed with a key whose fingerprint is
 * registered in the Firebase project: Dually's one key (FINDINGS_LOG, 2026-11-11). A build
 * signed with the throwaway debug key gets a refusal, which shows as [CloudSignInResult.Failed].
 */
class GoogleCloudSignIn(
    private val firebase: CloudFirebase,
    private val accountFile: CloudAccountFile,
) {
    /**
     * Shows Google's sheet and signs in with the account picked there.
     *
     * @param context the activity the sheet is shown over.
     */
    suspend fun signIn(context: Context): CloudSignInResult {
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(firebase.webClientId).build(),
                )
                .build()
        val credential =
            try {
                CredentialManager.create(context).getCredential(context, request).credential
            } catch (closed: GetCredentialCancellationException) {
                return CloudSignInResult.Cancelled
            } catch (none: NoCredentialException) {
                return CloudSignInResult.NoGoogleAccount
            } catch (refused: GetCredentialException) {
                return CloudSignInResult.Failed("Google's sheet: ${refused.type}")
            }
        val unexpected = CloudSignInResult.Failed("Google's sheet answered with ${credential.type}")
        if (credential !is CustomCredential) return unexpected
        val google =
            try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (unreadable: GoogleIdTokenParsingException) {
                return unexpected
            }
        val user =
            try {
                firebase
                    .auth()
                    .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                    .await()
                    .user
            } catch (offline: FirebaseNetworkException) {
                return CloudSignInResult.Offline
            } catch (refused: FirebaseException) {
                return CloudSignInResult.Failed("Firebase: ${refused.javaClass.simpleName}")
            } ?: return CloudSignInResult.Failed("Firebase: no account")
        val account = CloudAccount(id = user.uid, email = user.email ?: google.email.orEmpty())
        try {
            accountFile.write(account)
        } catch (unwritable: IOException) {
            // Not kept, so not signed in: the next start of the app would not know of it.
            firebase.auth().signOut()
            return CloudSignInResult.Failed("Account file: ${unwritable.javaClass.simpleName}")
        }
        return CloudSignInResult.SignedIn(account)
    }

    /**
     * Signs out of Firebase, tells Credential Manager to forget the choice, so the sheet asks
     * which account next time, and forgets the account. Done on this phone even without network.
     */
    suspend fun signOut(context: Context) {
        accountFile.clear()
        if (firebase.isConfigured) firebase.auth().signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (notCleared: ClearCredentialException) {
            // Only the sheet's memory of the last choice: signing out is done without it.
        }
    }
}
