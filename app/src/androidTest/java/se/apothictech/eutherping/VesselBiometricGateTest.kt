package se.apothictech.eutherping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VesselBiometricGateTest {
    @Test
    fun sealDefaultsEnabledAndPersistsExplicitChoice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().remove(VesselBiometricGate.PREFERENCE).commit()

        try {
            assertTrue(VesselBiometricGate.isEnabled(context))

            VesselBiometricGate.setEnabled(context, false)
            assertFalse(VesselBiometricGate.isEnabled(context))

            VesselBiometricGate.setEnabled(context, true)
            assertTrue(VesselBiometricGate.isEnabled(context))
        } finally {
            preferences.edit().remove(VesselBiometricGate.PREFERENCE).commit()
        }
    }
}
