package se.apothictech.eutherping.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsParticipantSelectionTest {
    @Test
    fun `canonical thread recipients take precedence over raw MMS addresses`() {
        assertEquals(
            listOf("sender"),
            chooseMmsParticipants(
                canonicalParticipants = listOf("sender"),
            ) { listOf("sender", "own-number") },
        )
    }

    @Test
    fun `incoming one to one MMS omits its sole to address when own number is unavailable`() {
        assertTrue(
            shouldOmitSoleIncomingMmsRecipient(
                incoming = true,
                ownNumbersKnown = false,
                toAddressCount = 1,
            ),
        )
    }

    @Test
    fun `incoming group MMS keeps multiple to addresses for fallback resolution`() {
        assertFalse(
            shouldOmitSoleIncomingMmsRecipient(
                incoming = true,
                ownNumbersKnown = false,
                toAddressCount = 2,
            ),
        )
    }

    @Test
    fun `known own number uses number matching instead of the sole recipient fallback`() {
        assertFalse(
            shouldOmitSoleIncomingMmsRecipient(
                incoming = true,
                ownNumbersKnown = true,
                toAddressCount = 1,
            ),
        )
    }
}
