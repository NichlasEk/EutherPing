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
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import se.apothictech.eutherping.secure.SecureRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val box: Int,
    val isMms: Boolean = false,
    val mmsAttachment: CarrierMmsAttachment? = null,
    val subscriptionId: Int? = null,
)

enum class MessageDeliveryState(val label: String) {
    SENDING("SENDING"),
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    FAILED("FAILED"),
}

fun SmsEntry.deliveryState(): MessageDeliveryState? {
    if (incoming) return null
    return if (isMms) {
        when (box) {
            Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageDeliveryState.SENDING
            Telephony.Mms.MESSAGE_BOX_FAILED -> MessageDeliveryState.FAILED
            Telephony.Mms.MESSAGE_BOX_SENT -> MessageDeliveryState.SENT
            else -> MessageDeliveryState.SENDING
        }
    } else {
        when {
            box == Telephony.Sms.MESSAGE_TYPE_FAILED || status == Telephony.Sms.STATUS_FAILED ->
                MessageDeliveryState.FAILED
            box == Telephony.Sms.MESSAGE_TYPE_OUTBOX || status == Telephony.Sms.STATUS_PENDING ->
                MessageDeliveryState.SENDING
            status == Telephony.Sms.STATUS_COMPLETE -> MessageDeliveryState.DELIVERED
            else -> MessageDeliveryState.SENT
        }
    }
}

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
    val participants: List<String> = listOf(address),
)

data class SmsMessagePage(
    val messages: List<SmsEntry>,
    val hasOlder: Boolean,
    val resolvedThreadId: Long? = null,
)

data class SmsSearchHit(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val text: String,
    val timestamp: Long,
    val incoming: Boolean,
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
    const val EXTRA_PART_INDEX = "part_index"
    const val EXTRA_PART_COUNT = "part_count"
    private const val STATUS_PREFERENCES = "sms_send_status"

    val requiredPermissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.RECEIVE_MMS)
        add(Manifest.permission.RECEIVE_WAP_PUSH)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= 33) add("android.permission.POST_NOTIFICATIONS")
    }.toTypedArray()

    val carrierIdentityPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
    )

    fun isDefaultSmsApp(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    fun smsPartCount(context: Context, body: String): Int {
        if (body.isEmpty()) return 0
        return smsManager(context).divideMessage(body).size
    }

    fun hasSmsPermissions(context: Context): Boolean = requiredPermissions
        .filterNot {
            it == "android.permission.POST_NOTIFICATIONS" ||
                it == Manifest.permission.READ_PHONE_STATE ||
                it == Manifest.permission.READ_PHONE_NUMBERS
        }
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun hasCarrierIdentityPermissions(context: Context): Boolean = carrierIdentityPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

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
            var participants: List<String> = emptyList(),
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
            Telephony.Sms.SUBSCRIPTION_ID,
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
            val subscriptionIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
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
                    box = type,
                    subscriptionId = cursor.getInt(subscriptionIndex).takeIf { !cursor.isNull(subscriptionIndex) && it >= 0 },
                )
                val index = threads.getOrPut(threadId, ::MutableIndex)
                val address = cursor.getString(addressIndex).orEmpty()
                if (address.isNotBlank()) index.address = address
                if (address.isNotBlank() && index.participants.isEmpty()) index.participants = listOf(address)
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

        val resolvedMmsParticipants = mutableMapOf<Long, List<String>>()
        loadMmsEntries(
            context,
            threadId = null,
            address = null,
            includeParts = false,
            resolveAddresses = false,
        ).forEach { mms ->
            val threadId = mms.threadId ?: return@forEach
            val index = threads.getOrPut(threadId, ::MutableIndex)
            val participants = resolvedMmsParticipants.getOrPut(threadId) {
                mmsParticipants(context, mms.id, mms.incoming, mms.subscriptionId)
            }
            if (participants.isNotEmpty()) {
                index.participants = participants
                index.address = participants.joinToString(", ")
            } else if (index.address.isBlank()) {
                index.address = mmsAddress(context, mms.id, mms.incoming)
            }
            val entry = SmsEntry(
                id = -mms.id - 1,
                body = mms.body.ifBlank { "📷 Carrier MMS" },
                timestamp = mms.timestamp,
                incoming = mms.incoming,
                read = mms.read,
                status = mms.messageBox,
                box = mms.messageBox,
                isMms = true,
                subscriptionId = mms.subscriptionId,
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
                participants = index.participants.ifEmpty { listOf(index.address).filter(String::isNotBlank) },
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
            Telephony.Sms.SUBSCRIPTION_ID,
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
            val subscriptionIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
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
                    box = type,
                    subscriptionId = cursor.getInt(subscriptionIndex).takeIf { !cursor.isNull(subscriptionIndex) && it >= 0 },
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
                box = mms.messageBox,
                isMms = true,
                mmsAttachment = mms.attachment,
                subscriptionId = mms.subscriptionId,
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

    fun loadMmsAttachment(context: Context, messageId: Long): CarrierMmsAttachment? {
        val providerId = -messageId - 1
        if (providerId < 0) return null
        return mmsParts(context, providerId).second
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
        val effectiveThreadId = resolveConversationThreadId(context, threadId, address)
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
        if (effectiveThreadId != null) {
            selection = "${Telephony.Sms.THREAD_ID} = ?"
            arguments = arrayOf(effectiveThreadId.toString())
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
                    box = type,
                )
            }
        }
        val smsTruncated = candidates.size > limit
        val mmsEntries = if (secureLane == true) {
            emptyList()
        } else {
            loadMmsEntries(context, effectiveThreadId, address, limit = queryLimit)
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
                box = mms.messageBox,
                isMms = true,
                mmsAttachment = mms.attachment,
            )
        }
        val newest = candidates.sortedByDescending(SmsEntry::timestamp)
        SmsMessagePage(
            messages = newest.take(limit).sortedBy(SmsEntry::timestamp),
            hasOlder = smsTruncated || mmsTruncated || newest.size > limit,
            resolvedThreadId = effectiveThreadId,
        )
    }

    internal fun resolveConversationThreadId(
        context: Context,
        threadId: Long?,
        address: String,
    ): Long? {
        threadId?.takeIf { it > 0L }?.let { return it }
        val trimmedAddress = address.trim()
        if (trimmedAddress.isBlank()) return null

        fun queryThreadId(selection: String, arguments: Array<String>): Long? = runCatching {
            queryNewest(
                context = context,
                uri = Telephony.Sms.CONTENT_URI,
                projection = arrayOf(Telephony.Sms.THREAD_ID),
                selection = selection,
                arguments = arguments,
                sortColumn = Telephony.Sms.DATE,
                limit = 1,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0L } else null
            }
        }.getOrNull()

        queryThreadId(
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(trimmedAddress),
        )?.let { return it }

        val normalizedAddress = PhoneNumberUtils.normalizeNumber(trimmedAddress)
        if (normalizedAddress.length >= 7 && ',' !in trimmedAddress && ';' !in trimmedAddress) {
            queryThreadId(
                "PHONE_NUMBERS_EQUAL(${Telephony.Sms.ADDRESS}, ?, 0)",
                arrayOf(trimmedAddress),
            )?.let { return it }

            runCatching {
                queryNewest(
                    context = context,
                    uri = Telephony.Sms.CONTENT_URI,
                    projection = arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS),
                    selection = "1 = 1",
                    arguments = emptyArray(),
                    sortColumn = Telephony.Sms.DATE,
                    limit = 300,
                )?.use { cursor ->
                    val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                    val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    while (cursor.moveToNext()) {
                        val candidate = cursor.getString(addressIndex).orEmpty()
                        if (PhoneNumberUtils.compare(trimmedAddress, candidate)) {
                            return cursor.getLong(threadIndex).takeIf { it > 0L }
                        }
                    }
                }
            }
        }

        return loadMmsEntries(
            context = context,
            threadId = null,
            address = trimmedAddress,
            includeParts = false,
            limit = 50,
        ).firstNotNullOfOrNull(MmsEntry::threadId)
    }

    fun searchMessages(
        context: Context,
        query: String,
        secureLane: Boolean,
        matchingAddresses: Set<String> = emptySet(),
        limit: Int = 50,
    ): Result<List<SmsSearchHit>> = runCatching {
        check(hasSmsPermissions(context)) { "SMS permissions are not granted" }
        val needle = query.trim()
        if (needle.isBlank()) return@runCatching emptyList()
        val secureClause = secureBodyPrefixesClause()
        val selection: String
        val arguments: Array<String>
        val queryLimit: Int
        if (secureLane) {
            selection = "($secureClause)"
            arguments = SecureRepository.secureBodyPrefixes.map { "$it%" }.toTypedArray()
            queryLimit = maxOf(limit * 6, 240)
        } else {
            selection = "${Telephony.Sms.BODY} LIKE ? AND NOT ($secureClause)"
            arguments = arrayOf("%$needle%") + SecureRepository.secureBodyPrefixes.map { "$it%" }
            queryLimit = limit
        }
        val hits = linkedMapOf<String, SmsSearchHit>()
        fun addHit(hit: SmsSearchHit, key: String = "sms:${hit.messageId}") {
            if (hits.size < limit) hits.putIfAbsent(key, hit)
        }
        queryNewest(
            context,
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
            ),
            selection,
            arguments,
            Telephony.Sms.DATE,
            queryLimit,
        )?.use { cursor ->
            while (cursor.moveToNext() && hits.size < limit) {
                val address = cursor.getString(2).orEmpty()
                val body = cursor.getString(3).orEmpty()
                val incoming = cursor.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX
                val display = if (secureLane) {
                    SecureRepository.decodeForDisplay(context, address, body, incoming)?.text ?: continue
                } else {
                    body
                }
                if (!display.contains(needle, ignoreCase = true)) continue
                addHit(SmsSearchHit(
                    messageId = cursor.getLong(0),
                    threadId = cursor.getLong(1),
                    address = address,
                    text = display,
                    timestamp = cursor.getLong(4),
                    incoming = incoming,
                ))
            }
        }
        val normalizedNeedle = PhoneNumberUtils.normalizeNumber(needle)
        val normalizedAddresses = matchingAddresses.mapTo(hashSetOf(), PhoneNumberUtils::normalizeNumber)
        if (hits.size < limit && (!secureLane || normalizedAddresses.isNotEmpty() || normalizedNeedle.isNotBlank())) {
            queryNewest(
                context,
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                ),
                "1 = 1",
                emptyArray(),
                Telephony.Sms.DATE,
                maxOf(limit * 6, 240),
            )?.use { cursor ->
                while (cursor.moveToNext() && hits.size < limit) {
                    val address = cursor.getString(2).orEmpty()
                    val body = cursor.getString(3).orEmpty()
                    val incoming = cursor.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    val display = if (secureLane) {
                        if (!SecureRepository.isSecureBody(body)) continue
                        SecureRepository.decodeForDisplay(context, address, body, incoming)?.text ?: continue
                    } else {
                        if (SecureRepository.isSecureBody(body)) continue
                        body
                    }
                    val timestamp = cursor.getLong(4)
                    if (!searchMetadataMatches(
                            address,
                            timestamp,
                            needle,
                            normalizedNeedle,
                            normalizedAddresses,
                        ) && !display.contains(needle, ignoreCase = true)
                    ) continue
                    addHit(
                        SmsSearchHit(
                            messageId = cursor.getLong(0),
                            threadId = cursor.getLong(1),
                            address = address,
                            text = display,
                            timestamp = timestamp,
                            incoming = incoming,
                        ),
                    )
                }
            }
        }
        if (!secureLane && hits.size < limit) {
            loadMmsEntries(
                context = context,
                threadId = null,
                address = null,
                limit = maxOf(limit * 4, 160),
            ).forEach { mms ->
                if (hits.size >= limit) return@forEach
                val thread = mms.threadId ?: return@forEach
                if (!mms.body.contains(needle, ignoreCase = true) &&
                    !searchMetadataMatches(
                        mms.address,
                        mms.timestamp,
                        needle,
                        normalizedNeedle,
                        normalizedAddresses,
                    )
                ) return@forEach
                addHit(
                    SmsSearchHit(
                        messageId = -mms.id - 1,
                        threadId = thread,
                        address = mms.address,
                        text = mms.body.ifBlank {
                            if (mms.attachment != null) "📷 Carrier MMS" else "Carrier MMS"
                        },
                        timestamp = mms.timestamp,
                        incoming = mms.incoming,
                    ),
                    key = "mms:${mms.id}",
                )
            }
        }
        hits.values.sortedByDescending(SmsSearchHit::timestamp).take(limit)
    }

    private fun searchMetadataMatches(
        address: String,
        timestamp: Long,
        needle: String,
        normalizedNeedle: String,
        matchingAddresses: Set<String>,
    ): Boolean {
        val normalizedAddress = PhoneNumberUtils.normalizeNumber(address)
        val addressMatches = normalizedNeedle.isNotBlank() && normalizedAddress.contains(normalizedNeedle)
        val contactMatches = matchingAddresses.any { candidate ->
            candidate == normalizedAddress ||
                (candidate.length >= 7 && normalizedAddress.length >= 7 &&
                    candidate.takeLast(7) == normalizedAddress.takeLast(7))
        }
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val dateMatches = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.getDefault()),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()),
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()),
        ).any { formatter -> formatter.format(date).contains(needle, ignoreCase = true) }
        return addressMatches || contactMatches || dateMatches
    }

    private fun secureBodyPrefixesClause(): String =
        SecureRepository.secureBodyPrefixes.joinToString(" OR ") { "${Telephony.Sms.BODY} LIKE ?" }

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
        val subscriptionId: Int?,
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
                Telephony.Mms.SUBSCRIPTION_ID,
            )
            val selection = threadId?.let { "${Telephony.Mms.THREAD_ID} = ?" }
            val arguments = threadId?.let { arrayOf(it.toString()) }
            val cursor = if (limit != null) {
                queryNewest(
                    context = context,
                    uri = Telephony.Mms.CONTENT_URI,
                    projection = projection,
                    selection = selection ?: "1 = 1",
                    arguments = arguments ?: emptyArray(),
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
                val subscriptionIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBSCRIPTION_ID)
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
                            subscriptionId = cursor.getInt(subscriptionIndex)
                                .takeIf { !cursor.isNull(subscriptionIndex) && it >= 0 },
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

    internal fun mmsParticipants(
        context: Context,
        id: Long,
        incoming: Boolean,
        subscriptionId: Int?,
    ): List<String> = runCatching {
        val ownNumbers = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            context.getSystemService(SubscriptionManager::class.java)
                .activeSubscriptionInfoList.orEmpty()
                .filter { subscriptionId == null || subscriptionId < 0 || it.subscriptionId == subscriptionId }
                .mapNotNull { it.number?.takeIf(String::isNotBlank) }
        } else {
            emptyList()
        }
        val values = mutableListOf<String>()
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
                val type = cursor.getInt(typeIndex)
                if (type != 151 && (!incoming || type != 137)) continue
                val value = cursor.getString(addressIndex).orEmpty().trim()
                if (value.isBlank() || value == "insert-address-token") continue
                if (incoming && ownNumbers.any { PhoneNumberUtils.compare(it, value) }) continue
                if (values.none { PhoneNumberUtils.compare(it, value) }) values += value
            }
        }
        values
    }.getOrDefault(emptyList())

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

    fun markThreadRead(context: Context, threadId: Long?): Boolean {
        if (threadId == null || !isDefaultSmsApp(context)) return false
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return runCatching {
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
            true
        }.getOrDefault(false)
    }

    fun markThreadUnread(context: Context, threadId: Long?, secureLane: Boolean): Boolean {
        if (threadId == null || !isDefaultSmsApp(context)) return false
        data class Candidate(val uri: Uri, val timestamp: Long)

        var smsCandidate: Candidate? = null
        queryNewest(
            context = context,
            uri = Telephony.Sms.CONTENT_URI,
            projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
            selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
            arguments = arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            sortColumn = Telephony.Sms.DATE,
            limit = 120,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val body = cursor.getString(1).orEmpty()
                if (SecureRepository.isSecureBody(body) != secureLane) continue
                smsCandidate = Candidate(
                    Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, cursor.getLong(0).toString()),
                    cursor.getLong(2),
                )
                break
            }
        }

        var mmsCandidate: Candidate? = null
        if (!secureLane) {
            queryNewest(
                context = context,
                uri = Telephony.Mms.CONTENT_URI,
                projection = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                selection = "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.MESSAGE_BOX} = ?",
                arguments = arrayOf(threadId.toString(), Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
                sortColumn = Telephony.Mms.DATE,
                limit = 1,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mmsCandidate = Candidate(
                        Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, cursor.getLong(0).toString()),
                        cursor.getLong(1) * 1000L,
                    )
                }
            }
        }
        val candidate = listOfNotNull(smsCandidate, mmsCandidate).maxByOrNull(Candidate::timestamp)
            ?: return false
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        return runCatching {
            context.contentResolver.update(candidate.uri, values, null, null) > 0
        }.getOrDefault(false)
    }

    fun threadIdForMessage(context: Context, messageUri: Uri): Long? = runCatching {
        context.contentResolver.query(
            messageUri,
            arrayOf(Telephony.Sms.THREAD_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0L } else null
        }
    }.getOrNull()

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

    fun sendText(
        context: Context,
        address: String,
        body: String,
        subscriptionId: Int? = null,
    ): Result<Uri> = runCatching {
        check(isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        check(hasSmsPermissions(context)) { "SMS permissions are not granted" }
        require(address.isNotBlank()) { "A destination number is required" }
        require(body.isNotBlank()) { "The message is empty" }

        val selectedSubscriptionId = subscriptionId ?: CarrierSubscriptionRepository.selected(
            context,
            CarrierSubscriptionRepository.conversationKey(null, listOf(address)),
        )
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            selectedSubscriptionId?.takeIf { it >= 0 }?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }
        val messageUri = checkNotNull(
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values),
        ) { "Could not persist outgoing SMS" }

        transmitText(context, address, body, messageUri, selectedSubscriptionId)
        messageUri
    }

    fun retryFailedText(context: Context, messageId: Long): Result<Unit> = runCatching {
        check(isDefaultSmsApp(context)) { "EutherPing is not the default SMS app" }
        require(messageId >= 0) { "Invalid SMS identifier" }
        val messageUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
        val stored = checkNotNull(
            context.contentResolver.query(
                messageUri,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.SUBSCRIPTION_ID,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) null else StoredFailedSms(
                    cursor.getString(0).orEmpty(),
                    cursor.getString(1).orEmpty(),
                    cursor.getInt(2),
                    cursor.getInt(3).takeIf { !cursor.isNull(3) && it >= 0 },
                )
            },
        ) { "The failed SMS no longer exists" }
        check(stored.box == Telephony.Sms.MESSAGE_TYPE_FAILED) { "Only a failed SMS can be retried" }
        updateSmsProviderState(
            context,
            messageUri,
            Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            Telephony.Sms.STATUS_PENDING,
        )
        clearFailure(context, messageUri)
        transmitText(context, stored.address, stored.body, messageUri, stored.subscriptionId)
    }

    private data class StoredFailedSms(
        val address: String,
        val body: String,
        val box: Int,
        val subscriptionId: Int?,
    )

    private fun transmitText(
        context: Context,
        address: String,
        body: String,
        messageUri: Uri,
        subscriptionId: Int?,
    ) {
        val smsManager = smsManager(context, subscriptionId)
        val parts = smsManager.divideMessage(body)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveredIntents = ArrayList<PendingIntent>(parts.size)
        parts.indices.forEach { partIndex ->
            sentIntents += statusIntent(context, ACTION_SMS_SENT, messageUri, partIndex, parts.size)
            deliveredIntents += statusIntent(context, ACTION_SMS_DELIVERED, messageUri, partIndex, parts.size)
        }
        try {
            if (parts.size == 1) {
                smsManager.sendTextMessage(address, null, body, sentIntents[0], deliveredIntents[0])
            } else {
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveredIntents)
            }
        } catch (error: Throwable) {
            updateSmsProviderState(
                context,
                messageUri,
                Telephony.Sms.MESSAGE_TYPE_FAILED,
                Telephony.Sms.STATUS_FAILED,
            )
            throw error
        }
    }

    fun failureDescription(context: Context, messageId: Long, isMms: Boolean): String? {
        val uri = if (isMms) {
            Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, (-messageId - 1).toString())
        } else {
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
        }
        val code = context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
            .getInt("failure:${uri}", Int.MIN_VALUE)
        val httpStatus = context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
            .getInt("http:${uri}", 0)
        if (isMms && httpStatus > 0) {
            return "The carrier's MMS server returned HTTP $httpStatus. Check mobile data, APN/MMSC settings, and the selected SIM before retrying."
        }
        if (code == Int.MIN_VALUE) return if (isMms) {
            "The carrier or Android MMS service rejected the send. Mobile data, APN settings, or the selected SIM may be unavailable."
        } else {
            "Android could not complete this SMS. Check mobile service and try again."
        }
        return when (code) {
            SmsManager.RESULT_ERROR_RADIO_OFF -> "The mobile radio was turned off."
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile service was available."
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Android's SMS sending limit was exceeded."
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "The SIM's fixed-dialing rules blocked this number."
            SmsManager.RESULT_ERROR_NULL_PDU -> "Android could not create the carrier message."
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "The carrier rejected the message or returned an unknown error."
            Activity.RESULT_CANCELED -> "The carrier send was cancelled before completion."
            else -> "Android reported carrier error $code. Check mobile data, service, SIM, and APN settings."
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subscriptionId: Int? = null): SmsManager {
        val base = if (Build.VERSION.SDK_INT >= 31) {
            checkNotNull(context.getSystemService(SmsManager::class.java)) {
            "SMS service is unavailable"
            }
        } else {
            SmsManager.getDefault()
        }
        val id = subscriptionId?.takeIf { it >= 0 } ?: return base
        return if (Build.VERSION.SDK_INT >= 31) {
            base.createForSubscriptionId(id)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(id)
        }
    }

    private fun statusIntent(
        context: Context,
        action: String,
        messageUri: Uri,
        partIndex: Int,
        partCount: Int,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MESSAGE_URI, messageUri.toString())
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_PART_COUNT, partCount)
        }
        return PendingIntent.getBroadcast(
            context,
            31 * messageUri.hashCode() + 17 * action.hashCode() + partIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun updateSentPartState(
        context: Context,
        messageUri: Uri,
        resultCode: Int,
        partIndex: Int,
        partCount: Int,
    ) {
        if (!isDefaultSmsApp(context)) return
        if (resultCode != Activity.RESULT_OK) {
            rememberFailure(context, messageUri, resultCode)
            clearPartProgress(context, "sent", messageUri)
            updateSmsProviderState(
                context,
                messageUri,
                Telephony.Sms.MESSAGE_TYPE_FAILED,
                Telephony.Sms.STATUS_FAILED,
            )
        } else if (recordSuccessfulPart(context, "sent", messageUri, partIndex, partCount)) {
            updateSmsProviderState(
                context,
                messageUri,
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_NONE,
            )
        }
    }

    fun updateDeliveredPartState(
        context: Context,
        messageUri: Uri,
        resultCode: Int,
        partIndex: Int,
        partCount: Int,
    ) {
        if (!isDefaultSmsApp(context) || resultCode != Activity.RESULT_OK) return
        if (recordSuccessfulPart(context, "delivered", messageUri, partIndex, partCount)) {
            updateSmsProviderState(
                context,
                messageUri,
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_COMPLETE,
            )
        }
    }

    private fun updateSmsProviderState(context: Context, messageUri: Uri, box: Int, status: Int) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, box)
            put(Telephony.Sms.STATUS, status)
        }
        runCatching { context.contentResolver.update(messageUri, values, null, null) }
        context.sendBroadcast(Intent(ACTION_SMS_CHANGED).setPackage(context.packageName))
    }

    @Synchronized
    private fun recordSuccessfulPart(
        context: Context,
        stage: String,
        messageUri: Uri,
        partIndex: Int,
        partCount: Int,
    ): Boolean {
        val expected = partCount.coerceAtLeast(1)
        val key = "$stage:${messageUri}"
        val preferences = context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
        val completed = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        completed += partIndex.coerceAtLeast(0).toString()
        return if (completed.size >= expected) {
            preferences.edit().remove(key).apply()
            true
        } else {
            preferences.edit().putStringSet(key, completed).apply()
            false
        }
    }

    private fun clearPartProgress(context: Context, stage: String, messageUri: Uri) {
        context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove("$stage:${messageUri}")
            .apply()
    }

    private fun rememberFailure(context: Context, messageUri: Uri, resultCode: Int) {
        context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt("failure:${messageUri}", resultCode)
            .apply()
    }

    fun rememberCarrierFailure(context: Context, rawUri: String, resultCode: Int, httpStatus: Int) {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return
        rememberFailure(context, uri, resultCode)
        if (httpStatus > 0) {
            context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt("http:${uri}", httpStatus)
                .apply()
        }
    }

    fun clearCarrierFailure(context: Context, messageUri: Uri) = clearFailure(context, messageUri)

    private fun clearFailure(context: Context, messageUri: Uri) {
        context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove("failure:${messageUri}")
            .remove("http:${messageUri}")
            .apply()
    }
}
