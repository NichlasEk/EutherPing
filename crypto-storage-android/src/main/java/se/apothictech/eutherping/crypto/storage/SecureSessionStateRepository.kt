// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import se.apothictech.eutherping.crypto.ProtocolStateRepository
import se.apothictech.eutherping.crypto.ProtocolStateTransaction

/**
 * Android implementation of the `secure_sessions_v3` state boundary.
 *
 * Each local identity has one app-private, no-backup file. The complete
 * copy-on-write record set is authenticated and encrypted with an AES-GCM key
 * held by Android Keystore, then replaced through [AtomicFile].
 */
class SecureSessionStateRepository private constructor(
    context: Context,
    private val namespace: String,
    private val beforeFinishWrite: (() -> Unit)?,
) : ProtocolStateRepository {
    constructor(context: Context, namespace: String) : this(context, namespace, null)

    private val applicationContext = context.applicationContext
    private val namespaceId = sha256(namespace.encodeToByteArray()).toHex()
    private val directory = File(applicationContext.noBackupFilesDir, DIRECTORY).apply {
        check(isDirectory || mkdirs()) { "Unable to create secure session directory" }
    }
    private val atomicFile = AtomicFile(File(directory, "$namespaceId.state"))
    private val associatedData = "$AAD_PREFIX$namespaceId".encodeToByteArray()
    private val lock = locks.computeIfAbsent(atomicFile.baseFile.absolutePath) { Any() }

    init {
        require(namespace.isNotBlank()) { "Secure session namespace must not be blank" }
    }

    override fun <T> transaction(
        lockId: String,
        block: (ProtocolStateTransaction) -> T,
    ): T = synchronized(lock) {
        require(lockId.isNotBlank()) { "Protocol transaction lock ID must not be blank" }
        val working = loadRecords().mapValuesTo(linkedMapOf()) { (_, value) -> value.copyOf() }
        try {
            val transaction = StateTransaction(working)
            val result = block(transaction)
            if (transaction.changed) writeRecords(working)
            result
        } finally {
            working.values.forEach { it.fill(0) }
            working.clear()
        }
    }

    private fun loadRecords(): Map<String, ByteArray> {
        if (!atomicFile.baseFile.isFile) return emptyMap()
        val encrypted = atomicFile.openRead().use { input ->
            check(input.available() <= MAX_FILE_BYTES) { "Secure session file exceeds limit" }
            input.readBytes()
        }
        check(encrypted.size <= MAX_FILE_BYTES) { "Secure session file exceeds limit" }
        val plaintext = try {
            decryptContainer(encrypted)
        } finally {
            encrypted.fill(0)
        }
        return decodeRecords(plaintext)
    }

    private fun writeRecords(records: Map<String, ByteArray>) {
        val plaintext = encodeRecords(records)
        val encrypted = try {
            encryptContainer(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val output = atomicFile.startWrite()
        try {
            output.write(encrypted)
            output.flush()
            output.fd.sync()
            beforeFinishWrite?.invoke()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        } finally {
            encrypted.fill(0)
        }
    }

    private fun encryptContainer(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, sessionKey())
            updateAAD(associatedData)
        }
        val nonce = cipher.iv.also {
            check(it.size == NONCE_BYTES) { "Android Keystore returned an invalid GCM nonce" }
        }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream(CONTAINER_HEADER_BYTES + ciphertext.size).use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(CONTAINER_MAGIC)
                output.writeByte(CONTAINER_VERSION)
                output.writeByte(nonce.size)
                output.writeInt(ciphertext.size)
                output.write(nonce)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }.also {
            nonce.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun decryptContainer(container: ByteArray): ByteArray {
        require(container.size in CONTAINER_HEADER_BYTES..MAX_FILE_BYTES) {
            "Invalid secure session container size"
        }
        return DataInputStream(ByteArrayInputStream(container)).use { input ->
            val magic = ByteArray(CONTAINER_MAGIC.size).also(input::readFully)
            require(magic.contentEquals(CONTAINER_MAGIC)) { "Invalid secure session container" }
            require(input.readUnsignedByte() == CONTAINER_VERSION) {
                "Unsupported secure session container version"
            }
            val nonceLength = input.readUnsignedByte()
            require(nonceLength == NONCE_BYTES) { "Invalid secure session nonce" }
            val ciphertextLength = input.readInt()
            require(ciphertextLength >= TAG_BYTES && ciphertextLength == input.available() - nonceLength) {
                "Invalid secure session ciphertext length"
            }
            val nonce = ByteArray(nonceLength).also(input::readFully)
            val ciphertext = ByteArray(ciphertextLength).also(input::readFully)
            try {
                Cipher.getInstance(CIPHER).run {
                    init(Cipher.DECRYPT_MODE, sessionKey(), GCMParameterSpec(TAG_BITS, nonce))
                    updateAAD(associatedData)
                    doFinal(ciphertext)
                }
            } finally {
                nonce.fill(0)
                ciphertext.fill(0)
            }
        }
    }

    private fun encodeRecords(records: Map<String, ByteArray>): ByteArray {
        require(records.size <= MAX_RECORDS) { "Too many secure session records" }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(RECORD_MAGIC)
                output.writeByte(RECORD_VERSION)
                output.writeInt(records.size)
                records.toSortedMap().forEach { (key, value) ->
                    val keyBytes = key.encodeToByteArray()
                    require(keyBytes.isNotEmpty() && keyBytes.size <= MAX_KEY_BYTES) {
                        "Invalid secure session record key"
                    }
                    require(value.size <= MAX_VALUE_BYTES) { "Secure session record exceeds limit" }
                    output.writeShort(keyBytes.size)
                    output.writeInt(value.size)
                    output.write(keyBytes)
                    output.write(value)
                }
            }
            require(bytes.size() <= MAX_PLAINTEXT_BYTES) { "Secure session state exceeds limit" }
            bytes.toByteArray()
        }
    }

    private fun decodeRecords(plaintext: ByteArray): Map<String, ByteArray> = try {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Secure session state exceeds limit" }
        DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
            val magic = ByteArray(RECORD_MAGIC.size).also(input::readFully)
            require(magic.contentEquals(RECORD_MAGIC)) { "Invalid secure session record file" }
            require(input.readUnsignedByte() == RECORD_VERSION) {
                "Unsupported secure session record version"
            }
            val count = input.readInt()
            require(count in 0..MAX_RECORDS) { "Invalid secure session record count" }
            buildMap {
                repeat(count) {
                    val keyLength = input.readUnsignedShort()
                    val valueLength = input.readInt()
                    require(keyLength in 1..MAX_KEY_BYTES && valueLength in 0..MAX_VALUE_BYTES) {
                        "Invalid secure session record length"
                    }
                    require(keyLength + valueLength <= input.available()) {
                        "Truncated secure session record"
                    }
                    val key = ByteArray(keyLength).also(input::readFully).decodeToString()
                    require(key.isNotBlank() && key !in this) { "Invalid secure session record key" }
                    put(key, ByteArray(valueLength).also(input::readFully))
                }
                require(input.available() == 0) { "Trailing secure session record data" }
            }
        }
    } finally {
        plaintext.fill(0)
    }

    private fun sessionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        synchronized(keyCreationLock) {
            val refreshed = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (refreshed.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                generateKey()
            }
        }
    }

    /** Deletes this identity's state after an explicit, verified reset. */
    fun deleteAllStateForVerifiedReset() = synchronized(lock) { atomicFile.delete() }

    internal fun stateFileForTesting(): File = atomicFile.baseFile

    internal fun deleteForTesting() = deleteAllStateForVerifiedReset()

    internal companion object {
        fun withWriteFailureForTesting(
            context: Context,
            namespace: String,
            failure: () -> Unit,
        ) = SecureSessionStateRepository(context, namespace, failure)

        private const val DIRECTORY = "secure_sessions_v3"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "eutherping_secure_sessions_v3_master"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val AAD_PREFIX = "EutherPing|secure_sessions_v3|1|"
        private const val CONTAINER_VERSION = 1
        private const val RECORD_VERSION = 1
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val CONTAINER_HEADER_BYTES = 4 + 1 + 1 + 4 + NONCE_BYTES + TAG_BYTES
        private const val MAX_FILE_BYTES = 8 * 1024 * 1024
        private const val MAX_PLAINTEXT_BYTES = 6 * 1024 * 1024
        private const val MAX_RECORDS = 1024
        private const val MAX_KEY_BYTES = 512
        private const val MAX_VALUE_BYTES = 2 * 1024 * 1024
        private val CONTAINER_MAGIC = "EPS3".encodeToByteArray()
        private val RECORD_MAGIC = "EPSR".encodeToByteArray()
        private val keyCreationLock = Any()
        private val locks = ConcurrentHashMap<String, Any>()

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

private class StateTransaction(
    private val records: MutableMap<String, ByteArray>,
) : ProtocolStateTransaction {
    var changed: Boolean = false
        private set

    override fun read(key: String): ByteArray? = records[key]?.copyOf()

    override fun write(key: String, value: ByteArray) {
        val replacement = value.copyOf()
        val previous = records.put(key, replacement)
        if (previous == null || !previous.contentEquals(replacement)) changed = true
        previous?.fill(0)
    }

    override fun delete(key: String) {
        records.remove(key)?.let {
            it.fill(0)
            changed = true
        }
    }

    override fun keys(prefix: String): Set<String> = records.keys.filterTo(linkedSetOf()) {
        it.startsWith(prefix)
    }
}
