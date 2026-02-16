package com.example.therapytimer.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.therapytimer.util.PreferencesManager

/**
 * Product ID for the one-time "Pro" in-app product.
 * Must match the product ID created in Google Play Console (Monetize → In-app products).
 * In Play Console you can name the product "Therapy Timer Pro" or "Unlock Pro"; the ID stays "full_version_unlock".
 */
const val FULL_VERSION_PRODUCT_ID = "full_version_unlock"

class BillingManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    /** Optional callback when a purchase completes and full version is unlocked (e.g. to close paywall). */
    var onPurchaseSuccess: (() -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(FULL_VERSION_PRODUCT_ID)) {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    preferencesManager.setFullVersionUnlocked(true)
                    onPurchaseSuccess?.invoke()
                }
            }
        }
    }

    /**
     * Connect to Play Billing and query product details. Call this before showing the paywall.
     */
    fun startConnection(onReady: (Boolean) -> Unit) {
        if (billingClient != null) {
            if (productDetails != null) onReady(true) else queryProductDetails(onReady)
            return
        }
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails(onReady)
                } else {
                    onReady(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                onReady(false)
            }
        })
    }

    private fun queryProductDetails(onReady: (Boolean) -> Unit) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(FULL_VERSION_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                productDetails = productDetailsList.first()
                onReady(true)
            } else {
                onReady(false)
            }
        }
    }

    /**
     * Launch the purchase flow. Must be called from an Activity. Result is delivered via
     * [PurchasesUpdatedListener]; you can also pass [onResult] to get a simple callback.
     */
    fun launchPurchase(activity: Activity, onResult: ((Boolean) -> Unit)? = null) {
        val details = productDetails
        if (details == null) {
            onResult?.invoke(false)
            return
        }
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        val result = billingClient?.launchBillingFlow(activity, flowParams)
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            onResult?.invoke(false)
        }
        // If OK, purchase result comes in purchasesUpdatedListener; we can't easily map to onResult
        // without storing the callback, so for now we don't invoke onResult on success from here.
        onResult?.invoke(result?.responseCode == BillingClient.BillingResponseCode.OK)
    }

    /**
     * Restore purchases: query existing purchases and unlock if the user already bought.
     */
    fun restorePurchases(onResult: (Boolean) -> Unit) {
        if (billingClient == null) {
            startConnection { connected ->
                if (connected) queryPurchases(onResult) else onResult(false)
            }
            return
        }
        queryPurchases(onResult)
    }

    private fun queryPurchases(onResult: (Boolean) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onResult(false)
                return@queryPurchasesAsync
            }
            var found = false
            for (purchase in purchases) {
                if (purchase.products.contains(FULL_VERSION_PRODUCT_ID)) {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    preferencesManager.setFullVersionUnlocked(true)
                    found = true
                }
            }
            onResult(found)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(params) { _ -> }
    }

    /**
     * Get the price string for the product (e.g. "$2.99") for display. Returns null if not loaded.
     */
    fun getPriceString(): String? {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
        productDetails = null
    }
}
