package se.apothictech.eutherping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.apothictech.eutherping.secure.SecureRepository

class TransportTest {
    @Test
    fun `secure transport never looks like sms`() {
        assertEquals("SECURE PING", Transport.SECURE.label)
        assertEquals("CELL // SMS", Transport.SMS.label)
    }

    @Test
    fun `secure wire capsules route to vessels`() {
        assertTrue(SecureRepository.isSecureBody("EP2I:invite"))
        assertTrue(SecureRepository.isSecureBody("EP2A:accept"))
        assertTrue(SecureRepository.isSecureBody("EP1M:message"))
        assertFalse(SecureRepository.isSecureBody("ordinary carrier SMS"))
    }
}
