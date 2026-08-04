package se.apothictech.eutherping.sms

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationIndexCacheTest {
    @Test
    fun restoresOrdinaryIndexWithoutPersistingSecurePlaintext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ConversationIndexCache.clear(context)
        val signal = cached("SMS", "ordinary preview")
        val vessel = cached("SECURE", "secret plaintext must not survive")
        ConversationIndexCache.save(
            context,
            CachedConversationIndex(listOf(signal), listOf(vessel), updatedAt = 1234L),
        ).getOrThrow()

        val restored = ConversationIndexCache.load(context)
        assertNotNull(restored)
        assertEquals("ordinary preview", restored!!.signals.single().preview)
        assertEquals(listOf("0700000000", "0700000001"), restored.signals.single().recipients)
        assertFalse(restored.vessels.single().preview.contains("secret plaintext"))
        assertEquals(1234L, restored.updatedAt)
        ConversationIndexCache.clear(context)
    }

    private fun cached(lane: String, preview: String) = CachedConversation(
        id = 1,
        name = "0700000000",
        initials = "70",
        preview = preview,
        time = "12:34",
        lane = lane,
        unread = 2,
        distance = "ANDROID TELEPHONY",
        smsAddress = "0700000000",
        threadId = 7L,
        recipients = listOf("0700000000", "0700000001"),
    )
}
