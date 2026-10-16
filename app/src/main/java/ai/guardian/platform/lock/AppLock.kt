package ai.guardian.platform.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Unlocks Guardian with fingerprint/face or the phone's own PIN/pattern, via the system prompt.
 * Uses the platform API (minSdk 29), so no extra library is needed.
 */
object AppLock {
    /** Re-lock after the app has been in the background this long. */
    const val RELOCK_AFTER_MS = 30_000L

    fun canUse(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
            },
        )
    }
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
