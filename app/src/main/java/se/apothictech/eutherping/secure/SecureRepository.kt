package se.apothictech.eutherping.secure

import android.content.Context
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
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID

enum class SecurePeerState {
    NONE,
    INVITE_SENT,
    INVITE_RECEIVED,
    ACTIVE_UNVERIFIED,
    VERIFIED,
}

data class SecurePeer(
    val address: String,
    val encryptionPublicKey: ByteArray?,
    val signingPublicKey: ByteArray?,
    val fingerprint: String?,
    val state: SecurePeerState,
) {
    val canEncrypt: Boolean
        get() = encryptionPublicKey != null &&
            signingPublicKey != null &&
            state in setOf(SecurePeerState.ACTIVE_UNVERIFIED, SecurePeerState.VERIFIED)
}

data class SecureDecodedMessage(
    val text: String,
    val isSecure: Boolean,
    val verified: Boolean,
)

object SecureRepository {
    const val INVITE_PREFIX = "EP1I:"
    const val ACCEPT_PREFIX = "EP1A:"
    const val MESSAGE_PREFIX = "EP1M:"

    private const val KEYSET_PREFS = "eutherping_secure_keysets"
    private const val PEER_PREFS = "eutherping_secure_peers"
    private const val VAULT_PREFS = "eutherping_secure_vault"
    private const val HPKE_KEYSET = "hpke_private_v1"
    private const val SIGNING_KEYSET = "signing_private_v1"
    private const val VAULT_KEYSET = "vault_aead_v1"
    private const val MASTER_KEY_URI = "android-keystore://eutherping_secure_master_v1"
    private const val CONTEXT_LABEL = "EutherPing Secure Beta v1|"
    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING

    @Volatile
    private var cryptoRegistered = false

    fun ensureIdentity(context: Context): Result<String> = runCatching {
        identity(context).fingerprint
    }

    fun localFingerprint(context: Context): String = identity(context).fingerprint

    fun peer(context: Context, address: String): SecurePeer {
        val normalized = normalizeAddress(address)
        val raw = context.getSharedPreferences(PEER_PREFS, Context.MODE_PRIVATE)
            .getString(peerKey(normalized), null)
            ?: return SecurePeer(normalized, null, null, null, SecurePeerState.NONE)
        return runCatching {
            val json = JSONObject(raw)
            SecurePeer(
                address = normalized,
                encryptionPublicKey = json.optString("e").takeIf(String::isNotBlank)?.let(::decode),
                signingPublicKey = json.optString("s").takeIf(String::isNotBlank)?.let(::decode),
                fingerprint = json.optString("f").takeIf(String::isNotBlank),
                state = SecurePeerState.valueOf(json.getString("state")),
            )
        }.getOrElse { SecurePeer(normalized, null, null, null, SecurePeerState.NONE) }
    }

    fun createInvitation(context: Context, address: String): Result<String> = runCatching {
        val normalized = normalizeAddress(address)
        require(normalized.isNotBlank()) { "A destination number is required" }
        val wire = encodeBundle(INVITE_PREFIX, identity(context))
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
        encodeBundle(ACCEPT_PREFIX, identity(context))
    }

    fun markVerified(context: Context, address: String) {
        val current = peer(context, address)
        if (current.canEncrypt) savePeer(context, current.copy(state = SecurePeerState.VERIFIED))
    }

    fun handleIncomingControl(context: Context, address: String, body: String): Boolean {
        val prefix = when {
            body.startsWith(INVITE_PREFIX) -> INVITE_PREFIX
            body.startsWith(ACCEPT_PREFIX) -> ACCEPT_PREFIX
            else -> return false
        }
        val bundle = runCatching { decodeAndVerifyBundle(body.removePrefix(prefix)) }.getOrNull()
            ?: return false
        val current = peer(context, address)
        val sameVerifiedIdentity = current.state == SecurePeerState.VERIFIED &&
            current.fingerprint == bundle.fingerprint
        val nextState = when {
            sameVerifiedIdentity -> SecurePeerState.VERIFIED
            prefix == ACCEPT_PREFIX -> SecurePeerState.ACTIVE_UNVERIFIED
            else -> SecurePeerState.INVITE_RECEIVED
        }
        savePeer(
            context,
            SecurePeer(
                address = normalizeAddress(address),
                encryptionPublicKey = bundle.encryptionPublicKey,
                signingPublicKey = bundle.signingPublicKey,
                fingerprint = bundle.fingerprint,
                state = nextState,
            ),
        )
        return true
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

    fun decodeForDisplay(
        context: Context,
        address: String,
        body: String,
        incoming: Boolean,
    ): SecureDecodedMessage? = when {
        body.startsWith(INVITE_PREFIX) -> decodeBundleForDisplay(
            body = body,
            prefix = INVITE_PREFIX,
            validText = if (incoming) "Secure Ping invitation received" else "Secure Ping invitation sent",
        )
        body.startsWith(ACCEPT_PREFIX) -> decodeBundleForDisplay(
            body = body,
            prefix = ACCEPT_PREFIX,
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
        else -> null
    }

    fun notificationText(context: Context, address: String, body: String): String = when {
        body.startsWith(INVITE_PREFIX) -> if (isValidBundle(body, INVITE_PREFIX)) {
            "Secure Ping invitation received"
        } else {
            "Invalid Secure Ping invitation received"
        }
        body.startsWith(ACCEPT_PREFIX) -> if (isValidBundle(body, ACCEPT_PREFIX)) {
            "Secure Ping channel accepted"
        } else {
            "Invalid Secure Ping response received"
        }
        body.startsWith(MESSAGE_PREFIX) -> decryptIncoming(context, address, body)
            .fold(
                onSuccess = { "Encrypted Secure Ping received" },
                onFailure = { "Unverified Secure Ping received" },
            )
        else -> body
    }

    fun safetyNumber(context: Context, address: String): String? {
        val remote = peer(context, address).fingerprint ?: return null
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
        check(current.signingPublicKey != null && current.fingerprint != null) {
            "Unknown Secure Ping sender"
        }
        val outer = parseMessageOuter(body)
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

    private fun parseMessageOuter(body: String): JSONObject {
        require(body.startsWith(MESSAGE_PREFIX))
        val outer = JSONObject(String(decode(body.removePrefix(MESSAGE_PREFIX)), UTF_8))
        require(outer.getInt("v") == 1)
        return outer
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
        runCatching { decodeAndVerifyBundle(body.removePrefix(prefix)) }.isSuccess

    private fun invalidSecureCapsule(): SecureDecodedMessage = SecureDecodedMessage(
        text = "⚠ Invalid or unauthenticated Secure Ping capsule",
        isSecure = true,
        verified = false,
    )

    private data class IdentityBundle(
        val encryptionPublicKey: ByteArray,
        val signingPublicKey: ByteArray,
        val fingerprint: String,
        val signer: PublicKeySign? = null,
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
            signer = signingHandle.getPrimitive(PublicKeySign::class.java),
        )
    }

    private fun encodeBundle(prefix: String, bundle: IdentityBundle): String {
        val encryption = encode(bundle.encryptionPublicKey)
        val signing = encode(bundle.signingPublicKey)
        val unsigned = bundleCanonical(encryption, signing, bundle.fingerprint)
        val signature = checkNotNull(bundle.signer).sign(unsigned)
        return prefix + JSONObject()
            .put("v", 1)
            .put("e", encryption)
            .put("s", signing)
            .put("f", bundle.fingerprint)
            .put("x", encode(signature))
            .toString()
            .toByteArray(UTF_8)
            .let(::encode)
    }

    private fun decodeAndVerifyBundle(encoded: String): IdentityBundle {
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

    private fun normalizeAddress(address: String): String = address.trim().filter { it.isDigit() || it == '+' }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, BASE64_FLAGS)

    private fun decode(encoded: String): ByteArray = Base64.decode(encoded, BASE64_FLAGS)
}
