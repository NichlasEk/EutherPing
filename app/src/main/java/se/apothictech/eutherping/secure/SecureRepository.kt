package se.apothictech.eutherping.secure

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.KeyStatusType
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.shaded.protobuf.ByteString
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

enum class SecurePeerState {
    NONE,
    INVITE_SENT,
    INVITE_RECEIVED,
    ACTIVE_UNVERIFIED,
    VERIFIED,
    IDENTITY_CHANGE_PENDING,
}

data class SecurePeer(
    val address: String,
    val encryptionPublicKey: ByteArray?,
    val signingPublicKey: ByteArray?,
    val fingerprint: String?,
    val state: SecurePeerState,
    val pendingEncryptionPublicKey: ByteArray? = null,
    val pendingSigningPublicKey: ByteArray? = null,
    val pendingFingerprint: String? = null,
    val pendingRequiresAcceptance: Boolean = false,
) {
    val hasKeys: Boolean
        get() = encryptionPublicKey != null &&
            signingPublicKey != null

    val canEncrypt: Boolean
        get() = hasKeys && state == SecurePeerState.VERIFIED
}

data class SecureDecodedMessage(
    val text: String,
    val isSecure: Boolean,
    val verified: Boolean,
    val attachment: SecureAttachmentDescriptor? = null,
)

data class SecureAttachmentDescriptor(
    val id: String,
    val name: String,
    val mimeType: String,
    val plaintextSize: Long,
    val plaintextSha256: String,
    val ciphertextSize: Long,
    val ciphertextSha256: String,
    val contentKey: ByteArray,
    val nonce: ByteArray,
    val downloadUrl: String?,
    val transportToken: String,
    val bluetoothAvailable: Boolean,
    val bluetoothName: String?,
    val incoming: Boolean,
) {
    val transportLabel: String
        get() = when {
            downloadUrl != null && bluetoothAvailable -> "DIRECT WIFI + BLUETOOTH"
            bluetoothAvailable -> "BLUETOOTH"
            else -> "DIRECT WIFI"
        }
}

object SecureRepository {
    const val INVITE_PREFIX = "EP2I:"
    const val ACCEPT_PREFIX = "EP2A:"
    const val MESSAGE_PREFIX = "EP1M:"
    const val ATTACHMENT_PREFIX = "EP1F:"

    private const val LEGACY_INVITE_PREFIX = "EP1I:"
    private const val LEGACY_ACCEPT_PREFIX = "EP1A:"
    private const val HPKE_PUBLIC_TYPE_URL = "type.googleapis.com/google.crypto.tink.HpkePublicKey"
    private const val ED25519_PUBLIC_TYPE_URL = "type.googleapis.com/google.crypto.tink.Ed25519PublicKey"

    val secureBodyPrefixes = listOf(
        INVITE_PREFIX,
        ACCEPT_PREFIX,
        LEGACY_INVITE_PREFIX,
        LEGACY_ACCEPT_PREFIX,
        MESSAGE_PREFIX,
        ATTACHMENT_PREFIX,
    )

    fun isSecureBody(body: String): Boolean = secureBodyPrefixes.any(body::startsWith)

    private const val KEYSET_PREFS = "eutherping_secure_keysets"
    private const val PEER_PREFS = "eutherping_secure_peers"
    private const val VAULT_PREFS = "eutherping_secure_vault"
    private const val HPKE_KEYSET = "hpke_private_v1"
    private const val SIGNING_KEYSET = "signing_private_v1"
    private const val VAULT_KEYSET = "vault_aead_v1"
    private const val MASTER_KEY_URI = "android-keystore://eutherping_secure_master_v1"
    private const val CONTEXT_LABEL = "EutherPing Secure Beta v1|"
    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
    private const val CONTROL_ID_BYTES = 16
    private const val MAX_COMPACT_CONTROL_BASE64_CHARS = 301
    private val COMPACT_CONTROL_BASE64 = Regex("[A-Za-z0-9_-]+")

    @Volatile
    private var cryptoRegistered = false

    fun ensureIdentity(context: Context): Result<String> = runCatching {
        identity(context).fingerprint
    }

    fun localFingerprint(context: Context): String = identity(context).fingerprint

    fun signAttachmentRequest(context: Context, id: String, token: String): String {
        val canonical = "GET|$id|$token".toByteArray(UTF_8)
        return encode(signingKeyset(context).getPrimitive(PublicKeySign::class.java).sign(canonical))
    }

    fun verifyAttachmentRequest(
        signingPublicKey: ByteArray,
        id: String,
        token: String,
        encodedSignature: String,
    ): Boolean = runCatching {
        registerCrypto()
        val canonical = "GET|$id|$token".toByteArray(UTF_8)
        KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(signingPublicKey))
            .getPrimitive(PublicKeyVerify::class.java)
            .verify(decode(encodedSignature), canonical)
    }.isSuccess

    fun peer(context: Context, address: String): SecurePeer {
        val normalized = normalizeAddress(address)
        val preferences = context.getSharedPreferences(PEER_PREFS, Context.MODE_PRIVATE)
        val raw = preferences.getString(peerKey(normalized), null)
            ?: preferences.all.values.asSequence()
                .filterIsInstance<String>()
                .firstOrNull { candidate ->
                    runCatching {
                        addressesEquivalent(
                            context,
                            normalized,
                            JSONObject(candidate).optString("address"),
                        )
                    }.getOrDefault(false)
                }
            ?: return SecurePeer(normalized, null, null, null, SecurePeerState.NONE)
        return runCatching {
            val json = JSONObject(raw)
            SecurePeer(
                address = normalized,
                encryptionPublicKey = json.optString("e").takeIf(String::isNotBlank)?.let(::decode),
                signingPublicKey = json.optString("s").takeIf(String::isNotBlank)?.let(::decode),
                fingerprint = json.optString("f").takeIf(String::isNotBlank),
                state = SecurePeerState.valueOf(json.getString("state")),
                pendingEncryptionPublicKey = json.optString("pe").takeIf(String::isNotBlank)?.let(::decode),
                pendingSigningPublicKey = json.optString("ps").takeIf(String::isNotBlank)?.let(::decode),
                pendingFingerprint = json.optString("pf").takeIf(String::isNotBlank),
                pendingRequiresAcceptance = json.optBoolean("pa", false),
            )
        }.getOrElse { SecurePeer(normalized, null, null, null, SecurePeerState.NONE) }
    }

    fun createInvitation(context: Context, address: String): Result<String> = runCatching {
        val normalized = normalizeAddress(address)
        require(normalized.isNotBlank()) { "A destination number is required" }
        val current = peer(context, normalized)
        check(!current.hasKeys) { "A Secure identity already exists for this contact" }
        val wire = encodeBundle(context, INVITE_PREFIX, identity(context))
        savePeer(
            context,
            SecurePeer(normalized, null, null, null, SecurePeerState.INVITE_SENT),
        )
        wire
    }

    fun acceptInvitation(context: Context, address: String): Result<String> = runCatching {
        val current = peer(context, address)
        check(current.state == SecurePeerState.INVITE_RECEIVED) { "No pending Secure Ping invite" }
        check(current.encryptionPublicKey != null && current.signingPublicKey != null) {
            "The invitation has no usable keys"
        }
        savePeer(context, current.copy(state = SecurePeerState.ACTIVE_UNVERIFIED))
        encodeBundle(context, ACCEPT_PREFIX, identity(context))
    }

    fun markVerified(context: Context, address: String) {
        val current = peer(context, address)
        if (current.hasKeys && current.state == SecurePeerState.ACTIVE_UNVERIFIED) {
            savePeer(context, current.copy(state = SecurePeerState.VERIFIED))
        }
    }

    fun acceptIdentityChange(context: Context, address: String): Result<String?> = runCatching {
        val current = peer(context, address)
        check(current.state == SecurePeerState.IDENTITY_CHANGE_PENDING) {
            "No Secure identity change is waiting"
        }
        val encryption = checkNotNull(current.pendingEncryptionPublicKey)
        val signing = checkNotNull(current.pendingSigningPublicKey)
        val fingerprint = checkNotNull(current.pendingFingerprint)
        val response = if (current.pendingRequiresAcceptance) {
            encodeBundle(context, ACCEPT_PREFIX, identity(context))
        } else {
            null
        }
        savePeer(
            context,
            SecurePeer(
                address = current.address,
                encryptionPublicKey = encryption,
                signingPublicKey = signing,
                fingerprint = fingerprint,
                state = SecurePeerState.ACTIVE_UNVERIFIED,
            ),
        )
        response
    }

    fun rejectIdentityChange(context: Context, address: String) {
        val current = peer(context, address)
        if (current.state == SecurePeerState.IDENTITY_CHANGE_PENDING && current.hasKeys) {
            savePeer(
                context,
                current.copy(
                    state = SecurePeerState.VERIFIED,
                    pendingEncryptionPublicKey = null,
                    pendingSigningPublicKey = null,
                    pendingFingerprint = null,
                    pendingRequiresAcceptance = false,
                ),
            )
        }
    }

    fun acceptIncomingControl(
        context: Context,
        address: String,
        body: String,
        now: Long = System.currentTimeMillis(),
    ): SecureFrameAcceptance {
        val (prefix, isAcceptance) = when {
            body.startsWith(INVITE_PREFIX) -> INVITE_PREFIX to false
            body.startsWith(ACCEPT_PREFIX) -> ACCEPT_PREFIX to true
            body.startsWith(LEGACY_INVITE_PREFIX) -> LEGACY_INVITE_PREFIX to false
            body.startsWith(LEGACY_ACCEPT_PREFIX) -> LEGACY_ACCEPT_PREFIX to true
            else -> return SecureFrameAcceptance.NOT_AUTHENTICATED_FRAME
        }
        val bundle = runCatching { decodeBundle(body, prefix) }.getOrNull()
            ?: return SecureFrameAcceptance.INVALID
        val bodyHash = sha256(body.toByteArray(UTF_8)).toHex()
        val isFreshSignedControl = bundle.controlId != null && bundle.timestamp != null
        val acceptance = SecureReplayRepository.accept(
            context,
            AuthenticatedSecureFrame(
                kind = when {
                    !isFreshSignedControl -> "pairing-legacy"
                    isAcceptance -> "pairing-accept"
                    else -> "pairing-invite"
                },
                id = bundle.controlId ?: bodyHash,
                timestamp = bundle.timestamp ?: now,
                senderFingerprint = bundle.fingerprint,
                ciphertextSha256 = bodyHash,
            ),
            now,
        )
        if (acceptance != SecureFrameAcceptance.ACCEPTED) return acceptance
        val current = peer(context, address)
        val resolved = resolveIncomingIdentity(
            current = current,
            encryptionPublicKey = bundle.encryptionPublicKey,
            signingPublicKey = bundle.signingPublicKey,
            fingerprint = bundle.fingerprint,
            isAcceptance = isAcceptance,
        )
        savePeer(context, resolved)
        return SecureFrameAcceptance.ACCEPTED
    }

    internal fun resolveIncomingIdentity(
        current: SecurePeer,
        encryptionPublicKey: ByteArray,
        signingPublicKey: ByteArray,
        fingerprint: String,
        isAcceptance: Boolean,
    ): SecurePeer {
        if (current.fingerprint == fingerprint && current.hasKeys) {
            return current
        }
        if (current.state == SecurePeerState.IDENTITY_CHANGE_PENDING) {
            return current
        }
        if (current.state == SecurePeerState.VERIFIED && current.hasKeys) {
            return current.copy(
                state = SecurePeerState.IDENTITY_CHANGE_PENDING,
                pendingEncryptionPublicKey = encryptionPublicKey,
                pendingSigningPublicKey = signingPublicKey,
                pendingFingerprint = fingerprint,
                pendingRequiresAcceptance = !isAcceptance,
            )
        }
        if (current.hasKeys) {
            return current
        }
        return SecurePeer(
            address = current.address,
            encryptionPublicKey = encryptionPublicKey,
            signingPublicKey = signingPublicKey,
            fingerprint = fingerprint,
            state = if (isAcceptance) {
                SecurePeerState.ACTIVE_UNVERIFIED
            } else {
                SecurePeerState.INVITE_RECEIVED
            },
        )
    }

    fun encryptMessage(context: Context, address: String, plaintext: String): Result<String> = runCatching {
        val current = peer(context, address)
        check(current.canEncrypt) { "Secure Ping is not paired with this contact" }
        val recipientFingerprint = checkNotNull(current.fingerprint)
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val senderFingerprint = identity(context).fingerprint
        val payload = JSONObject()
            .put("v", 1)
            .put("id", messageId)
            .put("ts", timestamp)
            .put("from", senderFingerprint)
            .put("to", recipientFingerprint)
            .put("text", plaintext)
            .toString()
            .toByteArray(UTF_8)
        val signature = signingKeyset(context).getPrimitive(PublicKeySign::class.java).sign(payload)
        val signedPayload = JSONObject()
            .put("p", encode(payload))
            .put("s", encode(signature))
            .toString()
            .toByteArray(UTF_8)
        val peerPublic = KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(current.encryptionPublicKey))
        val ciphertext = peerPublic.getPrimitive(HybridEncrypt::class.java).encrypt(
            signedPayload,
            contextInfo(recipientFingerprint),
        )
        storeOutgoing(context, messageId, plaintext)
        MESSAGE_PREFIX + JSONObject()
            .put("v", 1)
            .put("id", messageId)
            .put("c", encode(ciphertext))
            .toString()
            .toByteArray(UTF_8)
            .let(::encode)
    }

    fun encryptAttachmentOffer(
        context: Context,
        address: String,
        descriptor: SecureAttachmentDescriptor,
    ): Result<String> = runCatching {
        val current = peer(context, address)
        check(current.canEncrypt) { "Secure Ping is not paired with this contact" }
        val recipientFingerprint = checkNotNull(current.fingerprint)
        val senderFingerprint = identity(context).fingerprint
        val payload = JSONObject()
            // Keep v1 for Wi-Fi interoperability with 0.6.x; Bluetooth fields are optional.
            .put("v", 1)
            .put("kind", "file")
            .put("id", descriptor.id)
            .put("ts", System.currentTimeMillis())
            .put("from", senderFingerprint)
            .put("to", recipientFingerprint)
            .put("name", descriptor.name)
            .put("mime", descriptor.mimeType)
            .put("ps", descriptor.plaintextSize)
            .put("ph", descriptor.plaintextSha256)
            .put("cs", descriptor.ciphertextSize)
            .put("ch", descriptor.ciphertextSha256)
            .put("key", encode(descriptor.contentKey))
            .put("nonce", encode(descriptor.nonce))
            .put("url", descriptor.downloadUrl ?: JSONObject.NULL)
            .put("token", descriptor.transportToken)
            .put("bt", descriptor.bluetoothAvailable)
            .put("btn", descriptor.bluetoothName ?: JSONObject.NULL)
            .toString()
            .toByteArray(UTF_8)
        val signature = signingKeyset(context).getPrimitive(PublicKeySign::class.java).sign(payload)
        val signedPayload = JSONObject()
            .put("p", encode(payload))
            .put("s", encode(signature))
            .toString()
            .toByteArray(UTF_8)
        val peerPublic = KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(current.encryptionPublicKey))
        val ciphertext = peerPublic.getPrimitive(HybridEncrypt::class.java).encrypt(
            signedPayload,
            contextInfo(recipientFingerprint),
        )
        storeOutgoing(context, descriptor.id, String(payload, UTF_8))
        ATTACHMENT_PREFIX + JSONObject()
            .put("v", 1)
            .put("id", descriptor.id)
            .put("c", encode(ciphertext))
            .toString()
            .toByteArray(UTF_8)
            .let(::encode)
    }

    fun decodeForDisplay(
        context: Context,
        address: String,
        body: String,
        incoming: Boolean,
    ): SecureDecodedMessage? = when {
        body.startsWith(INVITE_PREFIX) || body.startsWith(LEGACY_INVITE_PREFIX) -> decodeBundleForDisplay(
            body = body,
            prefix = if (body.startsWith(INVITE_PREFIX)) INVITE_PREFIX else LEGACY_INVITE_PREFIX,
            validText = if (incoming) "Secure Ping invitation received" else "Secure Ping invitation sent",
        )
        body.startsWith(ACCEPT_PREFIX) || body.startsWith(LEGACY_ACCEPT_PREFIX) -> decodeBundleForDisplay(
            body = body,
            prefix = if (body.startsWith(ACCEPT_PREFIX)) ACCEPT_PREFIX else LEGACY_ACCEPT_PREFIX,
            validText = "Secure Ping channel accepted",
            verified = peer(context, address).state == SecurePeerState.VERIFIED,
        )
        body.startsWith(MESSAGE_PREFIX) -> if (incoming) {
            decryptIncoming(context, address, body).getOrElse {
                SecureDecodedMessage(
                    text = "⚠ Secure Ping could not be decrypted or authenticated",
                    isSecure = true,
                    verified = false,
                )
            }
        } else runCatching {
            val messageId = parseMessageOuter(body).getString("id")
            val text = loadOutgoing(context, messageId)
                ?: "Encrypted Secure Ping sent from another installation"
            SecureDecodedMessage(
                text = text,
                isSecure = true,
                verified = peer(context, address).state == SecurePeerState.VERIFIED,
            )
        }.getOrElse {
            invalidSecureCapsule()
        }
        body.startsWith(ATTACHMENT_PREFIX) -> decodeAttachmentOffer(context, address, body, incoming)
            .fold(
                onSuccess = { attachment ->
                    SecureDecodedMessage(
                        text = "📎 ${attachment.name} • ${formatAttachmentSize(attachment.plaintextSize)} • ${attachment.transportLabel}",
                        isSecure = true,
                        verified = peer(context, address).state == SecurePeerState.VERIFIED,
                        attachment = attachment,
                    )
                },
                onFailure = { invalidSecureCapsule() },
            )
        else -> null
    }

    /** Removes the sender-side plaintext copy after its Telephony row is deleted. */
    fun forgetOutgoingPlaintext(context: Context, body: String) {
        val messageId = runCatching {
            when {
                body.startsWith(MESSAGE_PREFIX) -> parseMessageOuter(body).getString("id")
                body.startsWith(ATTACHMENT_PREFIX) -> parseAttachmentOuter(body).getString("id")
                else -> null
            }
        }.getOrNull() ?: return
        context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(messageId)
            .apply()
    }

    fun notificationText(context: Context, address: String, body: String): String = when {
        body.startsWith(INVITE_PREFIX) || body.startsWith(LEGACY_INVITE_PREFIX) -> if (
            isValidBundle(
                body,
                if (body.startsWith(INVITE_PREFIX)) INVITE_PREFIX else LEGACY_INVITE_PREFIX,
            )
        ) {
            "Secure Ping invitation received"
        } else {
            "Invalid Secure Ping invitation received"
        }
        body.startsWith(ACCEPT_PREFIX) || body.startsWith(LEGACY_ACCEPT_PREFIX) -> if (
            isValidBundle(
                body,
                if (body.startsWith(ACCEPT_PREFIX)) ACCEPT_PREFIX else LEGACY_ACCEPT_PREFIX,
            )
        ) {
            "Secure Ping channel accepted"
        } else {
            "Invalid Secure Ping response received"
        }
        body.startsWith(MESSAGE_PREFIX) -> decryptIncoming(context, address, body)
            .fold(
                onSuccess = { "Encrypted Secure Ping received" },
                onFailure = { "Unverified Secure Ping received" },
            )
        body.startsWith(ATTACHMENT_PREFIX) -> decodeAttachmentOffer(context, address, body, incoming = true)
            .fold(
                onSuccess = { "Encrypted attachment offer received" },
                onFailure = { "Invalid encrypted attachment offer received" },
            )
        else -> body
    }

    fun acceptIncomingAuthenticatedFrame(
        context: Context,
        address: String,
        body: String,
        now: Long = System.currentTimeMillis(),
    ): SecureFrameAcceptance {
        val kind = when {
            body.startsWith(MESSAGE_PREFIX) -> "message"
            body.startsWith(ATTACHMENT_PREFIX) -> "attachment"
            else -> return SecureFrameAcceptance.NOT_AUTHENTICATED_FRAME
        }
        val authenticated = runCatching {
            val outer = if (kind == "message") parseMessageOuter(body) else parseAttachmentOuter(body)
            val payload = decryptAndVerifyIncomingPayload(context, address, outer)
            if (kind == "message") {
                check(payload.getInt("v") == 1)
            } else {
                check(payload.getInt("v") in 1..2)
                check(payload.getString("kind") == "file")
            }
            val id = payload.getString("id")
            check(id == outer.getString("id"))
            AuthenticatedSecureFrame(
                kind = kind,
                id = id,
                timestamp = payload.getLong("ts"),
                senderFingerprint = payload.getString("from"),
                ciphertextSha256 = sha256(decode(outer.getString("c"))).joinToString("") {
                    "%02x".format(it)
                },
            )
        }.getOrElse { return SecureFrameAcceptance.INVALID }
        return SecureReplayRepository.accept(context, authenticated, now)
    }

    fun decodeAttachmentOffer(
        context: Context,
        address: String,
        body: String,
        incoming: Boolean,
    ): Result<SecureAttachmentDescriptor> = runCatching {
        val outer = parseAttachmentOuter(body)
        val payloadBytes = if (incoming) {
            decryptAndVerifyIncomingPayload(context, address, outer).toString().toByteArray(UTF_8)
        } else {
            val stored = loadOutgoing(context, outer.getString("id"))
                ?: error("Attachment metadata is unavailable on this installation")
            stored.toByteArray(UTF_8)
        }
        val payload = JSONObject(String(payloadBytes, UTF_8))
        check(payload.getInt("v") in 1..2)
        check(payload.getString("kind") == "file")
        check(payload.getString("id") == outer.getString("id"))
        attachmentDescriptor(payload, incoming)
    }

    fun safetyNumber(context: Context, address: String): String? {
        val peer = peer(context, address)
        val remote = if (peer.state == SecurePeerState.IDENTITY_CHANGE_PENDING) {
            peer.pendingFingerprint
        } else {
            peer.fingerprint
        } ?: return null
        val local = localFingerprint(context)
        val ordered = listOf(local, remote).sorted().joinToString("")
        val digest = sha256(ordered.toByteArray(UTF_8)).take(12)
        return digest.joinToString(" ") { "%02X".format(it) }.chunked(12).joinToString("  ")
    }

    private fun decryptIncoming(
        context: Context,
        address: String,
        body: String,
    ): Result<SecureDecodedMessage> = runCatching {
        val current = peer(context, address)
        val outer = parseMessageOuter(body)
        val payload = decryptAndVerifyIncomingPayload(context, address, outer)
        check(payload.getInt("v") == 1)
        check(payload.getString("id") == outer.getString("id"))
        check(payload.getString("from") == current.fingerprint)
        check(payload.getString("to") == localFingerprint(context))
        SecureDecodedMessage(
            text = payload.getString("text"),
            isSecure = true,
            verified = current.state == SecurePeerState.VERIFIED,
        )
    }

    private fun decryptAndVerifyIncomingPayload(
        context: Context,
        address: String,
        outer: JSONObject,
    ): JSONObject {
        val current = peer(context, address)
        check(current.signingPublicKey != null && current.fingerprint != null) {
            "Unknown Secure Ping sender"
        }
        val signedPayloadBytes = hpkeKeyset(context).getPrimitive(HybridDecrypt::class.java).decrypt(
            decode(outer.getString("c")),
            contextInfo(localFingerprint(context)),
        )
        val signedPayload = JSONObject(String(signedPayloadBytes, UTF_8))
        val payloadBytes = decode(signedPayload.getString("p"))
        val signature = decode(signedPayload.getString("s"))
        val publicSigning = KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(current.signingPublicKey))
        publicSigning.getPrimitive(PublicKeyVerify::class.java).verify(signature, payloadBytes)
        val payload = JSONObject(String(payloadBytes, UTF_8))
        check(payload.getString("from") == current.fingerprint)
        check(payload.getString("to") == localFingerprint(context))
        return payload
    }

    private fun parseMessageOuter(body: String): JSONObject {
        require(body.startsWith(MESSAGE_PREFIX))
        val outer = JSONObject(String(decode(body.removePrefix(MESSAGE_PREFIX)), UTF_8))
        require(outer.getInt("v") == 1)
        return outer
    }

    private fun parseAttachmentOuter(body: String): JSONObject {
        require(body.startsWith(ATTACHMENT_PREFIX))
        val outer = JSONObject(String(decode(body.removePrefix(ATTACHMENT_PREFIX)), UTF_8))
        require(outer.getInt("v") == 1)
        require(outer.getString("id").isNotBlank())
        return outer
    }

    private fun attachmentDescriptor(payload: JSONObject, incoming: Boolean): SecureAttachmentDescriptor {
        val id = payload.getString("id")
        val name = payload.getString("name")
        val mimeType = payload.getString("mime")
        val plaintextSize = payload.getLong("ps")
        val ciphertextSize = payload.getLong("cs")
        val plaintextHash = payload.getString("ph")
        val ciphertextHash = payload.getString("ch")
        val key = decode(payload.getString("key"))
        val nonce = decode(payload.getString("nonce"))
        val downloadUrl = payload.optString("url").takeIf { it.isNotBlank() && it != "null" }
        val bluetoothAvailable = payload.optBoolean("bt", false)
        val transportToken = payload.optString("token").takeIf(String::isNotBlank)
            ?: downloadUrl?.substringAfterLast('/')
            ?: ""
        val bluetoothName = payload.optString("btn").takeIf { it.isNotBlank() && it != "null" }
        require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        require(name.isNotBlank() && name.length <= 180 && '/' !in name && '\\' !in name)
        require(mimeType.isNotBlank() && mimeType.length <= 120)
        require(plaintextSize in 0..268_435_456L)
        require(ciphertextSize in 16..268_435_472L)
        require(plaintextHash.matches(Regex("[0-9a-f]{64}")))
        require(ciphertextHash.matches(Regex("[0-9a-f]{64}")))
        require(key.size == 32)
        require(nonce.size == 12)
        require(downloadUrl != null || bluetoothAvailable) { "Attachment offer has no transport" }
        require(transportToken.matches(Regex("[A-Za-z0-9_-]{32}"))) {
            "Attachment transport token is malformed"
        }
        require(bluetoothName == null || bluetoothName.length <= 120)
        return SecureAttachmentDescriptor(
            id = id,
            name = name,
            mimeType = mimeType,
            plaintextSize = plaintextSize,
            plaintextSha256 = plaintextHash,
            ciphertextSize = ciphertextSize,
            ciphertextSha256 = ciphertextHash,
            contentKey = key,
            nonce = nonce,
            downloadUrl = downloadUrl,
            transportToken = transportToken,
            bluetoothAvailable = bluetoothAvailable,
            bluetoothName = bluetoothName,
            incoming = incoming,
        )
    }

    private fun formatAttachmentSize(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    private fun decodeBundleForDisplay(
        body: String,
        prefix: String,
        validText: String,
        verified: Boolean = false,
    ): SecureDecodedMessage = if (isValidBundle(body, prefix)) {
        SecureDecodedMessage(validText, isSecure = true, verified = verified)
    } else {
        invalidSecureCapsule()
    }

    private fun isValidBundle(body: String, prefix: String): Boolean =
        runCatching { decodeBundle(body, prefix) }.isSuccess

    private fun invalidSecureCapsule(): SecureDecodedMessage = SecureDecodedMessage(
        text = "⚠ Invalid or unauthenticated Secure Ping capsule",
        isSecure = true,
        verified = false,
    )

    private data class IdentityBundle(
        val encryptionPublicKey: ByteArray,
        val signingPublicKey: ByteArray,
        val fingerprint: String,
        val timestamp: Long? = null,
        val controlId: String? = null,
    )

    private fun identity(context: Context): IdentityBundle {
        registerCrypto()
        val encryptionPublicKey = serializePublic(hpkeKeyset(context))
        val signingHandle = signingKeyset(context)
        val signingPublicKey = serializePublic(signingHandle)
        return IdentityBundle(
            encryptionPublicKey = encryptionPublicKey,
            signingPublicKey = signingPublicKey,
            fingerprint = fingerprint(encryptionPublicKey, signingPublicKey),
        )
    }

    private fun encodeBundle(
        context: Context,
        prefix: String,
        bundle: IdentityBundle,
        timestamp: Long = System.currentTimeMillis(),
        controlId: ByteArray = ByteArray(CONTROL_ID_BYTES).also(SecureRandom()::nextBytes),
    ): String {
        require(prefix == INVITE_PREFIX || prefix == ACCEPT_PREFIX)
        require(controlId.size == CONTROL_ID_BYTES)
        val encryption = compactPublicKey(bundle.encryptionPublicKey, HPKE_PUBLIC_TYPE_URL)
        val signing = compactPublicKey(bundle.signingPublicKey, ED25519_PUBLIC_TYPE_URL)
        val unsigned = ByteBuffer.allocate(
            1 + Long.SIZE_BYTES + CONTROL_ID_BYTES +
                Int.SIZE_BYTES + Short.SIZE_BYTES + encryption.value.size +
                Int.SIZE_BYTES + Short.SIZE_BYTES + signing.value.size,
        ).order(ByteOrder.BIG_ENDIAN)
            .put(3)
            .putLong(timestamp)
            .put(controlId)
            .putInt(encryption.keyId)
            .putShort(encryption.value.size.toShort())
            .put(encryption.value)
            .putInt(signing.keyId)
            .putShort(signing.value.size.toShort())
            .put(signing.value)
            .array()
        val signature = signingKeyset(context).getPrimitive(PublicKeySign::class.java).sign(unsigned)
        val wire = ByteBuffer.allocate(unsigned.size + Short.SIZE_BYTES + signature.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(unsigned)
            .putShort(signature.size.toShort())
            .put(signature)
            .array()
        return prefix + encode(wire)
    }

    private fun decodeBundle(body: String, prefix: String): IdentityBundle = when (prefix) {
        INVITE_PREFIX, ACCEPT_PREFIX -> decodeCompactBundle(body.removePrefix(prefix))
        LEGACY_INVITE_PREFIX, LEGACY_ACCEPT_PREFIX -> decodeAndVerifyLegacyBundle(body.removePrefix(prefix))
        else -> error("Unsupported Secure Ping key bundle")
    }

    private fun decodeCompactBundle(encoded: String): IdentityBundle {
        registerCrypto()
        require(encoded.length in 1..MAX_COMPACT_CONTROL_BASE64_CHARS) {
            "Compact pairing control exceeds its two-SMS bound"
        }
        require(COMPACT_CONTROL_BASE64.matches(encoded)) { "Invalid compact pairing encoding" }
        val wire = decode(encoded)
        val buffer = ByteBuffer.wrap(wire).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get().toInt()
        require(version == 2 || version == 3) { "Unsupported compact key bundle" }
        val timestamp = if (version == 3) buffer.long else null
        val controlIdBytes = if (version == 3) {
            ByteArray(CONTROL_ID_BYTES).also(buffer::get)
        } else {
            null
        }
        val encryption = readCompactPublicKey(buffer, HPKE_PUBLIC_TYPE_URL)
        val signing = readCompactPublicKey(buffer, ED25519_PUBLIC_TYPE_URL)
        if (version == 3) {
            val signedSize = buffer.position()
            require(buffer.remaining() >= Short.SIZE_BYTES) { "Missing pairing signature" }
            val signatureSize = buffer.short.toInt() and 0xffff
            require(signatureSize in 1..128 && buffer.remaining() == signatureSize) {
                "Invalid pairing signature length"
            }
            val signature = ByteArray(signatureSize).also(buffer::get)
            KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(signing))
                .getPrimitive(PublicKeyVerify::class.java)
                .verify(signature, wire.copyOfRange(0, signedSize))
        }
        require(!buffer.hasRemaining()) { "Unexpected compact key data" }
        KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(encryption))
            .getPrimitive(HybridEncrypt::class.java)
        KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(signing))
            .getPrimitive(PublicKeyVerify::class.java)
        return IdentityBundle(
            encryption,
            signing,
            fingerprint(encryption, signing),
            timestamp,
            controlIdBytes?.toHex(),
        )
    }

    private fun decodeAndVerifyLegacyBundle(encoded: String): IdentityBundle {
        registerCrypto()
        val json = JSONObject(String(decode(encoded), UTF_8))
        require(json.getInt("v") == 1)
        val encryptionEncoded = json.getString("e")
        val signingEncoded = json.getString("s")
        val encryption = decode(encryptionEncoded)
        val signing = decode(signingEncoded)
        val claimedFingerprint = json.getString("f")
        require(fingerprint(encryption, signing) == claimedFingerprint) { "Fingerprint mismatch" }
        val signingHandle = KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(signing))
        signingHandle.getPrimitive(PublicKeyVerify::class.java).verify(
            decode(json.getString("x")),
            bundleCanonical(encryptionEncoded, signingEncoded, claimedFingerprint),
        )
        return IdentityBundle(encryption, signing, claimedFingerprint)
    }

    private fun bundleCanonical(encryption: String, signing: String, fingerprint: String): ByteArray =
        "1|$encryption|$signing|$fingerprint".toByteArray(UTF_8)

    private data class CompactPublicKey(val keyId: Int, val value: ByteArray)

    private fun compactPublicKey(serialized: ByteArray, expectedTypeUrl: String): CompactPublicKey {
        val keyset = Keyset.parseFrom(serialized)
        require(keyset.keyCount == 1) { "Secure Ping requires one public key" }
        val key = keyset.getKey(0)
        require(key.keyId == keyset.primaryKeyId)
        require(key.status == KeyStatusType.ENABLED)
        require(key.outputPrefixType == OutputPrefixType.TINK)
        require(key.keyData.typeUrl == expectedTypeUrl)
        return CompactPublicKey(key.keyId, key.keyData.value.toByteArray())
    }

    private fun readCompactPublicKey(buffer: ByteBuffer, typeUrl: String): ByteArray {
        require(buffer.remaining() >= Int.SIZE_BYTES + Short.SIZE_BYTES)
        val keyId = buffer.int
        val size = buffer.short.toInt() and 0xffff
        require(size in 1..512 && buffer.remaining() >= size)
        val value = ByteArray(size)
        buffer.get(value)
        val keyData = KeyData.newBuilder()
            .setTypeUrl(typeUrl)
            .setValue(ByteString.copyFrom(value))
            .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PUBLIC)
            .build()
        val key = Keyset.Key.newBuilder()
            .setKeyData(keyData)
            .setStatus(KeyStatusType.ENABLED)
            .setKeyId(keyId)
            .setOutputPrefixType(OutputPrefixType.TINK)
            .build()
        return Keyset.newBuilder()
            .setPrimaryKeyId(keyId)
            .addKey(key)
            .build()
            .toByteArray()
    }

    private fun hpkeKeyset(context: Context): KeysetHandle {
        registerCrypto()
        val parameters = HpkeParameters.builder()
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
            .setVariant(HpkeParameters.Variant.TINK)
            .build()
        return encryptedManager(
            context,
            HPKE_KEYSET,
            KeyTemplate.createFrom(parameters),
        ).keysetHandle
    }

    private fun signingKeyset(context: Context): KeysetHandle {
        registerCrypto()
        return encryptedManager(context, SIGNING_KEYSET, SignatureKeyTemplates.ED25519).keysetHandle
    }

    private fun vaultAead(context: Context): Aead {
        registerCrypto()
        return encryptedManager(context, VAULT_KEYSET, AeadKeyTemplates.AES256_GCM)
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    private fun encryptedManager(
        context: Context,
        keysetName: String,
        template: com.google.crypto.tink.proto.KeyTemplate,
    ): AndroidKeysetManager = encryptedManager(
        context,
        keysetName,
        KeyTemplate.create(template.typeUrl, template.value.toByteArray(), KeyTemplate.OutputPrefixType.TINK),
    )

    private fun encryptedManager(
        context: Context,
        keysetName: String,
        template: KeyTemplate,
    ): AndroidKeysetManager {
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, keysetName, KEYSET_PREFS)
            .withMasterKeyUri(MASTER_KEY_URI)
            .withKeyTemplate(template)
            .build()
        check(manager.isUsingKeystore) { "Android Keystore is unavailable; Secure Ping remains disabled" }
        return manager
    }

    private fun serializePublic(privateHandle: KeysetHandle): ByteArray {
        val output = ByteArrayOutputStream()
        privateHandle.publicKeysetHandle.writeNoSecret(BinaryKeysetWriter.withOutputStream(output))
        return output.toByteArray()
    }

    private fun savePeer(context: Context, peer: SecurePeer) {
        val json = JSONObject()
            .put("address", peer.address)
            .put("e", peer.encryptionPublicKey?.let(::encode).orEmpty())
            .put("s", peer.signingPublicKey?.let(::encode).orEmpty())
            .put("f", peer.fingerprint.orEmpty())
            .put("state", peer.state.name)
            .put("pe", peer.pendingEncryptionPublicKey?.let(::encode).orEmpty())
            .put("ps", peer.pendingSigningPublicKey?.let(::encode).orEmpty())
            .put("pf", peer.pendingFingerprint.orEmpty())
            .put("pa", peer.pendingRequiresAcceptance)
        context.getSharedPreferences(PEER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(peerKey(peer.address), json.toString())
            .apply()
    }

    private fun storeOutgoing(context: Context, messageId: String, plaintext: String) {
        val encrypted = vaultAead(context).encrypt(plaintext.toByteArray(UTF_8), messageId.toByteArray(UTF_8))
        context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(messageId, encode(encrypted))
            .apply()
    }

    private fun loadOutgoing(context: Context, messageId: String): String? = runCatching {
        val encrypted = context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
            .getString(messageId, null) ?: return null
        String(
            vaultAead(context).decrypt(decode(encrypted), messageId.toByteArray(UTF_8)),
            UTF_8,
        )
    }.getOrNull()

    fun saveEncryptedDraft(context: Context, draftId: String, plaintext: String) {
        val storageKey = encryptedDraftStorageKey(draftId)
        if (plaintext.isBlank()) {
            context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(storageKey)
                .apply()
            return
        }
        val associatedData = "draft|$draftId".toByteArray(UTF_8)
        val encrypted = vaultAead(context).encrypt(plaintext.toByteArray(UTF_8), associatedData)
        context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(storageKey, encode(encrypted))
            .apply()
    }

    fun loadEncryptedDraft(context: Context, draftId: String): String? = runCatching {
        val encrypted = context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
            .getString(encryptedDraftStorageKey(draftId), null) ?: return null
        val associatedData = "draft|$draftId".toByteArray(UTF_8)
        String(vaultAead(context).decrypt(decode(encrypted), associatedData), UTF_8)
    }.getOrNull()

    fun clearEncryptedDraft(context: Context, draftId: String) {
        context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(encryptedDraftStorageKey(draftId))
            .apply()
    }

    private fun encryptedDraftStorageKey(draftId: String): String =
        "draft.${encode(MessageDigest.getInstance("SHA-256").digest(draftId.toByteArray(UTF_8)))}"

    private fun registerCrypto() {
        if (cryptoRegistered) return
        synchronized(this) {
            if (cryptoRegistered) return
            HybridConfig.register()
            SignatureConfig.register()
            AeadConfig.register()
            cryptoRegistered = true
        }
    }

    private fun fingerprint(encryption: ByteArray, signing: ByteArray): String =
        sha256(encryption + signing).take(16).joinToString("") { "%02X".format(it) }

    private fun contextInfo(recipientFingerprint: String): ByteArray =
        (CONTEXT_LABEL + recipientFingerprint).toByteArray(UTF_8)

    private fun peerKey(address: String): String =
        sha256(normalizeAddress(address).toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }

    private fun addressesEquivalent(context: Context, left: String, right: String): Boolean {
        val normalizedLeft = normalizeAddress(left)
        val normalizedRight = normalizeAddress(right)
        if (normalizedLeft == normalizedRight) return true
        if (normalizedLeft.count(Char::isDigit) < 7 || normalizedRight.count(Char::isDigit) < 7) return false
        @Suppress("DEPRECATION")
        return PhoneNumberUtils.compare(context, normalizedLeft, normalizedRight)
    }

    private fun normalizeAddress(address: String): String = address.trim().filter { it.isDigit() || it == '+' }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, BASE64_FLAGS)

    private fun decode(encoded: String): ByteArray = Base64.decode(encoded, BASE64_FLAGS)
}
