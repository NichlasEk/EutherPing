// SPDX-License-Identifier: AGPL-3.0-only

package se.apothictech.eutherping.crypto.libsignal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.DuplicateMessageException
import se.apothictech.eutherping.crypto.ProtocolAddress
import se.apothictech.eutherping.crypto.ProtocolCiphertext
import se.apothictech.eutherping.crypto.ProtocolCiphertextKind
import se.apothictech.eutherping.crypto.ProtocolStateRepository
import se.apothictech.eutherping.crypto.ProtocolStateTransaction

class LibsignalProviderTest {
    @Test
    fun aliceAndBobRatchetBidirectionallyOutOfOrderAndAfterReload() {
        val provider = LibsignalProvider()
        val aliceState = CopyOnWriteMemoryRepository()
        val bobState = CopyOnWriteMemoryRepository()
        val aliceAddress = ProtocolAddress("alice")
        val bobAddress = ProtocolAddress("bob")
        var alice = provider.createEngine(aliceAddress, aliceState)
        var bob = provider.createEngine(bobAddress, bobState)

        val bobPreKeys = bob.createPreKeyPublication()
        assertEquals(LibsignalProvider.PROVIDER_ID, bobPreKeys.providerId)
        assertTrue("PQXDH publication size must be measured", bobPreKeys.payload.size > 1_000)
        alice.establishOutboundSession(bobAddress, bobPreKeys)

        val initial = alice.encrypt(bobAddress, "first".encodeToByteArray())
        assertEquals(ProtocolCiphertextKind.PRE_KEY, initial.kind)
        assertArrayEquals("first".encodeToByteArray(), bob.decrypt(aliceAddress, initial))

        val reply = bob.encrypt(aliceAddress, "reply".encodeToByteArray())
        assertEquals(ProtocolCiphertextKind.SESSION, reply.kind)
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

        val duplicate = runCatching { bob.decrypt(aliceAddress, second) }.exceptionOrNull()
        assertTrue(duplicate is DuplicateMessageException)
        println(
            "libsignal-probe version=${LibsignalProvider.LIBSIGNAL_VERSION} " +
                "prekeys=${bobPreKeys.payload.size} initial=${initial.payload.size} " +
                "session=${reply.payload.size} aliceState=${aliceState.committedBytes()} " +
                "bobState=${bobState.committedBytes()}",
        )
        assertTrue(aliceState.committedRecordCount() > 0)
        assertTrue(bobState.committedRecordCount() > 0)
        assertFalse(provider.descriptor.productionReady)
    }

    @Test
    fun rejectsProviderMismatchBeforeParsingCiphertext() {
        val provider = LibsignalProvider()
        val engine = provider.createEngine(ProtocolAddress("alice"), CopyOnWriteMemoryRepository())

        val error = runCatching {
            engine.decrypt(
                ProtocolAddress("bob"),
                ProtocolCiphertext("another-provider", ProtocolCiphertextKind.SESSION, byteArrayOf(1)),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun failedTransactionDoesNotCommitPartialState() {
        val repository = CopyOnWriteMemoryRepository()

        val error = runCatching {
            repository.transaction("rollback") { transaction ->
                transaction.write("must-not-survive", byteArrayOf(1, 2, 3))
                error("simulated storage failure")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, repository.committedRecordCount())
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
    fun committedRecordCount(): Int = records.size

    @Synchronized
    fun committedBytes(): Int = records.values.sumOf(ByteArray::size)
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
