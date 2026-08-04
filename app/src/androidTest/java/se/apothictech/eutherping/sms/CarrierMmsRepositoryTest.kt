package se.apothictech.eutherping.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import se.apothictech.eutherping.ConversationControlsRepository
import se.apothictech.eutherping.NotificationPrivacy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CarrierMmsRepositoryTest {
    @Test
    fun notificationMarkAsReadActionUpdatesAndroidHistory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        assertTrue("Test app did not become the default SMS handler", SmsRepository.isDefaultSmsApp(context))

        val address = "15559876543"
        val persistedMessageUri = SmsRepository.persistIncoming(
            context,
            address,
            "Mark this notification as read",
            System.currentTimeMillis(),
        )
        assertNotNull("Incoming test SMS was not persisted", persistedMessageUri)
        val messageUri = persistedMessageUri!!
        val threadId = SmsRepository.threadIdForMessage(context, messageUri)
        assertNotNull("Persisted SMS did not expose a thread id", threadId)

        IncomingMessageNotifier.show(
            context = context,
            address = address,
            body = "Mark this notification as read",
            secureLane = false,
            threadId = threadId,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications
            .firstOrNull { it.id == address.hashCode() }
            ?.notification
        assertNotNull("Incoming message notification was not posted", notification)
        val markReadAction = notification!!.actions
            ?.firstOrNull { it.title.toString() == "MARK AS READ" }
        assertNotNull("Notification did not expose MARK AS READ", markReadAction)

        markReadAction!!.actionIntent.send()
        val markedRead = repeatUntil(timeoutMillis = 2_000L) {
            context.contentResolver.query(
                messageUri,
                arrayOf("read", "seen"),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) == 1 && cursor.getInt(1) == 1
            } == true
        }
        assertTrue("SMS was not marked read and seen", markedRead)

        context.contentResolver.query(
            messageUri,
            arrayOf("read", "seen"),
            null,
            null,
            null,
        )?.use { cursor ->
            assertTrue("Marked SMS disappeared from Android history", cursor.moveToFirst())
            assertEquals("SMS was not marked read", 1, cursor.getInt(0))
            assertEquals("SMS was not marked seen", 1, cursor.getInt(1))
        }
        assertTrue("Notification remained visible after MARK AS READ", repeatUntil(2_000L) {
            manager.activeNotifications.none { it.id == address.hashCode() }
        })
        context.contentResolver.delete(messageUri, null, null)
    }

    private fun repeatUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test
    fun marksOnlyLatestCarrierMessageUnreadAgain() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        val uri = requireNotNull(
            SmsRepository.persistIncoming(context, "15551230009", "Unread round trip", System.currentTimeMillis()),
        )
        val threadId = requireNotNull(SmsRepository.threadIdForMessage(context, uri))
        assertTrue(SmsRepository.markThreadRead(context, threadId))
        assertTrue(SmsRepository.markThreadUnread(context, threadId, secureLane = false))
        context.contentResolver.query(uri, arrayOf("read", "seen"), null, null, null)?.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }
        context.contentResolver.delete(uri, null, null)
    }

    @Test
    fun postsCarrierMmsNotification() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ConversationControlsRepository.setNotificationPrivacy(context, NotificationPrivacy.SENDER_AND_PREVIEW)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()

        IncomingMessageNotifier.show(
            context,
            "+46701234567",
            "Carrier MMS received",
            secureLane = false,
            displayName = "Ada Lovelace",
        )
        assertTrue("Carrier MMS notification was not posted", repeatUntil(2_000L) {
            manager.activeNotifications.any { it.id == "+46701234567".hashCode() }
        })
        val notification = manager.activeNotifications
            .firstOrNull { it.id == "+46701234567".hashCode() }
            ?.notification
        assertNotNull("Carrier MMS notification was not posted", notification)
        assertTrue(
            "Carrier MMS notification text was incorrect",
            notification!!.extras.getCharSequence(Notification.EXTRA_TEXT) == "Carrier MMS received",
        )
        assertTrue(
            "Carrier MMS notification did not prefer the contact name",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE) == "Ada Lovelace",
        )
        val reply = notification.actions?.firstOrNull { it.title.toString() == "REPLY" }
        assertNotNull("Ordinary carrier notification did not expose REPLY", reply)
        assertTrue(
            "REPLY action did not carry Android RemoteInput",
            reply!!.remoteInputs?.any { it.resultKey == NotificationReplyReceiver.REMOTE_INPUT_KEY } == true,
        )
        manager.cancel("+46701234567".hashCode())
    }

    @Test
    fun notificationPrivacyCanHidePreviewOrSender() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val address = "+46701112233"

        ConversationControlsRepository.setNotificationPrivacy(context, NotificationPrivacy.SENDER_ONLY)
        IncomingMessageNotifier.show(context, address, "secret ordinary body", false, displayName = "Ada")
        var notification = manager.activeNotifications.first { it.id == address.hashCode() }.notification
        assertEquals("Ada", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("New message", notification.extras.getCharSequence(Notification.EXTRA_TEXT))

        ConversationControlsRepository.setNotificationPrivacy(context, NotificationPrivacy.PRIVATE)
        IncomingMessageNotifier.show(context, address, "secret ordinary body", false, displayName = "Ada")
        assertTrue("Private notification did not replace the previous preview", repeatUntil(2_000L) {
            manager.activeNotifications
                .firstOrNull { it.id == address.hashCode() }
                ?.notification
                ?.extras
                ?.getCharSequence(Notification.EXTRA_TITLE) == "New message"
        })
        notification = manager.activeNotifications.first { it.id == address.hashCode() }.notification
        assertEquals("New message", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Open EutherPing to read", notification.extras.getCharSequence(Notification.EXTRA_TEXT))

        manager.cancel(address.hashCode())
        ConversationControlsRepository.setNotificationPrivacy(context, NotificationPrivacy.SENDER_AND_PREVIEW)
    }

    @Test
    fun secureNotificationNeverAcceptsLockScreenPlaintextReply() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val address = "+46707654321"

        IncomingMessageNotifier.show(
            context,
            address,
            "Encrypted Secure Ping",
            secureLane = true,
            displayName = "Verified Vessel",
        )
        assertTrue("Secure notification was not posted", repeatUntil(2_000L) {
            manager.activeNotifications.any { it.id == address.hashCode() }
        })
        val notification = manager.activeNotifications
            .firstOrNull { it.id == address.hashCode() }
            ?.notification
        assertNotNull("Secure notification was not posted", notification)
        assertFalse(
            "Secure notification must not expose plaintext RemoteInput",
            notification!!.actions?.any { it.title.toString() == "REPLY" } == true,
        )
        manager.cancel(address.hashCode())
    }

    @Test
    fun downloadedReceiverDoesNotUseLegacyMmsConfigPath() {
        assertFalse(
            "Incoming MMS must not use the legacy receiver that crashes on Samsung Android 16",
            com.klinker.android.send_message.MmsReceivedReceiver::class.java
                .isAssignableFrom(MmsDownloadedReceiver::class.java),
        )
    }

    @Test
    fun readsSamsungStyleSubscriptionIdExtra() {
        val intent = Intent().putExtra("subscriptionId", 42)

        assertTrue(
            "Samsung-style subscriptionId extra was not recognized",
            with(CarrierMmsRepository) { intent.subscriptionId() } == 42,
        )
    }

    @Test
    fun composesAndPersistsCarrierImageBeforeSystemTransport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        assertTrue("Test app did not become the default SMS handler", SmsRepository.isDefaultSmsApp(context))
        assertTrue("SMS role did not grant required permissions", SmsRepository.hasSmsPermissions(context))
        assertTrue(
            "SMS role did not grant RECEIVE_MMS",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_MMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
        assertTrue(
            "SMS role did not grant RECEIVE_WAP_PUSH",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_WAP_PUSH) ==
                PackageManager.PERMISSION_GRANTED,
        )
        val directory = File(context.cacheDir, "mms_transport").apply { mkdirs() }
        val source = File(directory, "instrumentation-source.jpg")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).let { bitmap ->
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        val sourceUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.securefiles",
            source,
        )
        val draftPreview = CarrierMmsRepository.loadSourcePreview(context, sourceUri).getOrThrow()
        assertTrue("MMS draft preview had no pixels", draftPreview.width > 0 && draftPreview.height > 0)
        assertTrue(
            "Repeated MMS draft previews did not use the memory cache",
            draftPreview === CarrierMmsRepository.loadSourcePreview(context, sourceUri).getOrThrow(),
        )

        val result = CarrierMmsRepository.sendImage(
            context,
            "15551234567",
            "EutherPing carrier MMS instrumentation",
            sourceUri,
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val messageUri = result.getOrThrow()
        context.contentResolver.query(messageUri, arrayOf("_id", "msg_box"), null, null, null)?.use {
            assertTrue(it.moveToFirst())
            assertNotNull(it.getString(0))
        }
        assertTrue(
            "Bounded global search did not find the carrier MMS caption",
            SmsRepository.searchMessages(context, "carrier MMS instrumentation", secureLane = false)
                .getOrThrow()
                .any { it.messageId < 0 && it.text.contains("instrumentation") },
        )
        val snapshot = SmsRepository.loadConversationSnapshot(context).getOrThrow()
        val mmsConversation = snapshot.firstOrNull { conversation ->
            conversation.messages.any(SmsEntry::isMms)
        }
        assertNotNull(
            "One-pass history did not include the persisted carrier MMS",
            mmsConversation,
        )
        val mmsEntry = SmsRepository.loadMessages(
            context,
            mmsConversation!!.thread.threadId,
            mmsConversation.thread.address,
        ).firstOrNull(SmsEntry::isMms)
        val attachment = mmsEntry?.mmsAttachment
        assertNotNull("Conversation detail did not expose the carrier image", attachment)
        assertNotNull(
            "Targeted MMS part lookup did not expose the carrier image",
            mmsEntry?.let { SmsRepository.loadMmsAttachment(context, it.id) },
        )
        val preview = CarrierMmsRepository.loadPreview(context, attachment!!).getOrThrow()
        assertTrue("Carrier preview had no pixels", preview.width > 0 && preview.height > 0)
        val viewUri = CarrierMmsRepository.prepareStoredImage(context, attachment).getOrThrow()
        assertTrue(
            "Temporary MMS view copy was empty",
            context.contentResolver.openInputStream(viewUri)?.use { it.read() >= 0 } == true,
        )
        val savedFile = File(context.cacheDir, "mms_view/instrumentation-saved.jpg")
        val savedUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.securefiles",
            savedFile,
        )
        CarrierMmsRepository.saveStoredImage(context, attachment, savedUri).getOrThrow()
        assertTrue("Saved MMS image was empty", savedFile.length() > 0L)

        val longText = "Automatic text MMS ".repeat(18).trim()
        assertTrue(
            "Long carrier text did not exceed one SMS segment",
            SmsRepository.smsPartCount(context, longText) > 1,
        )
        assertTrue(
            "Short carrier text unexpectedly exceeded one SMS segment",
            SmsRepository.smsPartCount(context, "Short SMS") == 1,
        )
        val textMmsUri = CarrierMmsRepository.sendText(
            context,
            "15551234567",
            longText,
        ).getOrThrow()
        context.contentResolver.query(textMmsUri, arrayOf("_id", "msg_box"), null, null, null)?.use {
            assertTrue("Text MMS was not persisted in Android's provider", it.moveToFirst())
        }
        val textMms = SmsRepository.loadMessages(
            context,
            mmsConversation.thread.threadId,
            mmsConversation.thread.address,
        ).firstOrNull { it.isMms && it.body == longText }
        assertNotNull("Conversation history did not expose the automatic text MMS", textMms)
        assertTrue("Text-only MMS unexpectedly exposed an image", textMms?.mmsAttachment == null)

        val groupRecipients = listOf("15551234567", "15557654321")
        val groupMmsUri = CarrierMmsRepository.sendText(
            context,
            groupRecipients,
            "Short group reply-all instrumentation",
        ).getOrThrow()
        val groupProviderId = requireNotNull(groupMmsUri.lastPathSegment?.toLongOrNull())
        val storedParticipants = SmsRepository.mmsParticipants(
            context,
            groupProviderId,
            incoming = false,
            subscriptionId = null,
        )
        assertEquals(groupRecipients.toSet(), storedParticipants.toSet())
        val groupThreadId = requireNotNull(SmsRepository.threadIdForMessage(context, groupMmsUri))
        val groupIndex = SmsRepository.loadConversationIndex(context) { false }.getOrThrow()
            .firstOrNull { it.threadId == groupThreadId }
        assertNotNull("Group MMS did not retain a participant-based Android thread", groupIndex)
        assertEquals(groupRecipients.toSet(), groupIndex?.participants?.toSet())
    }

    @Test
    fun boundedSearchMatchesBodyNumberContactAndDate() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        val address = "+46705550123"
        val uri = requireNotNull(
            SmsRepository.persistIncoming(context, address, "Searchable narwhal phrase", System.currentTimeMillis()),
        )
        try {
            assertTrue(
                SmsRepository.searchMessages(context, "narwhal", false).getOrThrow()
                    .any { it.address == address },
            )
            assertTrue(
                SmsRepository.searchMessages(context, "5550123", false).getOrThrow()
                    .any { it.address == address },
            )
            assertTrue(
                SmsRepository.searchMessages(
                    context,
                    "Ada Search Contact",
                    false,
                    matchingAddresses = setOf(address),
                ).getOrThrow().any { it.address == address },
            )
            val today = java.time.LocalDate.now().toString()
            assertTrue(
                SmsRepository.searchMessages(context, today, false).getOrThrow()
                    .any { it.address == address },
            )
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun scalesNoisyImageToCarrierPayloadLimit() {
        val size = 1_400
        var noise = 0x13579BDF
        val pixels = IntArray(size * size) {
            noise = noise * 1_103_515_245 + 12_345
            0xff000000.toInt() or (noise and 0x00ffffff)
        }
        val source = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        val limit = 96 * 1024
        val encoded = try {
            CarrierMmsRepository.encodeBitmapForMms(source, limit)
        } finally {
            source.recycle()
        }

        assertTrue("Encoded MMS image exceeded the carrier limit", encoded.size <= limit)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        assertTrue("Encoded MMS image was not decodable", bounds.outWidth > 0 && bounds.outHeight > 0)
        assertTrue("Oversized noisy image was not spatially reduced", bounds.outWidth < size)
    }
}
