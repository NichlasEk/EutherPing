// SPDX-License-Identifier: AGPL-3.0-only

package se.apothictech.eutherping.crypto.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import se.apothictech.eutherping.crypto.ProtocolAddress
import se.apothictech.eutherping.crypto.ProtocolStateRepository
import se.apothictech.eutherping.crypto.ProtocolStateTransaction
import se.apothictech.eutherping.crypto.libsignal.LibsignalProvider

@RunWith(AndroidJUnit4::class)
class LibsignalAndroidSmokeTest {
    @Test
    fun nativeLibraryCreatesPostQuantumPreKeysOnAndroid() {
        val provider = LibsignalProvider()
        val engine = provider.createEngine(
            ProtocolAddress("android-probe"),
            MemoryRepository(),
        )

        val publication = engine.createPreKeyPublication()

        assertEquals(LibsignalProvider.PROVIDER_ID, publication.providerId)
        assertTrue(publication.payload.size > 1_000)
    }
}

private class MemoryRepository : ProtocolStateRepository {
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
