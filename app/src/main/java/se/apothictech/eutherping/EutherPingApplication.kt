package se.apothictech.eutherping

import android.app.Application
import android.util.Log
import se.apothictech.eutherping.secure.SecureRatchetRuntime

class EutherPingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSounds.initialize(this)
        SecureRatchetRuntime.start(this) { error ->
            Log.e("EutherPingRatchet", "Always-on ratchet initialization failed", error)
        }
    }
}
