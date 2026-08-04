package se.apothictech.eutherping

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.telephony.PhoneNumberUtils

internal data class ConversationControlState(
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

internal enum class NotificationPrivacy(val storedValue: String) {
    SENDER_AND_PREVIEW("sender_preview"),
    SENDER_ONLY("sender_only"),
    PRIVATE("private"),
}

internal object ConversationControlsRepository {
    private const val PREFERENCES = "conversation_controls_v1"
    private const val PINNED = "pinned"
    private const val ARCHIVED = "archived"
    private const val NOTIFICATION_PRIVACY = "notification_privacy"

    fun state(context: Context, address: String, secure: Boolean): ConversationControlState {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val id = conversationId(address, secure)
        return ConversationControlState(
            pinned = preferences.getStringSet(PINNED, emptySet()).orEmpty().contains(id),
            archived = preferences.getStringSet(ARCHIVED, emptySet()).orEmpty().contains(id),
        )
    }

    fun setPinned(context: Context, address: String, secure: Boolean, pinned: Boolean) {
        updateSet(context, PINNED, conversationId(address, secure), pinned)
    }

    fun setArchived(context: Context, address: String, secure: Boolean, archived: Boolean) {
        updateSet(context, ARCHIVED, conversationId(address, secure), archived)
    }

    fun notificationPrivacy(context: Context): NotificationPrivacy {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(NOTIFICATION_PRIVACY, null)
        return NotificationPrivacy.entries.firstOrNull { it.storedValue == stored }
            ?: NotificationPrivacy.SENDER_AND_PREVIEW
    }

    fun setNotificationPrivacy(context: Context, privacy: NotificationPrivacy) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(NOTIFICATION_PRIVACY, privacy.storedValue)
            .apply()
    }

    fun canBlockNumbers(context: Context): Boolean = runCatching {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    }.getOrDefault(false)

    fun blockedNumbers(context: Context): Set<String> = runCatching {
        if (!canBlockNumbers(context)) return@runCatching emptySet()
        buildSet {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                null,
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                )
                while (cursor.moveToNext()) {
                    normalize(cursor.getString(numberIndex).orEmpty()).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }.getOrDefault(emptySet())

    fun isBlocked(blockedNumbers: Set<String>, address: String): Boolean {
        val normalized = normalize(address)
        return normalized.isNotBlank() && blockedNumbers.any { blocked ->
            blocked == normalized ||
                (blocked.length >= 7 && normalized.length >= 7 && blocked.takeLast(7) == normalized.takeLast(7))
        }
    }

    fun setBlocked(context: Context, address: String, blocked: Boolean): Result<Unit> = runCatching {
        check(canBlockNumbers(context)) { "Android does not allow this user to manage blocked numbers" }
        val normalized = normalize(address)
        require(normalized.isNotBlank()) { "A valid phone number is required" }
        if (blocked) {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, address)
                put(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER, normalized.takeIf { it.startsWith("+") })
            }
            checkNotNull(
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values),
            ) { "Android did not add the number to the block list" }
        } else {
            context.contentResolver.delete(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ? OR " +
                    "${BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER} = ?",
                arrayOf(address, normalized),
            )
        }
    }

    private fun updateSet(context: Context, key: String, id: String, enabled: Boolean) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val values = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (enabled) values.add(id) else values.remove(id)
        preferences.edit().putStringSet(key, values).apply()
    }

    private fun conversationId(address: String, secure: Boolean): String =
        "${if (secure) "secure" else "carrier"}:${normalize(address)}"

    private fun normalize(address: String): String = PhoneNumberUtils.normalizeNumber(address.trim())
}
