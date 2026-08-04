package se.apothictech.eutherping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.apothictech.eutherping.secure.SecureRepository
import se.apothictech.eutherping.secure.SecureAttachmentRepository
import se.apothictech.eutherping.secure.SecureAttachmentDescriptor
import se.apothictech.eutherping.secure.SecureReplayRepository
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

    @Test
    fun `attachment labels expose only transports present in signed offer`() {
        assertEquals("DIRECT WIFI", descriptor(url = "http://192.168.1.2:39841/x", bluetooth = false).transportLabel)
        assertEquals("BLUETOOTH", descriptor(url = null, bluetooth = true).transportLabel)
        assertEquals(
            "DIRECT WIFI + BLUETOOTH",
            descriptor(url = "http://192.168.1.2:39841/x", bluetooth = true).transportLabel,
        )
    }

    @Test
    fun `only image attachments are eligible for inline vessel previews`() {
        assertTrue(SecureAttachmentRepository.isDisplayableImage("image/jpeg"))
        assertTrue(SecureAttachmentRepository.isDisplayableImage("IMAGE/PNG"))
        assertFalse(SecureAttachmentRepository.isDisplayableImage("application/pdf"))
        assertTrue(
            SecureAttachmentRepository.canPreviewInMemory(
                descriptor(url = null, bluetooth = true, mimeType = "image/png", plaintextSize = 1024),
            ),
        )
        assertFalse(
            SecureAttachmentRepository.canPreviewInMemory(
                descriptor(
                    url = null,
                    bluetooth = true,
                    mimeType = "image/png",
                    plaintextSize = 32L * 1024 * 1024 + 1,
                ),
            ),
        )
    }

    @Test
    fun `secure replay window accepts bounded clock skew only`() {
        val now = 1_800_000_000_000L
        assertTrue(SecureReplayRepository.isFresh(now - 30L * 24 * 60 * 60 * 1000, now))
        assertFalse(SecureReplayRepository.isFresh(now - 30L * 24 * 60 * 60 * 1000 - 1, now))
        assertTrue(SecureReplayRepository.isFresh(now + 24L * 60 * 60 * 1000, now))
        assertFalse(SecureReplayRepository.isFresh(now + 24L * 60 * 60 * 1000 + 1, now))
    }

    private fun descriptor(
        url: String?,
        bluetooth: Boolean,
        mimeType: String = "application/octet-stream",
        plaintextSize: Long = 1,
    ) = SecureAttachmentDescriptor(
        id = "00000000-0000-4000-8000-000000000000",
        name = "test.bin",
        mimeType = mimeType,
        plaintextSize = plaintextSize,
        plaintextSha256 = "0".repeat(64),
        ciphertextSize = 17,
        ciphertextSha256 = "1".repeat(64),
        contentKey = ByteArray(32),
        nonce = ByteArray(12),
        downloadUrl = url,
        transportToken = "a".repeat(32),
        bluetoothAvailable = bluetooth,
        bluetoothName = "Test phone",
        incoming = true,
    )
}
