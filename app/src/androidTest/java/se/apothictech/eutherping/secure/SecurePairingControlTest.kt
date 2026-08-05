package se.apothictech.eutherping.secure

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurePairingControlTest {
    @Test
    fun signedEp3ControlIsBoundedFreshAndAdmittedOnlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString().take(8)
        val outbound = "+46710$suffix"
        val inbound = "+46711$suffix"
        SecureReplayRepository.clear(context)
        try {
            val wire = SecureRepository.createInvitation(context, outbound).getOrThrow()
            assertTrue(wire.startsWith(SecureRepository.INVITE_PREFIX))
            assertTrue("EP3 pairing control must remain bounded", wire.length <= 773)
            assertEquals(
                SecureFrameAcceptance.ACCEPTED,
                SecureRepository.acceptIncomingControl(context, inbound, wire),
            )
            assertEquals(SecurePeerState.INVITE_RECEIVED, SecureRepository.peer(context, inbound).state)
            assertEquals(
                SecureFrameAcceptance.DUPLICATE,
                SecureRepository.acceptIncomingControl(context, inbound, wire),
            )
        } finally {
            SecureReplayRepository.clear(context)
        }
    }

    @Test
    fun signedControlRejectsTamperingAndStaleness() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString().take(8)
        val wire = SecureRepository.createInvitation(context, "+46712$suffix").getOrThrow()
        val tamperIndex = wire.length - 10
        val tampered = wire.replaceRange(
            tamperIndex,
            tamperIndex + 1,
            if (wire[tamperIndex] == 'A') "B" else "A",
        )
        SecureReplayRepository.clear(context)
        try {
            assertEquals(
                SecureFrameAcceptance.INVALID,
                SecureRepository.acceptIncomingControl(context, "+46713$suffix", tampered),
            )
            assertEquals(
                SecureFrameAcceptance.STALE,
                SecureRepository.acceptIncomingControl(
                    context,
                    "+46713$suffix",
                    wire,
                    now = System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000,
                ),
            )
        } finally {
            SecureReplayRepository.clear(context)
        }
    }

    @Test
    fun malformedOrOversizedControlIsRejectedBeforePeerState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString().take(8)
        val address = "+46716$suffix"
        assertEquals(
            SecureFrameAcceptance.INVALID,
            SecureRepository.acceptIncomingControl(
                context,
                address,
                SecureRepository.INVITE_PREFIX + "A".repeat(302),
            ),
        )
        assertEquals(
            SecureFrameAcceptance.INVALID,
            SecureRepository.acceptIncomingControl(
                context,
                address,
                SecureRepository.INVITE_PREFIX + "AA AA",
            ),
        )
        assertEquals(SecurePeerState.NONE, SecureRepository.peer(context, address).state)
    }

    @Test
    fun legacyCompactV2ControlRemainsReadable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString().take(8)
        val signedV4 = SecureRepository.createInvitation(context, "+46714$suffix").getOrThrow()
        val rawV4 = Base64.decode(
            signedV4.removePrefix(SecureRepository.INVITE_PREFIX),
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING,
        )
        val parser = ByteBuffer.wrap(rawV4).order(ByteOrder.BIG_ENDIAN)
        assertEquals(4, parser.get().toInt())
        parser.position(1 + 8 + 16)
        parser.int
        val encryptionSize = parser.short.toInt() and 0xffff
        parser.position(parser.position() + encryptionSize)
        parser.int
        val signingSize = parser.short.toInt() and 0xffff
        parser.position(parser.position() + signingSize)
        val legacyKeySectionEnd = parser.position()
        val publicationSize = parser.short.toInt() and 0xffff
        parser.position(parser.position() + publicationSize)
        val unsignedSize = parser.position()
        val signatureSize = parser.short.toInt() and 0xffff
        assertEquals(rawV4.size, unsignedSize + 2 + signatureSize)
        val keySection = rawV4.copyOfRange(1 + 8 + 16, legacyKeySectionEnd)
        val rawV2 = byteArrayOf(2) + keySection
        val legacyV2 = "EP2I:" + Base64.encodeToString(
            rawV2,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING,
        )

        assertEquals(
            SecureFrameAcceptance.ACCEPTED,
            SecureRepository.acceptIncomingControl(context, "+46715$suffix", legacyV2),
        )
        assertEquals(SecurePeerState.INVITE_RECEIVED, SecureRepository.peer(context, "+46715$suffix").state)
        assertEquals(
            SecureFrameAcceptance.DUPLICATE,
            SecureRepository.acceptIncomingControl(context, "+46715$suffix", legacyV2),
        )
    }
}
