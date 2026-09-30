package app.sprintstacker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.sprintstacker.ads.ConsentManager
import app.sprintstacker.game.AdRequest
import app.sprintstacker.game.GameViewModel
import app.sprintstacker.ui.GameScreen
import app.sprintstacker.ui.Palette
import app.sprintstacker.ui.SettingsScreen
import app.sprintstacker.ui.SprintStackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()
    private lateinit var consent: ConsentManager
    private var privacyOptionsRequired by mutableStateOf(false)

    /**
     * Watches the whole app, not just this activity, so rotating or opening an
     * ad does not count as leaving. A stop with the screen still on means the
     * user left; a stop with the screen off means it was only locked.
     */
    private val appLifecycle = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = vm.onAppStarted()
        override fun onStop(owner: LifecycleOwner) {
            val power = getSystemService(PowerManager::class.java)
            vm.onAppStopped(screenInteractive = power?.isInteractive ?: true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val ads = (application as SprintStackerApp).ads
        consent = ConsentManager(this)
        consent.gather(this) {
            privacyOptionsRequired = consent.privacyOptionsRequired
            if (consent.canRequestAds) ads.initialize()
        }
        // Consent from an earlier session lets ads start loading right away.
        if (consent.canRequestAds) ads.initialize()

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycle)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ads.collect { request ->
                    when (request) {
                        AdRequest.Interstitial -> ads.showInterstitial(this@MainActivity)
                        is AdRequest.Rewarded -> ads.showRewarded(
                            this@MainActivity,
                            onEarned = { vm.onRewardEarned(request.kind) },
                            onUnavailable = vm::onRewardUnavailable,
                        )
                    }
                }
            }
        }

        setContent {
            SprintStackerTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var showSettings by remember { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                Box(Modifier.fillMaxSize().background(Palette.Ink).safeDrawingPadding()) {
                    if (showSettings && state.sprint == null) {
                        SettingsScreen(
                            vibrate = state.vibrate,
                            privacyOptionsRequired = privacyOptionsRequired,
                            onVibrate = vm::setVibrate,
                            onPrivacyOptions = {
                                consent.showPrivacyOptions(this@MainActivity) {
                                    privacyOptionsRequired = consent.privacyOptionsRequired
                                }
                            },
                            onPrivacyPolicy = ::openPrivacyPolicy,
                            onResetAll = { vm.resetAllData(); showSettings = false },
                            onBack = { showSettings = false },
                        )
                    } else {
                        GameScreen(state, vm, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycle)
        super.onDestroy()
    }

    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
        } catch (e: ActivityNotFoundException) {
            // No browser installed; nothing else to do.
        }
    }
}
