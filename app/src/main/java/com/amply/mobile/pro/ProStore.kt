package com.amply.mobile.pro

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProState(
    /** Owned on this Google account. Cached, so Pro keeps working offline. */
    val owned: Boolean = false,
    /** Localised price from Google Play, e.g. "₹299.00"; null until Play answers. */
    val price: String? = null,
    /** Play can sell Pro in this build (product found). False for sideloaded and emulator builds. */
    val available: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Amply Pro on Android: a one-time, non-consumable in-app product sold through Google Play Billing.
 * It is the counterpart of the desktop's Microsoft Store durable add-on: bought once per Google
 * account, restored automatically on reinstall or another phone signed in to the same account.
 *
 * Play Console needs an in-app product with id [PRODUCT_ID]; until the app is uploaded to a test
 * track with that product active, [ProState.available] stays false and the paywall says so.
 */
class ProStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("amply_pro", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var productDetails: ProductDetails? = null

    private val _state = MutableStateFlow(ProState(owned = prefs.getBoolean(KEY_OWNED, false)))
    val state: StateFlow<ProState> = _state

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Re-reads the price and what this account owns. Also the "Restore purchase" action. */
    fun refresh(userAsked: Boolean = false) {
        if (!client.isReady) {
            if (userAsked) _state.update { it.copy(message = "Google Play isn't available on this device.") }
            start()
            return
        }
        val productParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(productParams) { _, result ->
            val details = result.productDetailsList.firstOrNull()
            productDetails = details
            _state.update {
                it.copy(available = details != null, price = details?.oneTimePurchaseOfferDetails?.formattedPrice)
            }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { it.isPro() && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            purchases.filter { it.isPro() }.forEach(::acknowledgeIfNeeded)
            setOwned(owned)
            if (userAsked) {
                _state.update { it.copy(message = if (owned) "Amply Pro restored." else "No Amply Pro purchase on this Google account.") }
            }
        }
    }

    fun buy(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _state.update { it.copy(message = "Amply Pro can't be bought in this build yet. Install Amply from Google Play.") }
            refresh()
            return
        }
        _state.update { it.copy(busy = true) }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()))
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(busy = false, message = "Google Play couldn't start the purchase (${result.debugMessage}).") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _state.update { it.copy(busy = false) }
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().filter { it.isPro() }.forEach { purchase ->
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        acknowledgeIfNeeded(purchase)
                        setOwned(true)
                        _state.update { it.copy(message = "Welcome to Amply Pro. Thank you!") }
                    }
                    Purchase.PurchaseState.PENDING ->
                        _state.update { it.copy(message = "Your payment is pending. Pro unlocks as soon as it goes through.") }
                    else -> Unit
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(userAsked = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _state.update { it.copy(message = "The purchase didn't go through (${result.debugMessage}).") }
        }
    }

    /** Unacknowledged purchases are refunded by Google after three days. */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        scope.launch { client.acknowledgePurchase(params) { } }
    }

    private fun setOwned(owned: Boolean) {
        prefs.edit().putBoolean(KEY_OWNED, owned).apply()
        _state.update { it.copy(owned = owned) }
    }

    private fun Purchase.isPro() = PRODUCT_ID in products

    companion object {
        const val PRODUCT_ID = "amply_pro"
        private const val KEY_OWNED = "owned"
    }
}
