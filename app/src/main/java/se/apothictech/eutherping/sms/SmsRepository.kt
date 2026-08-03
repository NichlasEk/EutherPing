package se.apothictech.eutherping.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import se.apothictech.eutherping.secure.SecureRepository

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

/**
 * The small amount of data the inbox needs from a thread. Keeping this separate from
 * [SmsConversationSnapshot] avoids retaining every message body just to render one row.
 */
data class SmsConversationIndexEntry(
    val threadId: Long,
    val address: String,
    val latestOrdinary: SmsEntry?,
    val latestSecure: SmsEntry?,
    val ordinaryUnread: Int,
    val secureUnread: Int,
    val latestTimestamp: Long,
)

data class SmsMessagePage(
    val messages: List<SmsEntry>,
    val hasOlder: Boolean,
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
        add(Manifest.permission.RECEIVE_MMS)
        add(Manifest.permission.RECEIVE_WAP_PUSH)
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
     * Scans Telephony once but retains only the latest ordinary and secure message per thread.
     * The old snapshot path materialized every message and the UI then decrypted all of them,
     * which made inbox refresh increasingly expensive as the device history grew.
     */
    fun loadConversationIndex(
        context: Context,
        isSecureBody: (String) -> Boolean,
    ): Result<List<SmsConversationIndexEntry>> = runCatching {
        check(hasSmsPermissions(context)) { "SMS permissions are not granted" }
        data class MutableIndex(
            var address: String = "",
            var latestOrdinary: SmsEntry? = null,
            var latestSecure: SmsEntry? = null,
            var ordinaryUnread: Int = 0,
            var secureUnread: Int = 0,
            var latestTimestamp: Long = 0L,
        )

        val threads = linkedMapOf<Long, MutableIndex>()
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
        checkNotNull(
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} ASC",
            ),
        ) { "Android's SMS provider returned no message cursor" }.use { cursor ->
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
                val body = cursor.getString(bodyIndex).orEmpty()
                val type = cursor.getInt(typeIndex)
                val incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                val read = cursor.getInt(readIndex) != 0
                val timestamp = cursor.getLong(dateIndex)
                val entry = SmsEntry(
                    id = cursor.getLong(idIndex),
                    body = body,
                    timestamp = timestamp,
                    incoming = incoming,
                    read = read,
                    status = cursor.getInt(statusIndex),
                )
                val index = threads.getOrPut(threadId, ::MutableIndex)
                val address = cursor.getString(addressIndex).orEmpty()
                if (address.isNotBlank()) index.address = address
                index.latestTimestamp = maxOf(index.latestTimestamp, timestamp)
                if (isSecureBody(body)) {
                    index.latestSecure = entry
                    if (incoming && !read) index.secureUnread++
                } else {
                    index.latestOrdinary = entry
                    if (incoming && !read) index.ordinaryUnread++
                }
            }
        }

        val resolvedMmsAddresses = mutableMapOf<Long, String>()
        loadMmsEntries(
            context,
            threadId = null,
            address = null,
            includeParts = false,
            resolveAddresses = false,
        ).forEach { mms ->
            val threadId = mms.threadId ?: return@forEach
            val index = threads.getOrPut(threadId, ::MutableIndex)
            if (index.address.isBlank()) {
                index.address = resolvedMmsAddresses.getOrPut(threadId) {
                    mmsAddress(context, mms.id, mms.incoming)
                }
            }
            val entry = SmsEntry(
                id = -mms.id - 1,
                body = mms.body.ifBlank { "📷 Carrier MMS" },
                timestamp = mms.timestamp,
                incoming = mms.incoming,
                read = mms.read,
                status = mms.messageBox,
                isMms = true,
            )
            if (index.latestOrdinary == null || mms.timestamp >= index.latestOrdinary!!.timestamp) {
                index.latestOrdinary = entry
            }
            index.latestTimestamp = maxOf(index.latestTimestamp, mms.timestamp)
            if (mms.incoming && !mms.read) index.ordinaryUnread++
        }

        threads.map { (threadId, index) ->
            SmsConversationIndexEntry(
                threadId = threadId,
                address = index.address.ifBlank { "Unknown sender" },
                latestOrdinary = index.latestOrdinary,
                latestSecure = index.latestSecure,
                ordinaryUnread = index.ordinaryUnread,
                secureUnread = index.secureUnread,
                latestTimestamp = index.latestTimestamp,
            )
        }.sortedByDescending(SmsConversationIndexEntry::latestTimestamp)
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
        checkNotNull(
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} ASC",
            ),
        ) { "Android's SMS provider returned no message cursor" }.use { cursor ->
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
        return loadMessagePage(context, threadId, address, Int.MAX_VALUE)
            .getOrDefault(SmsMessagePage(emptyList(), hasOlder = false))
            .messages
    }

    fun loadMessagePage(
        context: Context,
        threadId: Long?,
        address: String,
        limit: Int,
        secureLane: Boolean? = null,
    ): Result<SmsMessagePage> = runCatching {
        check(hasSmsPermissions(context)) { "SMS permissions are not granted" }
        require(limit > 0) { "Message limit must be positive" }
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
        )
        var selection: String
        var arguments: Array<String>
        if (threadId != null && threadId > 0) {
            selection = "${Telephony.Sms.THREAD_ID} = ?"
            arguments = arrayOf(threadId.toString())
        } else {
            selection = "${Telephony.Sms.ADDRESS} = ?"
            arguments = arrayOf(address)
        }
        secureLane?.let { secure ->
            val secureClause = SecureRepository.secureBodyPrefixes.joinToString(" OR ") {
                "${Telephony.Sms.BODY} LIKE ?"
            }
            selection += if (secure) " AND ($secureClause)" else " AND NOT ($secureClause)"
            arguments += SecureRepository.secureBodyPrefixes.map { "$it%" }
        }
        val candidates = mutableListOf<SmsEntry>()
        val queryLimit = (limit.toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        queryNewest(
            context = context,
            uri = Telephony.Sms.CONTENT_URI,
            projection = projection,
            selection = selection,
            arguments = arguments,
            sortColumn = Telephony.Sms.DATE,
            limit = queryLimit,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            while (candidates.size < queryLimit && cursor.moveToNext()) {
                val type = cursor.getInt(typeIndex)
                candidates += SmsEntry(
                    id = cursor.getLong(idIndex),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    timestamp = cursor.getLong(dateIndex),
                    incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    read = cursor.getInt(readIndex) != 0,
                    status = cursor.getInt(statusIndex),
                )
            }
        }
        val smsTruncated = candidates.size > limit
        val mmsEntries = if (secureLane == true) {
            emptyList()
        } else {
            loadMmsEntries(context, threadId, address, limit = queryLimit)
        }
        val mmsTruncated = mmsEntries.size > limit
        mmsEntries.forEach { mms ->
            candidates += SmsEntry(
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
            )
        }
        val newest = candidates.sortedByDescending(SmsEntry::timestamp)
        SmsMessagePage(
            messages = newest.take(limit).sortedBy(SmsEntry::timestamp),
            hasOlder = smsTruncated || mmsTruncated || newest.size > limit,
        )
    }

    private fun queryNewest(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        selection: String,
        arguments: Array<String>,
        sortColumn: String,
        limit: Int,
    ): Cursor? {
        val queryArguments = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arguments)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortColumn))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        runCatching {
            context.contentResolver.query(uri, projection, queryArguments, null)
        }.getOrNull()?.let { return it }
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                arguments,
                "$sortColumn DESC LIMIT $limit",
            )
        }.getOrNull()?.let { return it }
        return context.contentResolver.query(
            uri,
            projection,
            selection,
            arguments,
            "$sortColumn DESC",
        )
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
        resolveAddresses: Boolean = true,
        limit: Int? = null,
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
            val selection = threadId?.let { "${Telephony.Mms.THREAD_ID} = ?" }
            val arguments = threadId?.let { arrayOf(it.toString()) }
            val cursor = if (limit != null && selection != null && arguments != null) {
                queryNewest(
                    context = context,
                    uri = Telephony.Mms.CONTENT_URI,
                    projection = projection,
                    selection = selection,
                    arguments = arguments,
                    sortColumn = Telephony.Mms.DATE,
                    limit = limit,
                )
            } else {
                context.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    projection,
                    selection,
                    arguments,
                    "${Telephony.Mms.DATE} ASC",
                )
            }
            cursor?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                val readIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
                val subjectIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)
                while ((limit == null || size < limit) && cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val box = cursor.getInt(boxIndex)
                    val incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX
                    val mmsAddress = when {
                        !resolveAddresses -> ""
                        threadId != null && address != null -> address
                        else -> mmsAddress(context, id, incoming)
                    }
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

    fun deleteMessage(context: Context, messageId: Long, isMms: Boolean): Result<Unit> = runCatching {
        check(isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        val providerId = if (isMms) -messageId - 1 else messageId
        require(providerId >= 0) { "Invalid message identifier" }
        val uri = if (isMms) {
            Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, providerId.toString())
        } else {
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, providerId.toString())
        }
        check(context.contentResolver.delete(uri, null, null) > 0) {
            "Android did not delete the message"
        }
    }

    fun deleteThread(context: Context, threadId: Long): Result<Int> = runCatching {
        check(isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        require(threadId > 0) { "Invalid conversation identifier" }
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val arguments = arrayOf(threadId.toString())
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, selection, arguments) +
            context.contentResolver.delete(Telephony.Mms.CONTENT_URI, selection, arguments)
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
