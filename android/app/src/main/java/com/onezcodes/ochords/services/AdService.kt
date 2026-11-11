package com.onezcodes.ochords.services

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.onezcodes.ochords.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google AdMob for the free plan: an adaptive banner, an interstitial after finished sessions, and rewarded ads
 * (an extra scan, a refill of strings). IDs come from BuildConfig (local.properties; Google's test units in Debug).
 * With no app ID the app shows its own placeholders, so demo builds never load real ads.
 * "Ads that fit me" off = non-personalised requests (`npa`).
 */
class AdService(private val context: Context) {
    data class Units(val appID: String, val banner: String, val interstitial: String, val rewarded: String)

    companion object {
        val units: Units? = Units(BuildConfig.ADMOB_APP_ID.trim(), BuildConfig.ADMOB_BANNER.trim(), BuildConfig.ADMOB_INTERSTITIAL.trim(), BuildConfig.ADMOB_REWARDED.trim())
            .takeIf { it.appID.isNotEmpty() }
        val isConfigured: Boolean get() = units != null
        val isTestMode: Boolean get() = units?.appID?.startsWith("ca-app-pub-3940256099942544") == true

        /** The adaptive banner's height for a width in dp (50–90 dp); the placeholder's height when ads aren't configured. */
        fun bannerHeight(context: Context, widthDp: Int): Int {
            if (!isConfigured) return 58
            val h = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp).height
            return if (h > 0) h else 58
        }
    }

    var started by mutableStateOf(false); private set
    var personalised = true
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    /** Starts the SDK once (after the ad-consent choice, so the first request already carries it). */
    suspend fun start() {
        if (!isConfigured || started) return
        withContext(Dispatchers.IO) { MobileAds.initialize(context) }
        started = true
    }

    fun request(): AdRequest {
        val b = AdRequest.Builder()
        if (!personalised) b.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") })
        return b.build()
    }

    /** Loads the next interstitial in the background so it shows without a wait. */
    fun preloadInterstitial() {
        val unit = units?.interstitial ?: return
        if (interstitial != null) return
        InterstitialAd.load(context, unit, request(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null }
        })
    }

    /** Shows the loaded interstitial; false when none is ready (the caller shows nothing). */
    fun showInterstitial(activity: Activity): Boolean {
        val ad = interstitial ?: return false
        interstitial = null
        ad.show(activity)
        preloadInterstitial()
        return true
    }

    /** Loads and shows a rewarded ad. Resolves true when the player earned the reward. */
    suspend fun showRewarded(activity: Activity): Boolean {
        val unit = units?.rewarded ?: return false
        start()
        if (rewarded == null) {
            val loaded = CompletableDeferred<RewardedAd?>()
            RewardedAd.load(context, unit, request(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { loaded.complete(ad) }
                override fun onAdFailedToLoad(error: LoadAdError) { loaded.complete(null) }
            })
            rewarded = loaded.await()
        }
        val ad = rewarded ?: return false
        rewarded = null
        val done = CompletableDeferred<Boolean>()
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { done.complete(earned) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { done.complete(false) }
        }
        ad.show(activity) { earned = true }
        return done.await()
    }
}
