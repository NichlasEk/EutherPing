package se.apothictech.eutherping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.apothictech.eutherping.secure.SecureRepository
import se.apothictech.eutherping.secure.SecureAttachmentRepository
import java.net.SocketException

class TransportTest {
    @Test
    fun `secure transport never looks like sms`() {
        assertEquals("SECURE PING", Transport.SECURE.label)
        assertEquals("CELL // SMS + MMS", Transport.SMS.label)
    }

    @Test
    fun `secure wire capsules route to vessels`() {
        assertTrue(SecureRepository.isSecureBody("EP2I:invite"))
        assertTrue(SecureRepository.isSecureBody("EP2A:accept"))
        assertTrue(SecureRepository.isSecureBody("EP1M:message"))
        assertTrue(SecureRepository.isSecureBody("EP1F:attachment"))
        assertFalse(SecureRepository.isSecureBody("ordinary carrier SMS"))
    }

    @Test
    fun `graphene network denial becomes an actionable attachment error`() {
        val explained = SecureAttachmentRepository.explainAttachmentFailure(
            SocketException("socket failed: EPERM (Operation not permitted)"),
        )
        assertTrue(explained.message.orEmpty().contains("enable Network"))
        assertTrue(explained.message.orEmpty().contains("same Wi-Fi"))
    }
}
