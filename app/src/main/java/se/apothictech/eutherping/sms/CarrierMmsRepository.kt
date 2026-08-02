package se.apothictech.eutherping.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.SendReq
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object CarrierMmsRepository {
    const val ACTION_MMS_SENT = "se.apothictech.eutherping.MMS_SENT"
    const val EXTRA_MMS_URI = "mms_uri"
    private const val MMS_CACHE = "mms_transport"
    private const val MMS_VIEW_CACHE = "mms_view"
    private const val MAX_SOURCE_BYTES = 25L * 1024 * 1024

    fun loadPreview(context: Context, attachment: CarrierMmsAttachment): Result<Bitmap> = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        checkNotNull(context.contentResolver.openInputStream(attachment.uri)).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "MMS image could not be decoded" }
        var sample = 1
        while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
        checkNotNull(context.contentResolver.openInputStream(attachment.uri)).use {
            checkNotNull(
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }),
            ) { "MMS image could not be decoded" }
        }
    }

    fun prepareStoredImage(context: Context, attachment: CarrierMmsAttachment): Result<Uri> = runCatching {
        val directory = File(context.cacheDir, MMS_VIEW_CACHE).apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val safeName = attachment.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "carrier-mms-image" }
        val copy = File(directory, safeName)
        checkNotNull(context.contentResolver.openInputStream(attachment.uri)) {
            "MMS image is no longer available from Android"
        }.use { input -> copy.outputStream().use(input::copyTo) }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.securefiles",
            copy,
        )
    }

    fun openStoredImage(context: Context, attachment: CarrierMmsAttachment): Result<Unit> = runCatching {
        val contentUri = prepareStoredImage(context, attachment).getOrThrow()
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, attachment.mimeType)
                clipData = android.content.ClipData.newRawUri("EutherPing carrier MMS", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun sendImage(
        context: Context,
        address: String,
        caption: String,
        source: Uri,
    ): Result<Uri> = runCatching {
        check(SmsRepository.isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        check(SmsRepository.hasSmsPermissions(context)) { "SMS permissions are not granted" }
        require(address.isNotBlank()) { "A destination number is required" }
        require(context.contentResolver.getType(source)?.startsWith("image/") == true) {
            "Carrier MMS currently supports images"
        }

        val manager = smsManager(context)
        val image = encodeCarrierImage(context, source, carrierPayloadLimit(manager))
        val request = SendReq().apply {
            setTo(arrayOf(EncodedStringValue(address)))
            setDate(System.currentTimeMillis() / 1000L)
            setDeliveryReport(PduHeaders.VALUE_NO)
            setReadReport(PduHeaders.VALUE_NO)
            setPriority(PduHeaders.PRIORITY_NORMAL)
            setExpiry(7L * 24 * 60 * 60)
            setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
            setBody(PduBody().apply {
                if (caption.isNotBlank()) addPart(textPart(caption.trim()))
                addPart(imagePart(image))
            })
            setMessageSize(image.bytes.size.toLong() + caption.toByteArray().size)
        }
        val messageUri = PduPersister.getPduPersister(context).persist(
            request,
            Telephony.Mms.Outbox.CONTENT_URI,
            true,
            false,
            null,
            manager.subscriptionId,
        )
        val pdu = checkNotNull(PduComposer(context, request).make()) { "Could not compose carrier MMS" }
        val directory = File(context.cacheDir, MMS_CACHE).apply { mkdirs() }
        val pduFile = File(directory, "send-${UUID.randomUUID()}.pdu").apply { writeBytes(pdu) }
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.securefiles",
            pduFile,
        )
        val statusIntent = Intent(context, MmsStatusReceiver::class.java).apply {
            action = ACTION_MMS_SENT
            putExtra(EXTRA_MMS_URI, messageUri.toString())
            putExtra("pdu_path", pduFile.absolutePath)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            messageUri.hashCode(),
            statusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.sendMultimediaMessage(context, contentUri, null, null, pending)
        messageUri
    }

    fun receiveNotification(context: Context, intent: Intent): Result<Unit> = runCatching {
        check(SmsRepository.isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        val raw = checkNotNull(intent.getByteArrayExtra("data")) { "MMS notification had no PDU" }
        val notification = PduParser(raw).parse() as? NotificationInd
            ?: error("Unsupported MMS push type")
        val manager = smsManager(context, intent.subscriptionId())
        var location = String(checkNotNull(notification.contentLocation), Charsets.ISO_8859_1)
        if (manager.carrierConfigValues?.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID) == true &&
            location.endsWith("=")
        ) {
            location += String(checkNotNull(notification.transactionId), Charsets.ISO_8859_1)
        }
        val directory = File(context.cacheDir, MMS_CACHE).apply { mkdirs() }
        val destination = File(directory, "receive-${UUID.randomUUID()}.pdu")
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.securefiles",
            destination,
        )
        val resultIntent = Intent(context, MmsDownloadedReceiver::class.java).apply {
            putExtra(com.klinker.android.send_message.MmsReceivedReceiver.EXTRA_FILE_PATH, destination.absolutePath)
            putExtra(com.klinker.android.send_message.MmsReceivedReceiver.EXTRA_LOCATION_URL, location)
            putExtra(com.klinker.android.send_message.MmsReceivedReceiver.SUBSCRIPTION_ID, manager.subscriptionId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            location.hashCode(),
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.downloadMultimediaMessage(context, location, contentUri, null, pending)
    }

    fun updateSentState(context: Context, rawUri: String, resultCode: Int) {
        if (!SmsRepository.isDefaultSmsApp(context)) return
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return
        val values = ContentValues().apply {
            put(
                Telephony.Mms.MESSAGE_BOX,
                if (resultCode == Activity.RESULT_OK) {
                    Telephony.Mms.MESSAGE_BOX_SENT
                } else {
                    Telephony.Mms.MESSAGE_BOX_FAILED
                },
            )
        }
        runCatching { context.contentResolver.update(uri, values, null, null) }
        context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
    }

    private data class EncodedImage(val bytes: ByteArray, val name: String)

    private fun encodeCarrierImage(context: Context, uri: Uri, limit: Int): EncodedImage {
        val declaredSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        require(declaredSize == null || declaredSize < 0 || declaredSize <= MAX_SOURCE_BYTES) {
            "The selected image is larger than 25 MB"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image could not be decoded" }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = checkNotNull(
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            },
        ) { "The selected image could not be decoded" }
        try {
            var quality = 90
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    output.toByteArray()
                }
                quality -= 10
            } while (bytes.size > limit && quality >= 40)
            require(bytes.size <= limit) { "The image is too large for this carrier's MMS limit" }
            return EncodedImage(bytes, "eutherping-${System.currentTimeMillis()}.jpg")
        } finally {
            bitmap.recycle()
        }
    }

    private fun textPart(text: String) = PduPart().apply {
        setContentType("text/plain".toByteArray())
        setContentLocation("text.txt".toByteArray())
        setContentId("text".toByteArray())
        setCharset(106)
        setData(text.toByteArray(Charsets.UTF_8))
    }

    private fun imagePart(image: EncodedImage) = PduPart().apply {
        setContentType("image/jpeg".toByteArray())
        setContentLocation(image.name.toByteArray())
        setContentId("image".toByteArray())
        setName(image.name.toByteArray())
        setFilename(image.name.toByteArray())
        setData(image.bytes)
    }

    private fun carrierPayloadLimit(manager: SmsManager): Int {
        val configured = manager.carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 614_400)
            ?: 614_400
        return (configured - 32_768).coerceIn(128 * 1024, 1_500_000)
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subscriptionId: Int? = null): SmsManager {
        val base = if (Build.VERSION.SDK_INT >= 31) {
            checkNotNull(context.getSystemService(SmsManager::class.java)) { "SMS service is unavailable" }
        } else {
            SmsManager.getDefault()
        }
        val id = subscriptionId ?: SmsManager.getDefaultSmsSubscriptionId()
        return when {
            id < 0 -> base
            Build.VERSION.SDK_INT >= 31 -> base.createForSubscriptionId(id)
            else -> SmsManager.getSmsManagerForSubscriptionId(id)
        }
    }

    private fun Intent.subscriptionId(): Int? {
        val candidates = listOf(
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subscription",
            "sub_id",
        )
        return candidates.firstNotNullOfOrNull { key ->
            getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        }
    }
}
