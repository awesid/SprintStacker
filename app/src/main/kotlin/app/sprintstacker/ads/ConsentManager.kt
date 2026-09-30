package app.sprintstacker.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * Wraps Google's User Messaging Platform. The consent form is shown on launch
 * where required (EEA, UK), before any ad is requested.
 */
class ConsentManager(activity: Activity) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)

    val canRequestAds: Boolean get() = info.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gather(activity: Activity, onDone: (FormError?) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error -> onDone(error) } },
            { error -> onDone(error) },
        )
    }

    fun showPrivacyOptions(activity: Activity, onDone: (FormError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> onDone(error) }
    }
}
