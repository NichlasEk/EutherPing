package se.apothictech.eutherping.secure

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.apothictech.eutherping.crypto.ProtocolAddress
import se.apothictech.eutherping.crypto.ProtocolStateRepository
import se.apothictech.eutherping.crypto.ProtocolStateTransaction
import se.apothictech.eutherping.crypto.vodozemac.VodozemacProvider

class VodozemacPeerResetTest {
    @Test
    fun `verified reset deletes only the selected peer state`() {
        val state = MemoryRepository()
        val selected = ProtocolAddress("selected-peer")
        val retained = ProtocolAddress("retained-peer")
        val selectedStorageId = storageId(selected)
        val retainedStorageId = storageId(retained)
        state.transaction("fixture") { records ->
            records.write("account", byteArrayOf(1))
            records.write("session/$selectedStorageId", byteArrayOf(2))
            records.write("identity/$selectedStorageId", byteArrayOf(3))
            records.write("session/$retainedStorageId", byteArrayOf(4))
            records.write("identity/$retainedStorageId", byteArrayOf(5))
        }

        VodozemacProvider.deletePeerStateForVerifiedReset(state, selected)

        val keys = state.keys()
        assertTrue("account" in keys)
        assertFalse("session/$selectedStorageId" in keys)
        assertFalse("identity/$selectedStorageId" in keys)
        assertTrue("session/$retainedStorageId" in keys)
        assertTrue("identity/$retainedStorageId" in keys)
    }

    private fun storageId(address: ProtocolAddress): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(address.stableId.encodeToByteArray())
}

private class MemoryRepository : ProtocolStateRepository {
    private val records = linkedMapOf<String, ByteArray>()

    override fun <T> transaction(
        lockId: String,
        block: (ProtocolStateTransaction) -> T,
    ): T = block(MemoryTransaction(records))

    fun keys(): Set<String> = records.keys
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
