package com.onezcodes.ochords.services

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
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * O Chords Pro through Google Play Billing: one subscription product with a monthly and a yearly base plan
 * (set them up the same way in Play Console, with a 7-day free trial offer). Pro on this phone is whatever Play
 * says; the account is told about each purchase so Pro follows the player to their other devices.
 */
class StoreService(context: Context) : PurchasesUpdatedListener {
    companion object {
        const val productID = "ochords_pro"
        const val monthlyPlan = "monthly"
        const val yearlyPlan = "yearly"
    }

    /** One purchasable plan: a base plan (plus its trial offer when the player is eligible). */
    data class Plan(val id: String, val price: String, val priceMicros: Long, val currency: String, val offerToken: String, val hasTrial: Boolean, val details: ProductDetails)

    enum class PurchaseOutcome { Purchased, Cancelled, Pending }

    class StoreException(message: String) : Exception(message) {
        companion object {
            val unavailable get() = StoreException("Google Play isn’t available right now. Try again in a moment.")
            val failedVerification get() = StoreException("Google Play couldn’t confirm that purchase.")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()

    var plans by mutableStateOf<List<Plan>>(emptyList()); private set
    /** An active Pro subscription (or trial), as Play reports it on this phone. */
    var isPro by mutableStateOf(false); private set
    var purchaseToken: String? = null; private set
    var activeProductID: String? = null; private set
    var loaded by mutableStateOf(false); private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pendingPurchase: CompletableDeferred<PurchaseOutcome>? = null
    private var connected = false

    val yearly: Plan? get() = plans.firstOrNull { it.id == yearlyPlan }
    val monthly: Plan? get() = plans.firstOrNull { it.id == monthlyPlan }

    /** "save 33%" for the yearly plan against twelve monthly payments; null when the prices aren't known. */
    val yearlySavingPercent: Int?
        get() {
            val y = yearly?.priceMicros ?: return null
            val m = monthly?.priceMicros ?: return null
            if (m <= 0) return null
            val saving = ((1 - y.toDouble() / (m * 12)) * 100).toInt()
            return saving.takeIf { it > 0 }
        }

    private suspend fun connect(): Boolean {
        if (connected) return true
        val done = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { connected = result.responseCode == BillingClient.BillingResponseCode.OK; done.complete(connected) }
            override fun onBillingServiceDisconnected() { connected = false }
        })
        return withTimeoutOrNull(8000) { done.await() } ?: false
    }

    /** Loads the plans and the current entitlement. */
    suspend fun start() {
        if (!connect()) { loaded = true; return }
        loadProducts()
        refreshEntitlement()
        loaded = true
    }

    suspend fun loadProducts() {
        if (plans.isNotEmpty() || !connect()) return
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(productID).setProductType(BillingClient.ProductType.SUBS).build(),
        )).build()
        val result = runCatching { client.queryProductDetails(params) }.getOrNull() ?: return
        val details = result.productDetailsList?.firstOrNull() ?: return
        val offers = details.subscriptionOfferDetails ?: return
        plans = listOf(yearlyPlan, monthlyPlan).mapNotNull { planID ->
            val forPlan = offers.filter { it.basePlanId == planID }
            if (forPlan.isEmpty()) return@mapNotNull null
            // Prefer an offer with a free phase (the trial) when Play says the player is eligible for it.
            val chosen = forPlan.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: forPlan.first()
            val paid = chosen.pricingPhases.pricingPhaseList.first { it.priceAmountMicros > 0 }
            Plan(planID, paid.formattedPrice, paid.priceAmountMicros, paid.priceCurrencyCode, chosen.offerToken,
                hasTrial = chosen.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }, details = details)
        }
    }

    suspend fun purchase(activity: Activity, plan: Plan): PurchaseOutcome {
        if (!connect()) throw StoreException.unavailable
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(plan.details).setOfferToken(plan.offerToken).build(),
        )).build()
        val deferred = CompletableDeferred<PurchaseOutcome>()
        pendingPurchase = deferred
        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) { pendingPurchase = null; throw StoreException.unavailable }
        return deferred.await()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val pending = pendingPurchase
        pendingPurchase = null
        scope.launch {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases?.forEach { acknowledge(it) }
                    refreshEntitlement()
                    pending?.complete(if (purchases?.any { it.purchaseState == Purchase.PurchaseState.PENDING } == true) PurchaseOutcome.Pending else PurchaseOutcome.Purchased)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> pending?.complete(PurchaseOutcome.Cancelled)
                else -> pending?.completeExceptionally(StoreException.unavailable)
            }
        }
    }

    private suspend fun acknowledge(p: Purchase) {
        if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
            runCatching { client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) }
        }
    }

    /** "Restore purchases": re-reads this Google account's subscriptions. */
    suspend fun restore() { if (connect()) refreshEntitlement() }

    /** Re-reads the current entitlement from Play (purchased, non-pending subscriptions only). */
    suspend fun refreshEntitlement() {
        if (!connect()) return
        val result = runCatching { client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) }.getOrNull() ?: return
        val active = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(productID) }
        active.forEach { acknowledge(it) }
        val was = isPro
        val chosen = active.firstOrNull()
        isPro = chosen != null
        purchaseToken = chosen?.purchaseToken
        activeProductID = chosen?.products?.firstOrNull()
        if (was != isPro) onChange?.invoke(isPro)
    }
}
