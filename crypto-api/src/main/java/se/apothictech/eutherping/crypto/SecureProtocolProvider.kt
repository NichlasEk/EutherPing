package se.apothictech.eutherping.crypto

/**
 * Provider-neutral boundary for a stateful, reviewed Secure Vessels protocol.
 *
 * Implementations own protocol primitives and serialization. EutherPing owns
 * the transport envelope, identity UX, and an atomic encrypted implementation
 * of [ProtocolStateRepository].
 */
interface SecureProtocolProvider {
    val descriptor: SecureProtocolDescriptor

    fun createEngine(
        localAddress: ProtocolAddress,
        stateRepository: ProtocolStateRepository,
    ): SecureProtocolEngine
}

data class SecureProtocolDescriptor(
    val providerId: String,
    val implementationVersion: String,
    val wireFamily: String,
    val license: String,
    val forwardSecrecy: Boolean,
    val postCompromiseSecurity: Boolean,
    val postQuantumHandshake: Boolean,
    val productionReady: Boolean,
)

data class ProtocolAddress(
    val name: String,
    val deviceId: Int = 1,
) {
    init {
        require(name.isNotBlank()) { "Protocol address must not be blank" }
        require(deviceId in 1..127) { "Device ID must be between 1 and 127" }
    }

    val stableId: String get() = "$name.$deviceId"
}

enum class ProtocolCiphertextKind {
    PRE_KEY,
    SESSION,
}

data class ProtocolCiphertext(
    val providerId: String,
    val kind: ProtocolCiphertextKind,
    val payload: ByteArray,
)

data class ProtocolPreKeyPublication(
    val providerId: String,
    val payload: ByteArray,
)

interface SecureProtocolEngine {
    val descriptor: SecureProtocolDescriptor

    fun createPreKeyPublication(): ProtocolPreKeyPublication

    fun establishOutboundSession(
        remoteAddress: ProtocolAddress,
        publication: ProtocolPreKeyPublication,
    )

    fun encrypt(remoteAddress: ProtocolAddress, plaintext: ByteArray): ProtocolCiphertext

    fun decrypt(remoteAddress: ProtocolAddress, ciphertext: ProtocolCiphertext): ByteArray
}

/**
 * Runs one copy-on-write transaction for every protocol transition. A durable
 * implementation must commit all changed records atomically before returning.
 */
interface ProtocolStateRepository {
    fun <T> transaction(lockId: String, block: (ProtocolStateTransaction) -> T): T
}

interface ProtocolStateTransaction {
    fun read(key: String): ByteArray?

    fun write(key: String, value: ByteArray)

    fun delete(key: String)

    fun keys(prefix: String): Set<String>
}
