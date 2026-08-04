package se.apothictech.eutherping.secure

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureIdentityTransitionTest {
    private val oldEncryption = byteArrayOf(1)
    private val oldSigning = byteArrayOf(2)
    private val newEncryption = byteArrayOf(3)
    private val newSigning = byteArrayOf(4)

    @Test
    fun verifiedIdentityIsPreservedAndReplacementIsQuarantined() {
        val verified = peer(SecurePeerState.VERIFIED, oldEncryption, oldSigning, "old")

        val resolved = SecureRepository.resolveIncomingIdentity(
            verified,
            newEncryption,
            newSigning,
            "new",
            isAcceptance = false,
        )

        assertEquals(SecurePeerState.IDENTITY_CHANGE_PENDING, resolved.state)
        assertArrayEquals(oldEncryption, resolved.encryptionPublicKey)
        assertArrayEquals(oldSigning, resolved.signingPublicKey)
        assertEquals("old", resolved.fingerprint)
        assertArrayEquals(newEncryption, resolved.pendingEncryptionPublicKey)
        assertArrayEquals(newSigning, resolved.pendingSigningPublicKey)
        assertEquals("new", resolved.pendingFingerprint)
        assertEquals(true, resolved.pendingRequiresAcceptance)
    }

    @Test
    fun sameVerifiedIdentityIsIdempotent() {
        val verified = peer(SecurePeerState.VERIFIED, oldEncryption, oldSigning, "old")

        val resolved = SecureRepository.resolveIncomingIdentity(
            verified,
            oldEncryption,
            oldSigning,
            "old",
            isAcceptance = false,
        )

        assertEquals(verified, resolved)
    }

    @Test
    fun furtherReplacementCannotOverwritePendingIdentity() {
        val pending = peer(SecurePeerState.IDENTITY_CHANGE_PENDING, oldEncryption, oldSigning, "old").copy(
            pendingEncryptionPublicKey = newEncryption,
            pendingSigningPublicKey = newSigning,
            pendingFingerprint = "new",
            pendingRequiresAcceptance = true,
        )

        val resolved = SecureRepository.resolveIncomingIdentity(
            pending,
            byteArrayOf(8),
            byteArrayOf(9),
            "third",
            isAcceptance = true,
        )

        assertEquals(pending, resolved)
    }

    @Test
    fun firstAcceptanceCreatesUnverifiedIdentity() {
        val resolved = SecureRepository.resolveIncomingIdentity(
            peer(SecurePeerState.INVITE_SENT),
            newEncryption,
            newSigning,
            "new",
            isAcceptance = true,
        )

        assertEquals(SecurePeerState.ACTIVE_UNVERIFIED, resolved.state)
        assertEquals("new", resolved.fingerprint)
    }

    private fun peer(
        state: SecurePeerState,
        encryption: ByteArray? = null,
        signing: ByteArray? = null,
        fingerprint: String? = null,
    ) = SecurePeer("+46700000000", encryption, signing, fingerprint, state)
}
