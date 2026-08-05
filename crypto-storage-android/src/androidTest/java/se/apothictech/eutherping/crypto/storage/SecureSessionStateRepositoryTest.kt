// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionStateRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun realKeystoreCiphertextReloadsFromNoBackupStorage() {
        val namespace = "reload-${UUID.randomUUID()}"
        val marker = "opaque-secret-${UUID.randomUUID()}".encodeToByteArray()
        val repository = SecureSessionStateRepository(context, namespace)
        try {
            repository.transaction("write") { state ->
                state.write("session/peer", marker)
                state.write("identity/peer", byteArrayOf(1, 2, 3))
            }

            val stateFile = repository.stateFileForTesting()
            assertTrue(stateFile.isFile)
            assertTrue(stateFile.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
            assertFalse(stateFile.readBytes().contains(marker))
            val committedCiphertext = stateFile.readBytes()

            val reloaded = SecureSessionStateRepository(context, namespace)
            val value = reloaded.transaction("reload") { it.read("session/peer") }
            assertArrayEquals(marker, value)
            assertArrayEquals(committedCiphertext, stateFile.readBytes())
        } finally {
            marker.fill(0)
            repository.deleteForTesting()
        }
    }

    @Test
    fun blockAndAtomicWriteFailuresPreserveLastCommittedCiphertext() {
        val namespace = "rollback-${UUID.randomUUID()}"
        val repository = SecureSessionStateRepository(context, namespace)
        try {
            repository.transaction("initial") { it.write("session/peer", "old".encodeToByteArray()) }
            val committed = repository.stateFileForTesting().readBytes()

            assertTrue(
                runCatching {
                    repository.transaction("block-failure") {
                        it.write("session/peer", "partial".encodeToByteArray())
                        error("simulated protocol failure")
                    }
                }.isFailure,
            )
            assertArrayEquals(committed, repository.stateFileForTesting().readBytes())

            val failing = SecureSessionStateRepository.withWriteFailureForTesting(
                context,
                namespace,
            ) { error("simulated fsync/commit boundary failure") }
            assertTrue(
                runCatching {
                    failing.transaction("write-failure") {
                        it.write("session/peer", "new".encodeToByteArray())
                    }
                }.isFailure,
            )
            assertArrayEquals(committed, repository.stateFileForTesting().readBytes())
            val value = SecureSessionStateRepository(context, namespace)
                .transaction("verify") { it.read("session/peer") }
            assertArrayEquals("old".encodeToByteArray(), value)
        } finally {
            repository.deleteForTesting()
        }
    }

    @Test
    fun tamperAndCrossNamespaceSwapFailClosed() {
        val first = SecureSessionStateRepository(context, "first-${UUID.randomUUID()}")
        val second = SecureSessionStateRepository(context, "second-${UUID.randomUUID()}")
        try {
            first.transaction("first") { it.write("account", byteArrayOf(1, 2, 3, 4)) }
            second.transaction("second") { it.write("account", byteArrayOf(5, 6, 7, 8)) }
            val firstBytes = first.stateFileForTesting().readBytes()
            val secondBytes = second.stateFileForTesting().readBytes()

            val tampered = firstBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            first.stateFileForTesting().writeBytes(tampered)
            assertTrue(runCatching { first.transaction("tamper") { it.read("account") } }.isFailure)
            assertArrayEquals(tampered, first.stateFileForTesting().readBytes())

            first.stateFileForTesting().writeBytes(firstBytes)
            second.stateFileForTesting().writeBytes(firstBytes)
            assertTrue(runCatching { second.transaction("swap") { it.read("account") } }.isFailure)

            second.stateFileForTesting().writeBytes(secondBytes)
            assertArrayEquals(
                byteArrayOf(5, 6, 7, 8),
                second.transaction("restored") { it.read("account") },
            )
        } finally {
            first.deleteForTesting()
            second.deleteForTesting()
        }
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        needle.isNotEmpty() && indices.any { start ->
            start + needle.size <= size && needle.indices.all { offset ->
                this[start + offset] == needle[offset]
            }
        }
}
