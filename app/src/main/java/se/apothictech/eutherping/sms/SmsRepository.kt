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
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

data class SmsThread(
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val unread: Int,
)

data class SmsEntry(
    val id: Long,
    val body: String,
    val timestamp: Long,
    val incoming: Boolean,
    val status: Int,
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
                        )
                    } else if (unreadDelta > 0) {
                        byThread[threadId] = existing.copy(unread = existing.unread + 1)
                    }
                }
            }
            byThread.values.toList()
        }.getOrDefault(emptyList())
    }

    fun loadMessages(context: Context, threadId: Long?, address: String): List<SmsEntry> {
        if (!hasSmsPermissions(context)) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
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
                    val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
                    while (cursor.moveToNext()) {
                        val type = cursor.getInt(typeIndex)
                        add(
                            SmsEntry(
                                id = cursor.getLong(idIndex),
                                body = cursor.getString(bodyIndex).orEmpty(),
                                timestamp = cursor.getLong(dateIndex),
                                incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                                status = cursor.getInt(statusIndex),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
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
