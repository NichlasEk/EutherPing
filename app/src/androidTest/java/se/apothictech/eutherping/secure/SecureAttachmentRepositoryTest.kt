package se.apothictech.eutherping.secure

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureAttachmentRepositoryTest {
    @Test
    fun imagePreviewDecryptsIntoMemoryWithoutLeavingPlaintextCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecureAttachmentRepository.clearTransientPlaintext(context)
        val id = "preview-memory-test"
        val plaintext = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).let { bitmap ->
                bitmap.eraseColor(0xff7a21c8.toInt())
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                bitmap.recycle()
            }
            output.toByteArray()
        }
        val key = ByteArray(32) { index -> (index + 1).toByte() }
        val nonce = ByteArray(12) { index -> (index + 33).toByte() }
        val encrypted = File(context.filesDir, "secure_attachments/outgoing/$id.enc").apply {
            parentFile?.mkdirs()
            writeBytes(encrypt(id, plaintext, key, nonce))
        }
        val descriptor = descriptor(id, plaintext, encrypted, key, nonce)

        val bitmap = SecureAttachmentRepository.loadImagePreview(context, descriptor).getOrThrow()

        assertEquals(8, bitmap.width)
        assertEquals(8, bitmap.height)
        assertFalse(encrypted.readBytes().contentEquals(plaintext))
        assertNoFiles(context, "secure_attachment_preview")
        assertNoFiles(context, "secure_attachment_save")
        bitmap.recycle()
        plaintext.fill(0)
        encrypted.delete()
    }

    @Test
    fun authenticatedMemoryDecryptRejectsTamperedCiphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "tamper-test"
        val plaintext = "Vessel plaintext must stay private".toByteArray()
        val key = ByteArray(32) { index -> (index + 5).toByte() }
        val nonce = ByteArray(12) { index -> (index + 71).toByte() }
        val encrypted = File(context.cacheDir, "$id.enc").apply {
            writeBytes(encrypt(id, plaintext, key, nonce).also { bytes ->
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            })
        }
        val descriptor = descriptor(id, plaintext, encrypted, key, nonce)

        val result = runCatching {
            SecureAttachmentRepository.decryptVerifiedBytes(descriptor, encrypted, 1024)
        }

        assertTrue(result.isFailure)
        assertArrayEquals(plaintext, "Vessel plaintext must stay private".toByteArray())
        encrypted.delete()
    }

    @Test
    fun startupCleanupCoversEveryTransientSecureDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("secure_attachment_view", "secure_attachment_preview", "secure_attachment_save")
            .forEach { directory ->
                File(context.cacheDir, directory).apply { mkdirs() }
                    .resolve("plaintext.tmp")
                    .writeText("temporary Vessel plaintext")
            }

        SecureAttachmentRepository.clearTransientPlaintext(context)

        assertNoFiles(context, "secure_attachment_view")
        assertNoFiles(context, "secure_attachment_preview")
        assertNoFiles(context, "secure_attachment_save")
    }

    private fun descriptor(
        id: String,
        plaintext: ByteArray,
        encrypted: File,
        key: ByteArray,
        nonce: ByteArray,
    ) = SecureAttachmentDescriptor(
        id = id,
        name = "$id.png",
        mimeType = "image/png",
        plaintextSize = plaintext.size.toLong(),
        plaintextSha256 = MessageDigest.getInstance("SHA-256").digest(plaintext).toHex(),
        ciphertextSize = encrypted.length(),
        ciphertextSha256 = MessageDigest.getInstance("SHA-256").digest(encrypted.readBytes()).toHex(),
        contentKey = key,
        nonce = nonce,
        downloadUrl = null,
        transportToken = "test",
        bluetoothAvailable = false,
        bluetoothName = null,
        incoming = false,
    )

    private fun encrypt(id: String, plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(id.toByteArray())
            doFinal(plaintext)
        }

    private fun assertNoFiles(context: Context, directory: String) {
        assertTrue(File(context.cacheDir, directory).listFiles().isNullOrEmpty())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
