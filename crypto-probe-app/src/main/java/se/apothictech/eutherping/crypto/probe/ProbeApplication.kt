package se.apothictech.eutherping.crypto.probe

import android.app.Application
import se.apothictech.eutherping.crypto.libsignal.LibsignalProvider

class ProbeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(LibsignalProvider().descriptor.implementationVersion == "0.99.4")
    }
}
