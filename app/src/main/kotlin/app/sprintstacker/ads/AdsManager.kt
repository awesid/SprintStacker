package app.sprintstacker.ads

import android.app.Activity
import android.app.Application
import app.sprintstacker.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads and shows the two ad formats. Initialized only after UMP says ads may
 * be requested. Load and show calls happen on the main thread.
 */
class AdsManager(private val app: Application) {
    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch {
            withContext(Dispatchers.IO) { MobileAds.initialize(app) {} }
            loadInterstitial()
            loadRewarded()
        }
    }

    private fun loadInterstitial() {
        if (!initialized.get() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            app, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                }
            },
        )
    }

    private fun loadRewarded() {
        if (!initialized.get() || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(
            app, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    loadingRewarded = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                }
            },
        )
    }

    /** Shows an interstitial if one is ready; otherwise skips it quietly. */
    fun showInterstitial(activity: Activity) {
        val ad = interstitial ?: return loadInterstitial()
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadInterstitial()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = loadInterstitial()
        }
        ad.show(activity)
    }

    /**
     * Shows a rewarded video. [onEarned] runs after the ad closes, only if the
     * viewer watched long enough to earn the reward.
     */
    fun showRewarded(activity: Activity, onEarned: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded ?: run {
            loadRewarded()
            onUnavailable()
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (earned) onEarned()
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onUnavailable()
                loadRewarded()
            }
        }
        ad.show(activity) { earned = true }
    }
}
