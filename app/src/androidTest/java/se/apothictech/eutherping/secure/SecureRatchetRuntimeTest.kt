package se.apothictech.eutherping.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureRatchetRuntimeTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearState() {
        SecureRatchetRuntime.deleteAllStateForVerifiedReset(context)
    }

    @After
    fun cleanUp() {
        SecureRatchetRuntime.deleteAllStateForVerifiedReset(context)
    }

    @Test
    fun alwaysOnRuntimeCreatesEncryptedIdentityOnceAndReloadsIt() {
        val first = SecureRatchetRuntime.ensureReady(context).getOrThrow()
        assertTrue(first.payload.size == 164)

        val namespaceId = MessageDigest.getInstance("SHA-256")
            .digest(SecureRatchetRuntime.NAMESPACE.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
        val encryptedState = File(
            File(context.noBackupFilesDir, "secure_sessions_v3"),
            "$namespaceId.state",
        )
        assertTrue(encryptedState.isFile)
        assertFalse(encryptedState.readBytes().contains(first.payload))

        SecureRatchetRuntime.resetProcessCacheForTesting()
        val reloaded = SecureRatchetRuntime.ensureReady(context).getOrThrow()
        assertArrayEquals(first.payload, reloaded.payload)
        assertFalse(SecureRatchetRuntime.descriptor.productionReady)
    }
}

private fun ByteArray.contains(needle: ByteArray): Boolean =
    needle.isNotEmpty() && indices.any { start ->
        start + needle.size <= size && needle.indices.all { offset ->
            this[start + offset] == needle[offset]
        }
    }
