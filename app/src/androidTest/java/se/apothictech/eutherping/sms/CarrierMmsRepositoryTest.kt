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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CarrierMmsRepositoryTest {
    @Test
    fun postsCarrierMmsNotification() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()

        IncomingMessageNotifier.show(
            context,
            "+46701234567",
            "Carrier MMS received",
            secureLane = false,
            displayName = "Ada Lovelace",
        )

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
        manager.cancel("+46701234567".hashCode())
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
