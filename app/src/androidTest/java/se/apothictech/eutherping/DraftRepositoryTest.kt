package se.apothictech.eutherping

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftRepositoryTest {
    @Test
    fun ordinaryTextAndImageDraftRoundTripAndClear() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "+46700000101"
        DraftRepository.clear(context, address, secure = false)

        DraftRepository.save(
            context,
            address,
            secure = false,
            text = "unfinished caption",
            carrierImageUri = Uri.parse("content://test/image/1"),
        )

        assertEquals("unfinished caption", DraftRepository.load(context, address, false).text)
        assertEquals(
            "content://test/image/1",
            DraftRepository.load(context, address, false).carrierImageUri.toString(),
        )
        assertTrue(DraftRepository.hasDraft(context, address, false))
        DraftRepository.clear(context, address, false)
        assertEquals("", DraftRepository.load(context, address, false).text)
        assertNull(DraftRepository.load(context, address, false).carrierImageUri)
    }

    @Test
    fun vesselDraftRoundTripsThroughKeystoreVault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "+46700000102"
        DraftRepository.clear(context, address, secure = true)

        DraftRepository.save(context, address, secure = true, text = "private unfinished text", null)

        assertEquals("private unfinished text", DraftRepository.load(context, address, true).text)
        assertTrue(DraftRepository.hasDraft(context, address, true))
        DraftRepository.clear(context, address, true)
        assertEquals("", DraftRepository.load(context, address, true).text)
    }

    @Test
    fun vesselDraftContentAndPresenceStayOutOfOrdinaryDraftStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "+46700000103"
        val marker = "VESSEL-PLAINTEXT-MUST-NOT-ENTER-SIGNALS"
        DraftRepository.clear(context, address, secure = true)

        DraftRepository.save(context, address, secure = true, text = marker, null)

        val ordinary = context.getSharedPreferences(
            "eutherping_conversation_drafts",
            Context.MODE_PRIVATE,
        )
        val secureIndex = context.getSharedPreferences(
            "eutherping_secure_draft_index_v1",
            Context.MODE_PRIVATE,
        )
        val secureVault = context.getSharedPreferences(
            "eutherping_secure_vault",
            Context.MODE_PRIVATE,
        )
        val secureId = DraftRepository.draftId(address, secure = true)
        assertFalse(ordinary.contains("$secureId.text"))
        assertFalse(ordinary.contains("$secureId.image"))
        assertFalse(ordinary.contains("$secureId.present"))
        assertFalse(ordinary.all.values.any { it.toString().contains(marker) })
        assertTrue(secureIndex.all.values.contains(true))
        assertFalse(secureIndex.all.values.any { it.toString().contains(marker) })
        assertFalse(secureVault.all.values.any { it.toString().contains(marker) })
        assertEquals(marker, DraftRepository.load(context, address, secure = true).text)

        DraftRepository.clear(context, address, secure = true)
    }

    @Test
    fun legacyVesselPresenceMigratesOutOfOrdinaryDraftStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "+46700000104"
        DraftRepository.clear(context, address, secure = true)
        val id = DraftRepository.draftId(address, secure = true)
        val ordinary = context.getSharedPreferences(
            "eutherping_conversation_drafts",
            Context.MODE_PRIVATE,
        )
        ordinary.edit().putBoolean("$id.present", true).commit()

        assertTrue(DraftRepository.hasDraft(context, address, secure = true))
        assertFalse(ordinary.contains("$id.present"))
        assertTrue(
            context.getSharedPreferences("eutherping_secure_draft_index_v1", Context.MODE_PRIVATE)
                .contains("$id.present"),
        )

        DraftRepository.clear(context, address, secure = true)
    }
}
