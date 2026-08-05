// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.vodozemac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import se.apothictech.eutherping.crypto.ProtocolAddress
import se.apothictech.eutherping.crypto.ProtocolCiphertext
import se.apothictech.eutherping.crypto.ProtocolCiphertextKind
import se.apothictech.eutherping.crypto.ProtocolStateRepository
import se.apothictech.eutherping.crypto.ProtocolStateTransaction
import se.apothictech.eutherping.crypto.storage.SecureSessionStateRepository

@RunWith(AndroidJUnit4::class)
class VodozemacProviderTest {
    @Test
    fun realKeystoreRepositoriesPersistTwoIdentitiesAcrossReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val aliceNamespace = "real-alice-$suffix"
        val bobNamespace = "real-bob-$suffix"
        val aliceState = SecureSessionStateRepository(context, aliceNamespace)
        val bobState = SecureSessionStateRepository(context, bobNamespace)
        val provider = VodozemacProvider()
        val aliceAddress = ProtocolAddress("alice")
        val bobAddress = ProtocolAddress("bob")
        val marker = "real-persistent-message-$suffix".encodeToByteArray()
        try {
            var alice = provider.createEngine(aliceAddress, aliceState)
            var bob = provider.createEngine(bobAddress, bobState)
            val bobPublication = bob.createPreKeyPublication()
            alice.establishOutboundSession(bobAddress, bobPublication)
            val initial = alice.encrypt(bobAddress, marker)
            assertArrayEquals(marker, bob.decrypt(aliceAddress, initial))

            alice = provider.createEngine(
                aliceAddress,
                SecureSessionStateRepository(context, aliceNamespace),
            )
            bob = provider.createEngine(
                bobAddress,
                SecureSessionStateRepository(context, bobNamespace),
            )
            val reply = bob.encrypt(aliceAddress, "after-real-reload".encodeToByteArray())
            assertArrayEquals(
                "after-real-reload".encodeToByteArray(),
                alice.decrypt(bobAddress, reply),
            )

            val storageDirectory = File(context.noBackupFilesDir, "secure_sessions_v3")
            assertTrue(storageDirectory.isDirectory)
            assertFalse(
                storageDirectory.listFiles().orEmpty().any { file ->
                    file.isFile && file.readBytes().contains(marker)
                },
            )
        } finally {
            marker.fill(0)
            aliceState.deleteAllStateForVerifiedReset()
            bobState.deleteAllStateForVerifiedReset()
        }
    }

    @Test
    fun providerRatchetsOutOfOrderRejectsReplayAndSurvivesReload() {
        val provider = VodozemacProvider()
        val aliceState = CopyOnWriteMemoryRepository()
        val bobState = CopyOnWriteMemoryRepository()
        val aliceAddress = ProtocolAddress("alice")
        val bobAddress = ProtocolAddress("bob")
        var alice = provider.createEngine(aliceAddress, aliceState)
        var bob = provider.createEngine(bobAddress, bobState)

        val bobPublication = bob.createPreKeyPublication()
        assertEquals(VodozemacProvider.PROVIDER_ID, bobPublication.providerId)
        assertEquals(164, bobPublication.payload.size)
        alice.establishOutboundSession(bobAddress, bobPublication)
        val afterEstablish = aliceState.snapshot()
        assertTrue(
            runCatching {
                alice.establishOutboundSession(bobAddress, bobPublication)
            }.isFailure,
        )
        assertTrue(aliceState.matches(afterEstablish))

        val initial = alice.encrypt(bobAddress, "first".encodeToByteArray())
        assertEquals(ProtocolCiphertextKind.PRE_KEY, initial.kind)
        assertTrue(initial.payload.size <= 300)
        assertArrayEquals("first".encodeToByteArray(), bob.decrypt(aliceAddress, initial))

        val reply = bob.encrypt(aliceAddress, "reply".encodeToByteArray())
        assertEquals(ProtocolCiphertextKind.SESSION, reply.kind)
        assertTrue(reply.payload.size <= 100)
        assertArrayEquals("reply".encodeToByteArray(), alice.decrypt(bobAddress, reply))

        val second = alice.encrypt(bobAddress, "second".encodeToByteArray())
        val third = alice.encrypt(bobAddress, "third".encodeToByteArray())
        assertArrayEquals("third".encodeToByteArray(), bob.decrypt(aliceAddress, third))
        assertArrayEquals("second".encodeToByteArray(), bob.decrypt(aliceAddress, second))

        alice = provider.createEngine(aliceAddress, aliceState)
        bob = provider.createEngine(bobAddress, bobState)
        val afterReload = bob.encrypt(aliceAddress, "after reload".encodeToByteArray())
        assertArrayEquals(
            "after reload".encodeToByteArray(),
            alice.decrypt(bobAddress, afterReload),
        )

        val beforeReplay = bobState.snapshot()
        assertTrue(runCatching { bob.decrypt(aliceAddress, second) }.isFailure)
        assertTrue(bobState.matches(beforeReplay))
        assertFalse(provider.descriptor.productionReady)
        assertFalse(provider.descriptor.postQuantumHandshake)
    }

    @Test
    fun malformedSignatureIdentityChangeAndProviderMismatchRollBack() {
        val provider = VodozemacProvider()
        val bobState = CopyOnWriteMemoryRepository()
        val bobAddress = ProtocolAddress("bob")
        val aliceAddress = ProtocolAddress("alice")
        val bob = provider.createEngine(bobAddress, bobState)
        val bobPublication = bob.createPreKeyPublication()

        val alice = provider.createEngine(aliceAddress, CopyOnWriteMemoryRepository())
        alice.establishOutboundSession(bobAddress, bobPublication)
        val validInitial = alice.encrypt(bobAddress, "first".encodeToByteArray())
        val tampered = validInitial.payload.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        val beforeTamper = bobState.snapshot()
        assertTrue(
            runCatching {
                bob.decrypt(
                    aliceAddress,
                    ProtocolCiphertext(
                        VodozemacProvider.PROVIDER_ID,
                        ProtocolCiphertextKind.PRE_KEY,
                        tampered,
                    ),
                )
            }.isFailure,
        )
        assertTrue(bobState.matches(beforeTamper))

        assertArrayEquals("first".encodeToByteArray(), bob.decrypt(aliceAddress, validInitial))
        val newBobPublication = bob.createPreKeyPublication()
        val mallory = provider.createEngine(
            ProtocolAddress("mallory"),
            CopyOnWriteMemoryRepository(),
        )
        mallory.establishOutboundSession(bobAddress, newBobPublication)
        val identityReplacement = mallory.encrypt(bobAddress, "replacement".encodeToByteArray())
        val beforeReplacement = bobState.snapshot()
        assertTrue(runCatching { bob.decrypt(aliceAddress, identityReplacement) }.isFailure)
        assertTrue(bobState.matches(beforeReplacement))

        val mismatch = validInitial.copy(providerId = "another-provider")
        assertTrue(runCatching { bob.decrypt(aliceAddress, mismatch) }.isFailure)
    }
}

private class CopyOnWriteMemoryRepository : ProtocolStateRepository {
    private val records = linkedMapOf<String, ByteArray>()

    @Synchronized
    override fun <T> transaction(
        lockId: String,
        block: (ProtocolStateTransaction) -> T,
    ): T {
        val working = records.mapValuesTo(linkedMapOf()) { (_, value) -> value.copyOf() }
        val result = block(MemoryTransaction(working))
        records.clear()
        records.putAll(working.mapValues { (_, value) -> value.copyOf() })
        return result
    }

    @Synchronized
    fun snapshot(): Map<String, ByteArray> = records.mapValues { (_, value) -> value.copyOf() }

    @Synchronized
    fun matches(snapshot: Map<String, ByteArray>): Boolean =
        records.keys == snapshot.keys && records.all { (key, value) ->
            snapshot[key]?.contentEquals(value) == true
        }
}

private class MemoryTransaction(
    private val records: MutableMap<String, ByteArray>,
) : ProtocolStateTransaction {
    override fun read(key: String): ByteArray? = records[key]?.copyOf()

    override fun write(key: String, value: ByteArray) {
        records[key] = value.copyOf()
    }

    override fun delete(key: String) {
        records.remove(key)
    }

    override fun keys(prefix: String): Set<String> = records.keys.filterTo(linkedSetOf()) {
        it.startsWith(prefix)
    }
}

private fun ByteArray.contains(needle: ByteArray): Boolean =
    needle.isNotEmpty() && indices.any { start ->
        start + needle.size <= size && needle.indices.all { offset ->
            this[start + offset] == needle[offset]
        }
    }
