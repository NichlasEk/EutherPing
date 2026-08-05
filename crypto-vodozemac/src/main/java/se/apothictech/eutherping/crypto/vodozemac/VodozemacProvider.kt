// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.vodozemac

import android.util.Base64
import se.apothictech.eutherping.crypto.ProtocolAddress
import se.apothictech.eutherping.crypto.ProtocolCiphertext
import se.apothictech.eutherping.crypto.ProtocolCiphertextKind
import se.apothictech.eutherping.crypto.ProtocolPreKeyPublication
import se.apothictech.eutherping.crypto.ProtocolStateRepository
import se.apothictech.eutherping.crypto.ProtocolStateTransaction
import se.apothictech.eutherping.crypto.SecureProtocolDescriptor
import se.apothictech.eutherping.crypto.SecureProtocolEngine
import se.apothictech.eutherping.crypto.SecureProtocolProvider

/**
 * Non-shipping Vodozemac provider checkpoint. Native state is opaque to Kotlin
 * and every state transition is committed through [ProtocolStateRepository].
 */
class VodozemacProvider : SecureProtocolProvider {
    override val descriptor = SecureProtocolDescriptor(
        providerId = PROVIDER_ID,
        implementationVersion = VODOZEMAC_VERSION,
        wireFamily = "EP3-VODO-PROBE",
        license = "Apache-2.0",
        forwardSecrecy = true,
        postCompromiseSecurity = true,
        postQuantumHandshake = false,
        productionReady = false,
    )

    override fun createEngine(
        localAddress: ProtocolAddress,
        stateRepository: ProtocolStateRepository,
    ): SecureProtocolEngine = VodozemacEngine(descriptor, localAddress, stateRepository)

    companion object {
        const val PROVIDER_ID = "vodozemac"
        const val VODOZEMAC_VERSION = "0.10.0"
    }
}

private class VodozemacEngine(
    override val descriptor: SecureProtocolDescriptor,
    private val localAddress: ProtocolAddress,
    private val repository: ProtocolStateRepository,
) : SecureProtocolEngine {
    override fun createPreKeyPublication(): ProtocolPreKeyPublication = transition { state ->
        val result = NativeFields.decode(
            VodozemacNative.createPreKeyPublication(state.ensureAccount()),
            expectedCount = 2,
        )
        state.write(ACCOUNT_KEY, result[0])
        ProtocolPreKeyPublication(descriptor.providerId, result[1])
    }

    override fun establishOutboundSession(
        remoteAddress: ProtocolAddress,
        publication: ProtocolPreKeyPublication,
    ) = transition { state ->
        require(publication.providerId == descriptor.providerId) { "Provider mismatch" }
        val sessionKey = sessionKey(remoteAddress)
        check(state.read(sessionKey) == null) {
            "A session already exists for ${remoteAddress.stableId}; verified reset required"
        }
        state.pinIdentity(remoteAddress, NativeFrames.publicationSigningKey(publication.payload))
        val result = NativeFields.decode(
            VodozemacNative.establishOutbound(state.ensureAccount(), publication.payload),
            expectedCount = 1,
        )
        state.write(sessionKey, result[0])
    }

    override fun encrypt(
        remoteAddress: ProtocolAddress,
        plaintext: ByteArray,
    ): ProtocolCiphertext = transition { state ->
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Plaintext exceeds provider limit" }
        val sessionKey = sessionKey(remoteAddress)
        val session = checkNotNull(state.read(sessionKey)) { "No session for ${remoteAddress.stableId}" }
        val result = NativeFields.decode(
            VodozemacNative.encrypt(state.ensureAccount(), session, plaintext),
            expectedCount = 2,
        )
        state.write(sessionKey, result[0])
        ProtocolCiphertext(
            providerId = descriptor.providerId,
            kind = NativeFrames.ciphertextKind(result[1]),
            payload = result[1],
        )
    }

    override fun decrypt(
        remoteAddress: ProtocolAddress,
        ciphertext: ProtocolCiphertext,
    ): ByteArray = transition { state ->
        require(ciphertext.providerId == descriptor.providerId) { "Provider mismatch" }
        require(ciphertext.payload.size <= MAX_CIPHERTEXT_BYTES) {
            "Ciphertext exceeds provider limit"
        }
        require(NativeFrames.ciphertextKind(ciphertext.payload) == ciphertext.kind) {
            "Ciphertext kind mismatch"
        }
        val signingKey = if (ciphertext.kind == ProtocolCiphertextKind.PRE_KEY) {
            NativeFrames.preKeySigningKey(ciphertext.payload).also {
                state.pinIdentity(remoteAddress, it)
            }
        } else {
            byteArrayOf()
        }

        val sessionKey = sessionKey(remoteAddress)
        val result = NativeFields.decode(
            VodozemacNative.decrypt(
                state.ensureAccount(),
                state.read(sessionKey) ?: byteArrayOf(),
                ciphertext.payload,
            ),
            expectedCount = 4,
        )
        state.write(ACCOUNT_KEY, result[0])
        state.write(sessionKey, result[1])
        require(result[3].contentEquals(signingKey)) { "Native identity result mismatch" }
        result[2]
    }

    private fun <T> transition(block: (ProtocolStateTransaction) -> T): T =
        repository.transaction("vodozemac:${localAddress.stableId}", block)

    private fun sessionKey(remoteAddress: ProtocolAddress): String =
        "session/${remoteAddress.storageId()}"

    private fun ProtocolStateTransaction.ensureAccount(): ByteArray =
        read(ACCOUNT_KEY) ?: VodozemacNative.createAccount().also { write(ACCOUNT_KEY, it) }

    private fun ProtocolStateTransaction.pinIdentity(
        remoteAddress: ProtocolAddress,
        signingKey: ByteArray,
    ) {
        val key = "identity/${remoteAddress.storageId()}"
        val existing = read(key)
        require(existing == null || existing.contentEquals(signingKey)) {
            "Remote identity changed for ${remoteAddress.stableId}"
        }
        if (existing == null) write(key, signingKey)
    }

    private fun ProtocolAddress.storageId(): String = Base64.encodeToString(
        stableId.encodeToByteArray(),
        Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE,
    )

    companion object {
        private const val ACCOUNT_KEY = "account"
        private const val MAX_PLAINTEXT_BYTES = 64 * 1024
        private const val MAX_CIPHERTEXT_BYTES = 128 * 1024
    }
}
