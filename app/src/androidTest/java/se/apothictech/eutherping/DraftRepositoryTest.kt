package se.apothictech.eutherping

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
}
