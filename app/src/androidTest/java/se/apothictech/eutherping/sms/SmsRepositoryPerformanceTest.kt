package se.apothictech.eutherping.sms

import android.content.ContentValues
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import se.apothictech.eutherping.secure.SecureRepository

@RunWith(AndroidJUnit4::class)
class SmsRepositoryPerformanceTest {
    @Test
    fun inboxIndexKeepsOnlyLatestPerLaneAndHistoryIsPaged() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        assertTrue(SmsRepository.isDefaultSmsApp(context))

        val address = "1555000${System.currentTimeMillis().toString().takeLast(4)}"
        val inserted = listOf(
            insert(context, address, "old ordinary", 1_000L),
            insert(context, address, "EP1M:not-a-real-capsule", 2_000L),
            insert(context, address, "new ordinary", 3_000L),
        )
        try {
            val threadId = checkNotNull(
                context.contentResolver.query(
                    inserted.last(),
                    arrayOf(Telephony.Sms.THREAD_ID),
                    null,
                    null,
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null },
            )
            val index = SmsRepository.loadConversationIndex(
                context,
                SecureRepository::isSecureBody,
            ).getOrThrow().first { it.threadId == threadId }
            assertEquals("new ordinary", index.latestOrdinary?.body)
            assertEquals("EP1M:not-a-real-capsule", index.latestSecure?.body)

            val page = SmsRepository.loadMessagePage(context, threadId, address, limit = 2).getOrThrow()
            assertTrue(page.hasOlder)
            assertEquals(listOf("EP1M:not-a-real-capsule", "new ordinary"), page.messages.map { it.body })
        } finally {
            inserted.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    private fun insert(
        context: android.content.Context,
        address: String,
        body: String,
        timestamp: Long,
    ) = checkNotNull(
        context.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            },
        ),
    )
}
