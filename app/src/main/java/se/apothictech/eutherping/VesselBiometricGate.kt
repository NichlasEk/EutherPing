package se.apothictech.eutherping

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log

internal enum class VesselBiometricAvailability {
    READY,
    NO_HARDWARE,
    NOT_ENROLLED,
    UNAVAILABLE,
}

internal data class VesselBiometricSession(
    val prompt: BiometricPrompt,
    val cancellation: CancellationSignal,
) {
    fun cancel() = cancellation.cancel()
}

internal object VesselBiometricGate {
    const val PREFERENCE = "vessel_biometric_gate"

    fun isEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREFERENCE, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREFERENCE, enabled)
            .apply()
    }

    @Suppress("DEPRECATION")
    fun availability(context: Context): VesselBiometricAvailability {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (context.getSystemService(BiometricManager::class.java).canAuthenticate()) {
                BiometricManager.BIOMETRIC_SUCCESS -> VesselBiometricAvailability.READY
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> VesselBiometricAvailability.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> VesselBiometricAvailability.NOT_ENROLLED
                else -> VesselBiometricAvailability.UNAVAILABLE
            }
        } else {
            val fingerprint = context.getSystemService(FingerprintManager::class.java)
            when {
                !fingerprint.isHardwareDetected -> VesselBiometricAvailability.NO_HARDWARE
                !fingerprint.hasEnrolledFingerprints() -> VesselBiometricAvailability.NOT_ENROLLED
                else -> VesselBiometricAvailability.READY
            }
        }
    }

    fun authenticate(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ): VesselBiometricSession? {
        when (availability(activity)) {
            VesselBiometricAvailability.NO_HARDWARE -> {
                onFailure("This phone has no supported biometric sensor.")
                return null
            }
            VesselBiometricAvailability.NOT_ENROLLED -> {
                onFailure("Enroll a fingerprint in Android Settings before unlocking Vessels.")
                return null
            }
            VesselBiometricAvailability.UNAVAILABLE -> {
                onFailure("Android's biometric service is temporarily unavailable.")
                return null
            }
            VesselBiometricAvailability.READY -> Unit
        }

        val cancellation = CancellationSignal()
        val executor = activity.mainExecutor
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Secure Vessels")
            .setSubtitle("Authenticate with your enrolled biometric")
            .setDescription("EutherPing keeps the secure lane locked when the app leaves the foreground.")
            .setNegativeButton("CANCEL", executor) { _, _ -> onFailure("Vessel unlock cancelled.") }
            .build()
        prompt.authenticate(
            cancellation,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Log.i("EutherPingBiometric", "Secure Vessels biometric accepted")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.i("EutherPingBiometric", "Secure Vessels biometric error $errorCode: $errString")
                    onFailure(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    Log.i("EutherPingBiometric", "Secure Vessels biometric did not match")
                    onFailure("Fingerprint not recognized. Try again.")
                }
            },
        )
        return VesselBiometricSession(prompt, cancellation)
    }
}
