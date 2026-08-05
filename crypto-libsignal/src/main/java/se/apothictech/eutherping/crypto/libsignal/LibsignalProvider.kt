// SPDX-License-Identifier: AGPL-3.0-only

package se.apothictech.eutherping.crypto.libsignal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
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
 * Non-UI libsignal dependency spike. The provider is deliberately not wired to
 * EutherPing's message flow until its store and probe framing receive review.
 */
class LibsignalProvider : SecureProtocolProvider {
    override val descriptor = SecureProtocolDescriptor(
        providerId = PROVIDER_ID,
        implementationVersion = LIBSIGNAL_VERSION,
        wireFamily = "EP3-LS-PROBE",
        license = "AGPL-3.0-only",
        forwardSecrecy = true,
        postCompromiseSecurity = true,
        postQuantumHandshake = true,
        productionReady = false,
    )

    override fun createEngine(
        localAddress: ProtocolAddress,
        stateRepository: ProtocolStateRepository,
    ): SecureProtocolEngine = LibsignalEngine(descriptor, localAddress, stateRepository)

    companion object {
        const val PROVIDER_ID = "libsignal"
        const val LIBSIGNAL_VERSION = "0.99.4"
    }
}

private class LibsignalEngine(
    override val descriptor: SecureProtocolDescriptor,
    private val localAddress: ProtocolAddress,
    private val repository: ProtocolStateRepository,
) : SecureProtocolEngine {
    override fun createPreKeyPublication(): ProtocolPreKeyPublication = transition {
        val store = PersistedSignalProtocolStore(it)
        store.ensureAccount()
        val preKey = store.ensurePreKey(PRE_KEY_ID)
        val signedPreKey = store.ensureSignedPreKey(SIGNED_PRE_KEY_ID)
        val kyberPreKey = store.ensureKyberPreKey(KYBER_PRE_KEY_ID)
        ProtocolPreKeyPublication(
            providerId = descriptor.providerId,
            payload = ProbePreKeyCodec.encode(
                registrationId = store.readRegistrationId,
                deviceId = localAddress.deviceId,
                preKeyId = PRE_KEY_ID,
                preKey = preKey.keyPair.publicKey,
                signedPreKeyId = SIGNED_PRE_KEY_ID,
                signedPreKey = signedPreKey.keyPair.publicKey,
                signedPreKeySignature = signedPreKey.signature,
                identityKey = store.localIdentityKeyPair.publicKey,
                kyberPreKeyId = KYBER_PRE_KEY_ID,
                kyberPreKey = kyberPreKey.keyPair.publicKey,
                kyberPreKeySignature = kyberPreKey.signature,
            ),
        )
    }

    override fun establishOutboundSession(
        remoteAddress: ProtocolAddress,
        publication: ProtocolPreKeyPublication,
    ) = transition { transaction ->
        require(publication.providerId == descriptor.providerId) { "Provider mismatch" }
        val store = PersistedSignalProtocolStore(transaction).apply { ensureAccount() }
        SessionBuilder(store, remoteAddress.toSignal(), localAddress.toSignal())
            .process(ProbePreKeyCodec.decode(publication.payload))
    }

    override fun encrypt(remoteAddress: ProtocolAddress, plaintext: ByteArray): ProtocolCiphertext =
        transition { transaction ->
            val store = PersistedSignalProtocolStore(transaction).apply { ensureAccount() }
            val ciphertext = SessionCipher(store, localAddress.toSignal(), remoteAddress.toSignal())
                .encrypt(plaintext)
            ProtocolCiphertext(
                providerId = descriptor.providerId,
                kind = when (ciphertext.type) {
                    CiphertextMessage.PREKEY_TYPE -> ProtocolCiphertextKind.PRE_KEY
                    CiphertextMessage.WHISPER_TYPE -> ProtocolCiphertextKind.SESSION
                    else -> error("Unsupported libsignal ciphertext type ${ciphertext.type}")
                },
                payload = ciphertext.serialize(),
            )
        }

    override fun decrypt(
        remoteAddress: ProtocolAddress,
        ciphertext: ProtocolCiphertext,
    ): ByteArray = transition { transaction ->
        require(ciphertext.providerId == descriptor.providerId) { "Provider mismatch" }
        val store = PersistedSignalProtocolStore(transaction).apply { ensureAccount() }
        val cipher = SessionCipher(store, localAddress.toSignal(), remoteAddress.toSignal())
        when (ciphertext.kind) {
            ProtocolCiphertextKind.PRE_KEY -> cipher.decrypt(PreKeySignalMessage(ciphertext.payload))
            ProtocolCiphertextKind.SESSION -> cipher.decrypt(SignalMessage(ciphertext.payload))
        }
    }

    private fun <T> transition(block: (ProtocolStateTransaction) -> T): T =
        repository.transaction("libsignal:${localAddress.stableId}", block)

    companion object {
        private const val PRE_KEY_ID = 1
        private const val SIGNED_PRE_KEY_ID = 1
        private const val KYBER_PRE_KEY_ID = 1
    }
}

private class PersistedSignalProtocolStore(
    private val state: ProtocolStateTransaction,
) : SignalProtocolStore {
    val localIdentityKeyPair: IdentityKeyPair
        get() = IdentityKeyPair(requireNotNull(state.read(LOCAL_IDENTITY)))

    val readRegistrationId: Int
        get() = DataInputStream(ByteArrayInputStream(requireNotNull(state.read(REGISTRATION_ID)))).readInt()

    fun ensureAccount() {
        if (state.read(LOCAL_IDENTITY) != null) return
        state.write(LOCAL_IDENTITY, IdentityKeyPair.generate().serialize())
        state.write(
            REGISTRATION_ID,
            ByteArrayOutputStream().also { output ->
                DataOutputStream(output).use { it.writeInt(KeyHelper.generateRegistrationId(false)) }
            }.toByteArray(),
        )
    }

    fun ensurePreKey(id: Int): PreKeyRecord = runCatching { loadPreKey(id) }.getOrElse {
        PreKeyRecord(id, ECKeyPair.generate()).also { storePreKey(id, it) }
    }

    fun ensureSignedPreKey(id: Int): SignedPreKeyRecord = runCatching { loadSignedPreKey(id) }.getOrElse {
        val keyPair = ECKeyPair.generate()
        SignedPreKeyRecord(
            id,
            System.currentTimeMillis(),
            keyPair,
            localIdentityKeyPair.privateKey.calculateSignature(keyPair.publicKey.serialize()),
        ).also { storeSignedPreKey(id, it) }
    }

    fun ensureKyberPreKey(id: Int): KyberPreKeyRecord = runCatching { loadKyberPreKey(id) }.getOrElse {
        val keyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        KyberPreKeyRecord(
            id,
            System.currentTimeMillis(),
            keyPair,
            localIdentityKeyPair.privateKey.calculateSignature(keyPair.publicKey.serialize()),
        ).also { storeKyberPreKey(id, it) }
    }

    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentityKeyPair

    override fun getLocalRegistrationId(): Int = readRegistrationId

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        val key = remoteIdentityKey(address)
        val existing = state.read(key)?.let(::IdentityKey)
        state.write(key, identityKey.serialize())
        return if (existing == null || existing == identityKey) {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        } else {
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean = getIdentity(address)?.let(identityKey::equals) ?: true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        state.read(remoteIdentityKey(address))?.let(::IdentityKey)

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        state.read("prekey/$preKeyId")?.let(::PreKeyRecord)
            ?: throw InvalidKeyIdException("No prekey $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) =
        state.write("prekey/$preKeyId", record.serialize())

    override fun containsPreKey(preKeyId: Int): Boolean = state.read("prekey/$preKeyId") != null

    override fun removePreKey(preKeyId: Int) = state.delete("prekey/$preKeyId")

    override fun loadSession(address: SignalProtocolAddress): SessionRecord =
        state.read(sessionKey(address))?.let(::SessionRecord) ?: SessionRecord()

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.map { address ->
            state.read(sessionKey(address))?.let(::SessionRecord)
                ?: throw NoSessionException(address, "No session for $address")
        }

    override fun getSubDeviceSessions(name: String): List<Int> {
        val prefix = "session/${safe(name)}/"
        return state.keys(prefix).mapNotNull { it.removePrefix(prefix).toIntOrNull() }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        state.write(sessionKey(address), record.serialize())

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        state.read(sessionKey(address)) != null

    override fun deleteSession(address: SignalProtocolAddress) = state.delete(sessionKey(address))

    override fun deleteAllSessions(name: String) =
        state.keys("session/${safe(name)}/").forEach(state::delete)

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        state.read("signed/$signedPreKeyId")?.let(::SignedPreKeyRecord)
            ?: throw InvalidKeyIdException("No signed prekey $signedPreKeyId")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> =
        state.keys("signed/").mapNotNull { state.read(it)?.let(::SignedPreKeyRecord) }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) =
        state.write("signed/$signedPreKeyId", record.serialize())

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        state.read("signed/$signedPreKeyId") != null

    override fun removeSignedPreKey(signedPreKeyId: Int) = state.delete("signed/$signedPreKeyId")

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        state.read("kyber/$kyberPreKeyId")?.let(::KyberPreKeyRecord)
            ?: throw InvalidKeyIdException("No Kyber prekey $kyberPreKeyId")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> =
        state.keys("kyber/").mapNotNull { state.read(it)?.let(::KyberPreKeyRecord) }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        state.write("kyber/$kyberPreKeyId", record.serialize())

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean =
        state.read("kyber/$kyberPreKeyId") != null

    override fun markKyberPreKeyUsed(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ECPublicKey,
    ) {
        if (!containsKyberPreKey(kyberPreKeyId)) throw ReusedBaseKeyException("Kyber prekey reused")
        state.delete("kyber/$kyberPreKeyId")
    }

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord,
    ) = state.write(senderKey(sender, distributionId), record.serialize())

    override fun loadSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
    ): SenderKeyRecord? = state.read(senderKey(sender, distributionId))?.let(::SenderKeyRecord)

    private fun remoteIdentityKey(address: SignalProtocolAddress) =
        "identity/remote/${safe(address.name)}/${address.deviceId}"

    private fun sessionKey(address: SignalProtocolAddress) =
        "session/${safe(address.name)}/${address.deviceId}"

    private fun senderKey(address: SignalProtocolAddress, distributionId: UUID) =
        "sender/${safe(address.name)}/${address.deviceId}/$distributionId"

    companion object {
        private const val LOCAL_IDENTITY = "identity/local"
        private const val REGISTRATION_ID = "identity/registration"

        private fun safe(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}

private object ProbePreKeyCodec {
    private val MAGIC = byteArrayOf('L'.code.toByte(), 'S'.code.toByte(), 0, 1)
    private const val MAX_FIELD_BYTES = 4_096
    private const val MAX_TOTAL_BYTES = 8_192

    fun encode(
        registrationId: Int,
        deviceId: Int,
        preKeyId: Int,
        preKey: ECPublicKey,
        signedPreKeyId: Int,
        signedPreKey: ECPublicKey,
        signedPreKeySignature: ByteArray,
        identityKey: IdentityKey,
        kyberPreKeyId: Int,
        kyberPreKey: KEMPublicKey,
        kyberPreKeySignature: ByteArray,
    ): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(registrationId)
            data.writeInt(deviceId)
            data.writeInt(preKeyId)
            data.writeField(preKey.serialize())
            data.writeInt(signedPreKeyId)
            data.writeField(signedPreKey.serialize())
            data.writeField(signedPreKeySignature)
            data.writeField(identityKey.serialize())
            data.writeInt(kyberPreKeyId)
            data.writeField(kyberPreKey.serialize())
            data.writeField(kyberPreKeySignature)
        }
    }.toByteArray().also { require(it.size <= MAX_TOTAL_BYTES) { "Probe prekey payload is too large" } }

    fun decode(payload: ByteArray): PreKeyBundle {
        require(payload.size <= MAX_TOTAL_BYTES) { "Probe prekey payload is too large" }
        val input = DataInputStream(ByteArrayInputStream(payload))
        require(input.readExact(MAGIC.size).contentEquals(MAGIC)) { "Unknown probe prekey format" }
        val bundle = PreKeyBundle(
            registrationId = input.readInt(),
            deviceId = input.readInt(),
            preKeyId = input.readInt(),
            preKeyPublic = ECPublicKey(input.readField()),
            signedPreKeyId = input.readInt(),
            signedPreKeyPublic = ECPublicKey(input.readField()),
            signedPreKeySignature = input.readField(),
            identityKey = IdentityKey(input.readField()),
            kyberPreKeyId = input.readInt(),
            kyberPreKeyPublic = KEMPublicKey(input.readField()),
            kyberPreKeySignature = input.readField(),
        )
        require(input.available() == 0) { "Trailing probe prekey data" }
        return bundle
    }

    private fun DataOutputStream.writeField(value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_FIELD_BYTES) { "Invalid probe field size" }
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readField(): ByteArray {
        val size = readInt()
        require(size in 1..MAX_FIELD_BYTES) { "Invalid probe field size" }
        return readExact(size)
    }

    private fun DataInputStream.readExact(size: Int): ByteArray =
        ByteArray(size).also(::readFully)
}

private fun ProtocolAddress.toSignal() = SignalProtocolAddress(name, deviceId)
