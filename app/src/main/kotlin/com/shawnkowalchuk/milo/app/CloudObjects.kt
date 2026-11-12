package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.data.cloud.CloudAccountFile
import com.shawnkowalchuk.milo.data.cloud.buildCloudAccountFile
import com.shawnkowalchuk.milo.feature.settings.CloudBackupViewModel
import com.shawnkowalchuk.milo.platform.cloud.CloudFirebase
import com.shawnkowalchuk.milo.platform.cloud.GoogleCloudSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The long-lived objects of the optional cloud backup (ADR-012): the way into Firebase, the
 * account that is signed in, and the sign-in. Creating them starts nothing: Firebase starts at
 * the first sign-in or sign-out (`CloudFirebase`).
 *
 * Like [WidgetObjects], a part of the [AppContainer], which creates it once
 * (`container.cloud`); a class of its own only because the container is at its size limit. None
 * of it can touch a trip.
 */
class CloudObjects(appContext: Context) {
    val firebase: CloudFirebase by lazy { CloudFirebase(appContext) }

    val accountFile: CloudAccountFile by lazy { buildCloudAccountFile(appContext) }

    val signIn: GoogleCloudSignIn by lazy { GoogleCloudSignIn(firebase, accountFile) }
}

/** How the Settings screen's Cloud backup tile gets its ViewModel. */
internal fun cloudBackupViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val cloud = container.cloud
            CloudBackupViewModel<Context>(
                available = cloud.firebase.isConfigured,
                readAccount = { withContext(Dispatchers.IO) { cloud.accountFile.read() } },
                signIn = cloud.signIn::signIn,
                signOut = cloud.signIn::signOut,
                eventLog = container.eventLogRepository,
                clock = container.clock,
            )
        }
    }
