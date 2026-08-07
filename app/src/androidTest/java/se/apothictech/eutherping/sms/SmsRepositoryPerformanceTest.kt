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

            val signalPage = SmsRepository.loadMessagePage(
                context,
                threadId,
                address,
                limit = 2,
                secureLane = false,
            ).getOrThrow()
            assertEquals(listOf("old ordinary", "new ordinary"), signalPage.messages.map { it.body })

            val vesselPage = SmsRepository.loadMessagePage(
                context,
                threadId,
                address,
                limit = 1,
                secureLane = true,
            ).getOrThrow()
            assertEquals(listOf("EP1M:not-a-real-capsule"), vesselPage.messages.map { it.body })
        } finally {
            inserted.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    @Test
    fun firstHistoryPageStaysBoundedForLongThreads() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)

        val address = "1555100${System.currentTimeMillis().toString().takeLast(4)}"
        val inserted = (0 until 75).map { index ->
            insert(context, address, "bulk-$index", 10_000L + index)
        }
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
            val page = SmsRepository.loadMessagePage(
                context,
                threadId,
                address,
                limit = 20,
                secureLane = false,
            ).getOrThrow()

            assertTrue(page.hasOlder)
            assertEquals(20, page.messages.size)
            assertEquals("bulk-55", page.messages.first().body)
            assertEquals("bulk-74", page.messages.last().body)
        } finally {
            inserted.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    @Test
    fun contactOpenResolvesFormattedAddressBeforeLoadingBothDirections() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)

        val suffix = System.currentTimeMillis().toString().takeLast(7)
        val storedAddress = "+1 555 ${suffix.take(3)} ${suffix.drop(3)}"
        val selectedAddress = "+1555$suffix"
        val inserted = listOf(
            insert(context, storedAddress, "incoming formatted", 20_000L),
            insert(
                context,
                storedAddress,
                "outgoing formatted",
                21_000L,
                Telephony.Sms.MESSAGE_TYPE_SENT,
            ),
        )
        try {
            val page = SmsRepository.loadMessagePage(
                context = context,
                threadId = null,
                address = selectedAddress,
                limit = 20,
                secureLane = false,
            ).getOrThrow()

            assertTrue(page.resolvedThreadId != null)
            assertEquals(
                listOf("incoming formatted", "outgoing formatted"),
                page.messages.map(SmsEntry::body),
            )
            assertEquals(listOf(true, false), page.messages.map(SmsEntry::incoming))
        } finally {
            inserted.forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    private fun insert(
        context: android.content.Context,
        address: String,
        body: String,
        timestamp: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
    ) = checkNotNull(
        context.contentResolver.insert(
            Telephony.Sms.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, type)
            },
        ),
    )
}
