package se.apothictech.eutherping.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsParticipantSelectionTest {
    @Test
    fun `GrapheneOS canonical thread drops inferred own address with equivalent formatting`() {
        assertEquals(
            listOf("sender-address"),
            filterKnownOwnMmsParticipants(
                canonicalParticipants = listOf("sender-address", "DEVICE-ADDRESS"),
                ownNumberCandidates = listOf("device address"),
            ) { left, right ->
                left.filter(Char::isLetterOrDigit).lowercase() ==
                    right.filter(Char::isLetterOrDigit).lowercase()
            },
        )
    }

    @Test
    fun `incoming one to one MMS omits its sole to address when own number is unavailable`() {
        assertTrue(
            shouldOmitSoleIncomingMmsRecipient(
                incoming = true,
                toAddressCount = 1,
            ),
        )
    }

    @Test
    fun `incoming group MMS keeps multiple to addresses for fallback resolution`() {
        assertFalse(
            shouldOmitSoleIncomingMmsRecipient(
                incoming = true,
                toAddressCount = 2,
            ),
        )
    }

    @Test
    fun `sole incoming to address is self even when SIM exposes an own number`() {
        assertTrue(
            shouldOmitSoleIncomingMmsRecipient(
                incoming = true,
                toAddressCount = 1,
            ),
        )
    }
}
