package com.shawnkowalchuk.milo.platform.cloud

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.shawnkowalchuk.milo.data.cloud.PlayPurchase
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/** The backup's one-time product in Play Console (ADR-013), as the server knows it too. */
const val CLOUD_BACKUP_PRODUCT = "cloud_backup"

/**
 * Google Play Billing, for the backup's one-time unlock (ADR-013): its price, the purchase
 * sheet, and the purchases this phone's Google Play account holds. It decides nothing; what a
 * purchase comes to is `unlockAfter`, and only the server unlocks an account.
 *
 * Connects to Google Play the first time it is asked something, and stays connected.
 */
class PlayBilling(context: Context) {
    /** Where the sheet's answer goes; Google Play sends it to one listener for the whole app. */
    @Volatile
    private var waiting: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null

    private val client: BillingClient by lazy {
        BillingClient
            .newBuilder(context.applicationContext)
            .setListener { result, purchases -> waiting?.complete(result to purchases) }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .enableAutoServiceReconnection()
            .build()
    }

    /** The price as Google Play shows it to this user ("US$19.99", "CA$27.99"), or null. */
    suspend fun price(): String? = product()?.oneTimePurchaseOfferDetails?.formattedPrice

    /**
     * Shows Google Play's purchase sheet over [activity]. The purchase carries [accountTag]
     * (`cloudAccountTag`), so the server unlocks only the account that bought it.
     */
    suspend fun buy(activity: Activity, accountTag: String): PlayPurchase {
        val product = product() ?: return PlayPurchase.Unavailable
        val answer = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>()
        waiting = answer
        val params =
            BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams
                            .newBuilder()
                            .setProductDetails(product)
                            .build(),
                    ),
                ).setObfuscatedAccountId(accountTag)
                .build()
        val launched = client.launchBillingFlow(activity, params)
        if (launched.responseCode != BillingResponseCode.OK) return purchaseOf(launched, null)
        val (result, purchases) = answer.await()
        return purchaseOf(result, purchases)
    }

    /** The tokens of every paid purchase of the backup this phone's Google Play account holds. */
    suspend fun owned(): List<String> {
        if (!connected()) return emptyList()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(
                    BillingClient.ProductType.INAPP,
                ).build(),
            ) { _, purchases ->
                continuation.resume(
                    purchases
                        .filter { CLOUD_BACKUP_PRODUCT in it.products }
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .map { it.purchaseToken },
                )
            }
        }
    }

    private suspend fun product(): ProductDetails? {
        if (!connected()) return null
        val query =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(CLOUD_BACKUP_PRODUCT)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                ).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(query) { result, details ->
                continuation.resume(
                    details.productDetailsList.firstOrNull()
                        .takeIf { result.responseCode == BillingResponseCode.OK },
                )
            }
        }
    }

    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) {
                            continuation.resume(result.responseCode == BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        // Reconnected by the client itself (enableAutoServiceReconnection).
                    }
                },
            )
        }
    }

    private fun purchaseOf(result: BillingResult, purchases: List<Purchase>?): PlayPurchase {
        val paid =
            purchases.orEmpty().firstOrNull {
                CLOUD_BACKUP_PRODUCT in it.products
            }
        return when (result.responseCode) {
            BillingResponseCode.OK ->
                when (paid?.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> PlayPurchase.Bought(paid.purchaseToken)
                    Purchase.PurchaseState.PENDING -> PlayPurchase.Pending
                    else -> PlayPurchase.Failed("no purchase in the answer")
                }

            BillingResponseCode.USER_CANCELED -> PlayPurchase.Cancelled

            BillingResponseCode.ITEM_ALREADY_OWNED -> PlayPurchase.AlreadyOwned

            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.ITEM_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            -> PlayPurchase.Unavailable

            else -> PlayPurchase.Failed("response ${result.responseCode}")
        }
    }
}
