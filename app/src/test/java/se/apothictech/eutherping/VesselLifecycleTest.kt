package se.apothictech.eutherping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VesselLifecycleTest {
    @Test
    fun `system picker does not close an active vessel`() {
        assertFalse(
            shouldRelockVessels(
                biometricGateEnabled = true,
                trustedActivityResultPending = true,
            ),
        )
    }

    @Test
    fun `ordinary backgrounding still relocks vessels`() {
        assertTrue(
            shouldRelockVessels(
                biometricGateEnabled = true,
                trustedActivityResultPending = false,
            ),
        )
    }

    @Test
    fun `disabled biometric gate does not relock vessels`() {
        assertFalse(
            shouldRelockVessels(
                biometricGateEnabled = false,
                trustedActivityResultPending = false,
            ),
        )
    }
}
