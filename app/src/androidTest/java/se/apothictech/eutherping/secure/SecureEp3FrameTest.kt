package se.apothictech.eutherping.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.apothictech.eutherping.crypto.ProtocolCiphertext
import se.apothictech.eutherping.crypto.ProtocolCiphertextKind

@RunWith(AndroidJUnit4::class)
class SecureEp3FrameTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearProtocolState() {
        clearState()
    }

    @After
    fun cleanUpProtocolState() {
        clearState()
    }

    @Test
    fun attachmentFrameRoundTripsAsIndependentEp3Family() {
        val id = "00000000-0000-4000-8000-000000000001"
        val payload = ByteArray(1_024) { index -> (index and 0xff).toByte() }
        val ciphertext = ProtocolCiphertext(
            providerId = SecureRatchetRuntime.descriptor.providerId,
            kind = ProtocolCiphertextKind.SESSION,
            payload = payload,
        )

        val wire = SecureRepository.encodeRatchetFrame(
            SecureRepository.RATCHET_ATTACHMENT_PREFIX,
            id,
            ciphertext,
        )
        val decoded = SecureRepository.decodeRatchetFrame(
            wire,
            SecureRepository.RATCHET_ATTACHMENT_PREFIX,
            hasMessageId = true,
        )

        assertTrue(wire.startsWith("EP3F:"))
        assertEquals(id, decoded.messageId)
        assertEquals(ProtocolCiphertextKind.SESSION, decoded.ciphertext.kind)
        assertArrayEquals(payload, decoded.ciphertext.payload)
        assertTrue(
            runCatching {
                SecureRepository.decodeRatchetFrame(
                    wire,
                    SecureRepository.RATCHET_MESSAGE_PREFIX,
                    hasMessageId = true,
                )
            }.isFailure,
        )
    }

    @Test
    fun attachmentFrameRejectsMalformedIdAndTruncation() {
        val ciphertext = ProtocolCiphertext(
            providerId = SecureRatchetRuntime.descriptor.providerId,
            kind = ProtocolCiphertextKind.SESSION,
            payload = byteArrayOf(1, 2, 3),
        )
        assertTrue(
            runCatching {
                SecureRepository.encodeRatchetFrame(
                    SecureRepository.RATCHET_ATTACHMENT_PREFIX,
                    "x".repeat(36),
                    ciphertext,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                SecureRepository.decodeRatchetFrame(
                    "EP3F:AQ",
                    SecureRepository.RATCHET_ATTACHMENT_PREFIX,
                    hasMessageId = true,
                )
            }.isFailure,
        )
    }

    @Test
    fun fullPairingRatchetsAndRecoversAttachmentManifest() {
        val suffix = System.nanoTime().toString().takeLast(7)
        val phoneA = "+46701$suffix"
        val phoneB = "+46702$suffix"
        val invitation = SecureRepository.createInvitation(context, phoneB).getOrThrow()
        assertEquals(
            SecureFrameAcceptance.ACCEPTED,
            SecureRepository.acceptIncomingControl(context, phoneA, invitation),
        )
        val acceptance = SecureRepository.acceptInvitation(context, phoneA).getOrThrow()
        assertEquals(
            SecureFrameAcceptance.ACCEPTED,
            SecureRepository.acceptIncomingControl(context, phoneB, acceptance),
        )
        SecureRepository.markVerified(context, phoneA)
        SecureRepository.markVerified(context, phoneB)

        val id = UUID.randomUUID().toString()
        val descriptor = SecureAttachmentDescriptor(
            id = id,
            name = "ep3-photo.jpg",
            mimeType = "image/jpeg",
            plaintextSize = 4_096,
            plaintextSha256 = "1".repeat(64),
            ciphertextSize = 4_112,
            ciphertextSha256 = "2".repeat(64),
            contentKey = ByteArray(32) { index -> (index + 1).toByte() },
            nonce = ByteArray(12) { index -> (index + 33).toByte() },
            downloadUrl = null,
            transportToken = "A".repeat(32),
            bluetoothAvailable = true,
            bluetoothName = "EP3 test phone",
            incoming = false,
        )

        val wire = SecureRepository.encryptAttachmentOffer(context, phoneB, descriptor).getOrThrow()
        assertTrue(wire.startsWith(SecureRepository.RATCHET_ATTACHMENT_PREFIX))
        assertEquals(
            SecureFrameAcceptance.ACCEPTED,
            SecureRepository.acceptIncomingAuthenticatedFrame(context, phoneA, wire),
        )
        val received = SecureRepository.decodeAttachmentOffer(
            context,
            phoneA,
            wire,
            incoming = true,
        ).getOrThrow()

        assertEquals(id, received.id)
        assertEquals("ep3-photo.jpg", received.name)
        assertEquals("image/jpeg", received.mimeType)
        assertEquals(4_096, received.plaintextSize)
        assertArrayEquals(descriptor.contentKey, received.contentKey)
        assertArrayEquals(descriptor.nonce, received.nonce)
        assertTrue(received.incoming)
    }

    private fun clearState() {
        SecureRatchetRuntime.deleteAllStateForVerifiedReset(context)
        SecureReplayRepository.clear(context)
        context.getSharedPreferences("eutherping_secure_peers", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("eutherping_secure_vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
