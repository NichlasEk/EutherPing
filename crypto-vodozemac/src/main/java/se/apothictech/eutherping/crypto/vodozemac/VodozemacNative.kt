// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.vodozemac

import java.nio.ByteBuffer
import java.nio.ByteOrder
import se.apothictech.eutherping.crypto.ProtocolCiphertextKind

internal object VodozemacNative {
    init {
        System.loadLibrary("eutherping_vodozemac")
    }

    external fun createAccount(): ByteArray

    external fun createPreKeyPublication(account: ByteArray): ByteArray

    external fun establishOutbound(account: ByteArray, publication: ByteArray): ByteArray

    external fun encrypt(account: ByteArray, session: ByteArray, plaintext: ByteArray): ByteArray

    external fun decrypt(account: ByteArray, session: ByteArray, ciphertext: ByteArray): ByteArray
}

internal object NativeFrames {
    private val publicationMagic = "EVK1".encodeToByteArray()
    private val ciphertextMagic = "EVC1".encodeToByteArray()
    private const val PUBLICATION_BYTES = 164
    private const val PRE_KEY_OVERHEAD = 101
    private const val SESSION_OVERHEAD = 5

    fun publicationSigningKey(publication: ByteArray): ByteArray {
        require(publication.size == PUBLICATION_BYTES && publication.startsWith(publicationMagic)) {
            "Malformed Vodozemac publication"
        }
        return publication.copyOfRange(36, 68)
    }

    fun preKeySigningKey(ciphertext: ByteArray): ByteArray {
        require(ciphertextKind(ciphertext) == ProtocolCiphertextKind.PRE_KEY) {
            "Expected a pre-key ciphertext"
        }
        require(ciphertext.size > PRE_KEY_OVERHEAD) { "Truncated pre-key ciphertext" }
        return ciphertext.copyOfRange(5, 37)
    }

    fun ciphertextKind(ciphertext: ByteArray): ProtocolCiphertextKind {
        require(ciphertext.size > SESSION_OVERHEAD && ciphertext.startsWith(ciphertextMagic)) {
            "Malformed Vodozemac ciphertext"
        }
        return when (ciphertext[4].toInt()) {
            0 -> ProtocolCiphertextKind.PRE_KEY
            1 -> ProtocolCiphertextKind.SESSION
            else -> throw IllegalArgumentException("Unsupported Vodozemac ciphertext kind")
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

internal object NativeFields {
    private val magic = "EVR1".encodeToByteArray()

    fun decode(encoded: ByteArray, expectedCount: Int): List<ByteArray> {
        require(encoded.size >= 5 && encoded.copyOfRange(0, 4).contentEquals(magic)) {
            "Malformed native response"
        }
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        val count = buffer.get().toInt() and 0xff
        require(count == expectedCount) { "Unexpected native response field count" }
        val fields = ArrayList<ByteArray>(count)
        repeat(count) {
            require(buffer.remaining() >= Int.SIZE_BYTES) { "Truncated native response" }
            val length = buffer.int
            require(length >= 0 && length <= buffer.remaining()) { "Invalid native field length" }
            fields += ByteArray(length).also(buffer::get)
        }
        require(!buffer.hasRemaining()) { "Trailing native response bytes" }
        return fields
    }
}
