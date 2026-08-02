package se.apothictech.eutherping.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

data class SmsThread(
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val unread: Int,
    val incoming: Boolean,
)

data class SmsEntry(
    val id: Long,
    val body: String,
    val timestamp: Long,
    val incoming: Boolean,
    val read: Boolean,
    val status: Int,
    val isMms: Boolean = false,
    val mmsAttachment: CarrierMmsAttachment? = null,
)

data class SmsConversationSnapshot(
    val thread: SmsThread,
    val messages: List<SmsEntry>,
)

data class CarrierMmsAttachment(
    val uri: Uri,
    val mimeType: String,
    val name: String,
)

object SmsRepository {
    const val ACTION_SMS_CHANGED = "se.apothictech.eutherping.SMS_CHANGED"
    const val ACTION_SMS_SENT = "se.apothictech.eutherping.SMS_SENT"
    const val ACTION_SMS_DELIVERED = "se.apothictech.eutherping.SMS_DELIVERED"
    const val EXTRA_MESSAGE_URI = "message_uri"

    val requiredPermissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) add("android.permission.POST_NOTIFICATIONS")
    }.toTypedArray()

    fun isDefaultSmsApp(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    fun hasSmsPermissions(context: Context): Boolean = requiredPermissions
        .filterNot { it == "android.permission.POST_NOTIFICATIONS" }
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun loadThreads(context: Context): List<SmsThread> {
        if (!hasSmsPermissions(context)) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
        )
        val byThread = linkedMapOf<Long, SmsThread>()
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIndex)
                    val address = cursor.getString(addressIndex).orEmpty().ifBlank { "Unknown sender" }
                    val unreadDelta = if (cursor.getInt(readIndex) == 0) 1 else 0
                    val existing = byThread[threadId]
                    if (existing == null) {
                        byThread[threadId] = SmsThread(
                            threadId = threadId,
                            address = address,
                            body = cursor.getString(bodyIndex).orEmpty(),
                            timestamp = cursor.getLong(dateIndex),
                            unread = unreadDelta,
                            incoming = cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        )
                    } else if (unreadDelta > 0) {
                        byThread[threadId] = existing.copy(unread = existing.unread + 1)
                    }
                }
            }
            loadMmsEntries(context, threadId = null, address = null).asReversed().forEach { entry ->
                val mmsThreadId = entry.threadId ?: return@forEach
                val address = entry.address.ifBlank { "Unknown sender" }
                val unreadDelta = if (!entry.read) 1 else 0
                val candidate = SmsThread(
                    threadId = mmsThreadId,
                    address = address,
                    body = entry.body.ifBlank {
                        if (entry.attachment != null) "📷 Carrier MMS" else "Carrier MMS"
                    },
                    timestamp = entry.timestamp,
                    unread = unreadDelta,
                    incoming = entry.incoming,
                )
                val existing = byThread[mmsThreadId]
                byThread[mmsThreadId] = if (existing == null) {
                    candidate
                } else {
                    (if (candidate.timestamp > existing.timestamp) candidate else existing)
                        .copy(unread = existing.unread + unreadDelta)
                }
            }
            byThread.values.toList()
                .sortedByDescending(SmsThread::timestamp)
        }.getOrDefault(emptyList())
    }

    /**
     * Loads the conversation index and all SMS/MMS entries in one pass. List screens must use
     * this instead of querying every thread separately; some Telephony providers serialize each
     * query and become extremely slow with a real message history.
     */
    fun loadConversationSnapshot(context: Context): Result<List<SmsConversationSnapshot>> = runCatching {
        check(hasSmsPermissions(context)) { "SMS permissions are not granted" }
        val threads = linkedMapOf<Long, SmsThread>()
        val messages = linkedMapOf<Long, MutableList<SmsEntry>>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadIndex)
                if (threadId <= 0) continue
                val type = cursor.getInt(typeIndex)
                val incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                val read = cursor.getInt(readIndex) != 0
                val address = cursor.getString(addressIndex).orEmpty().ifBlank { "Unknown sender" }
                val body = cursor.getString(bodyIndex).orEmpty()
                val timestamp = cursor.getLong(dateIndex)
                messages.getOrPut(threadId, ::mutableListOf) += SmsEntry(
                    id = cursor.getLong(idIndex),
                    body = body,
                    timestamp = timestamp,
                    incoming = incoming,
                    read = read,
                    status = cursor.getInt(statusIndex),
                )
                val existing = threads[threadId]
                threads[threadId] = SmsThread(
                    threadId = threadId,
                    address = if (address == "Unknown sender") existing?.address ?: address else address,
                    body = body,
                    timestamp = timestamp,
                    unread = (existing?.unread ?: 0) + if (incoming && !read) 1 else 0,
                    incoming = incoming,
                )
            }
        }
        loadMmsEntries(context, threadId = null, address = null, includeParts = false).forEach { mms ->
            val threadId = mms.threadId ?: return@forEach
            val body = mms.body.ifBlank { "📷 Carrier MMS" }
            messages.getOrPut(threadId, ::mutableListOf) += SmsEntry(
                id = -mms.id - 1,
                body = body,
                timestamp = mms.timestamp,
                incoming = mms.incoming,
                read = mms.read,
                status = mms.messageBox,
                isMms = true,
                mmsAttachment = mms.attachment,
            )
            val existing = threads[threadId]
            val address = mms.address.ifBlank { existing?.address ?: "Unknown sender" }
            val candidate = SmsThread(
                threadId = threadId,
                address = address,
                body = body,
                timestamp = mms.timestamp,
                unread = (existing?.unread ?: 0) + if (mms.incoming && !mms.read) 1 else 0,
                incoming = mms.incoming,
            )
            threads[threadId] = if (existing == null || candidate.timestamp >= existing.timestamp) {
                candidate
            } else {
                existing.copy(unread = candidate.unread)
            }
        }
        threads.values.map { thread ->
            SmsConversationSnapshot(
                thread = thread,
                messages = messages[thread.threadId].orEmpty().sortedBy(SmsEntry::timestamp),
            )
        }.sortedByDescending { it.thread.timestamp }
    }

    fun loadMessages(context: Context, threadId: Long?, address: String): List<SmsEntry> {
        if (!hasSmsPermissions(context)) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
        )
        val selection: String
        val arguments: Array<String>
        if (threadId != null && threadId > 0) {
            selection = "${Telephony.Sms.THREAD_ID} = ?"
            arguments = arrayOf(threadId.toString())
        } else {
            selection = "${Telephony.Sms.ADDRESS} = ?"
            arguments = arrayOf(address)
        }
        return runCatching {
            buildList {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    selection,
                    arguments,
                    "${Telephony.Sms.DATE} ASC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                    val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                    val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
                    while (cursor.moveToNext()) {
                        val type = cursor.getInt(typeIndex)
                        add(
                            SmsEntry(
                                id = cursor.getLong(idIndex),
                                body = cursor.getString(bodyIndex).orEmpty(),
                                timestamp = cursor.getLong(dateIndex),
                                incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                                read = cursor.getInt(readIndex) != 0,
                                status = cursor.getInt(statusIndex),
                            ),
                        )
                    }
                }
                loadMmsEntries(context, threadId, address).forEach { mms ->
                    add(
                        SmsEntry(
                            id = -mms.id - 1,
                            body = mms.body.ifBlank {
                                if (mms.attachment != null) "📷 Carrier MMS" else "Carrier MMS"
                            },
                            timestamp = mms.timestamp,
                            incoming = mms.incoming,
                            read = mms.read,
                            status = mms.messageBox,
                            isMms = true,
                            mmsAttachment = mms.attachment,
                        ),
                    )
                }
            }.sortedBy(SmsEntry::timestamp)
        }.getOrDefault(emptyList())
    }

    private data class MmsEntry(
        val id: Long,
        val threadId: Long?,
        val address: String,
        val body: String,
        val timestamp: Long,
        val incoming: Boolean,
        val read: Boolean,
        val messageBox: Int,
        val attachment: CarrierMmsAttachment?,
    )

    private fun loadMmsEntries(
        context: Context,
        threadId: Long?,
        address: String?,
        includeParts: Boolean = true,
    ): List<MmsEntry> = runCatching {
        buildList {
            val projection = arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ,
                Telephony.Mms.SUBJECT,
            )
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                threadId?.let { "${Telephony.Mms.THREAD_ID} = ?" },
                threadId?.let { arrayOf(it.toString()) },
                "${Telephony.Mms.DATE} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                val readIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
                val subjectIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val box = cursor.getInt(boxIndex)
                    val incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX
                    val mmsAddress = mmsAddress(context, id, incoming)
                    if (address != null && !PhoneNumberUtils.compare(address, mmsAddress)) continue
                    val parts = if (includeParts) mmsParts(context, id) else "" to null
                    val subject = cursor.getString(subjectIndex).orEmpty().trim()
                    add(
                        MmsEntry(
                            id = id,
                            threadId = cursor.getLong(threadIndex).takeIf { it > 0 },
                            address = mmsAddress,
                            body = parts.first.ifBlank { subject },
                            timestamp = cursor.getLong(dateIndex) * 1000L,
                            incoming = incoming,
                            read = cursor.getInt(readIndex) != 0,
                            messageBox = box,
                            attachment = parts.second,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun mmsAddress(context: Context, id: Long, incoming: Boolean): String = runCatching {
        val preferredType = if (incoming) 137 else 151
        var fallback = ""
        context.contentResolver.query(
            Uri.parse("content://mms/$id/addr"),
            arrayOf("address", "type"),
            null,
            null,
            null,
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow("address")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                val value = cursor.getString(addressIndex).orEmpty()
                if (value.isBlank() || value == "insert-address-token") continue
                if (fallback.isBlank()) fallback = value
                if (cursor.getInt(typeIndex) == preferredType) return@runCatching value
            }
        }
        fallback
    }.getOrDefault("")

    private fun mmsParts(context: Context, id: Long): Pair<String, CarrierMmsAttachment?> {
        var text = ""
        var attachment: CarrierMmsAttachment? = null
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("_id", "ct", "text", "name", "fn", "cl"),
                "mid = ?",
                arrayOf(id.toString()),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("_id")
                val typeIndex = cursor.getColumnIndexOrThrow("ct")
                val textIndex = cursor.getColumnIndexOrThrow("text")
                val nameIndexes = listOf("name", "fn", "cl").map(cursor::getColumnIndex)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(typeIndex).orEmpty()
                    if (mime == "text/plain") {
                        text = cursor.getString(textIndex).orEmpty()
                    } else if (attachment == null && mime.startsWith("image/")) {
                        val name = nameIndexes.firstNotNullOfOrNull { index ->
                            if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank) else null
                        } ?: "carrier-mms-image"
                        attachment = CarrierMmsAttachment(
                            uri = Uri.parse("content://mms/part/${cursor.getLong(idIndex)}"),
                            mimeType = mime,
                            name = name,
                        )
                    }
                }
            }
        }
        return text to attachment
    }

    fun markThreadRead(context: Context, threadId: Long?) {
        if (threadId == null || !isDefaultSmsApp(context)) return
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
            )
            context.contentResolver.update(
                Telephony.Mms.CONTENT_URI,
                values,
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
            )
        }
    }

    fun persistIncoming(context: Context, address: String, body: String, timestamp: Long): Uri? {
        if (!isDefaultSmsApp(context)) return null
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        return runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.getOrNull()
    }

    fun sendText(context: Context, address: String, body: String): Result<Uri> = runCatching {
        check(isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        check(hasSmsPermissions(context)) { "SMS permissions are not granted" }
        require(address.isNotBlank()) { "A destination number is required" }
        require(body.isNotBlank()) { "The message is empty" }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
        }
        val messageUri = checkNotNull(
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values),
        ) { "Could not persist outgoing SMS" }

        @Suppress("DEPRECATION")
        val smsManager = if (Build.VERSION.SDK_INT >= 31) {
            checkNotNull(context.getSystemService(SmsManager::class.java)) {
                "SMS service is unavailable"
            }
        } else {
            SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(body)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveredIntents = ArrayList<PendingIntent>(parts.size)
        parts.indices.forEach { partIndex ->
            sentIntents += statusIntent(context, ACTION_SMS_SENT, messageUri, partIndex)
            deliveredIntents += statusIntent(context, ACTION_SMS_DELIVERED, messageUri, partIndex)
        }
        if (parts.size == 1) {
            smsManager.sendTextMessage(address, null, body, sentIntents[0], deliveredIntents[0])
        } else {
            smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveredIntents)
        }
        messageUri
    }

    private fun statusIntent(
        context: Context,
        action: String,
        messageUri: Uri,
        partIndex: Int,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MESSAGE_URI, messageUri.toString())
            putExtra("part_index", partIndex)
        }
        return PendingIntent.getBroadcast(
            context,
            31 * messageUri.hashCode() + 17 * action.hashCode() + partIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun updateSentState(context: Context, messageUri: Uri, resultCode: Int) {
        if (!isDefaultSmsApp(context)) return
        val values = ContentValues().apply {
            put(
                Telephony.Sms.TYPE,
                if (resultCode == Activity.RESULT_OK) {
                    Telephony.Sms.MESSAGE_TYPE_SENT
                } else {
                    Telephony.Sms.MESSAGE_TYPE_FAILED
                },
            )
            put(
                Telephony.Sms.STATUS,
                if (resultCode == Activity.RESULT_OK) Telephony.Sms.STATUS_NONE else Telephony.Sms.STATUS_FAILED,
            )
        }
        runCatching { context.contentResolver.update(messageUri, values, null, null) }
    }
}
