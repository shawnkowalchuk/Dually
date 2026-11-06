package com.shawnkowalchuk.milo.feature.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** The Play Store app's address for an app's page. */
internal fun playStorePage(packageName: String): String = "market://details?id=$packageName"

/** The same page on the web, for a phone without the Play Store app. */
internal fun playWebPage(packageName: String): String =
    "https://play.google.com/store/apps/details?id=$packageName"

/**
 * The tile above Buy me a coffee that opens Dually's page in the Play Store, where a review can
 * be left (Shawn's request of 2026-11-06, in his words). Drawn as the coffee tile is.
 *
 * It is a link to the store's page, not Google Play's review card: Google's rules forbid a
 * button that opens the card ([Google Play's review card] in APP_ENCYCLOPEDIA). It is shown in
 * every copy; the coffee tile is not shown in the copy from Google Play (ADR-004).
 */
@Composable
internal fun ReviewLinkTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var noStore by rememberSaveable { mutableStateOf(false) }
    Tile(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        press =
            TilePress(
                label = stringResource(R.string.settings_review_open),
                onClick = {
                    noStore =
                        !context.open(playStorePage(context.packageName)) &&
                        !context.open(playWebPage(context.packageName))
                },
            ),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        TileLinkLabel(stringResource(R.string.settings_review_label))
        Text(
            text = stringResource(R.string.settings_review_value),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_review_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (noStore) {
            StatusRow(
                label = stringResource(R.string.settings_review_no_store),
                status = RowStatus.PROBLEM,
            )
        }
    }
}

/**
 * Opens [address]. False if nothing on the phone opens it.
 *
 * Lint offers String.toUri() from core-ktx, as `CoffeeTile` says.
 */
@SuppressLint("UseKtx")
private fun Context.open(address: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
    true
} catch (nothing: ActivityNotFoundException) {
    false
}
