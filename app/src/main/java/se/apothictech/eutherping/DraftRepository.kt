package se.apothictech.eutherping

import android.content.Context
import android.net.Uri
import android.util.Base64
import se.apothictech.eutherping.secure.SecureRepository
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

internal data class StoredConversationDraft(
    val text: String = "",
    val carrierImageUri: Uri? = null,
)

internal object DraftRepository {
    private const val ORDINARY_PREFERENCES = "eutherping_conversation_drafts"
    private const val SECURE_INDEX_PREFERENCES = "eutherping_secure_draft_index_v1"
    private const val TEXT_SUFFIX = ".text"
    private const val IMAGE_SUFFIX = ".image"
    private const val PRESENT_SUFFIX = ".present"

    fun load(context: Context, address: String, secure: Boolean): StoredConversationDraft {
        val id = draftId(address, secure)
        val text = if (secure) {
            migrateLegacySecureMetadata(context, id)
            SecureRepository.loadEncryptedDraft(context, id).orEmpty()
        } else {
            val preferences = context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
            preferences.getString(id + TEXT_SUFFIX, null).orEmpty()
        }
        val image = if (secure) {
            null
        } else {
            context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
                .getString(id + IMAGE_SUFFIX, null)
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        }
        return StoredConversationDraft(text, image)
    }

    fun save(
        context: Context,
        address: String,
        secure: Boolean,
        text: String,
        carrierImageUri: Uri?,
    ) {
        val id = draftId(address, secure)
        if (secure) {
            SecureRepository.saveEncryptedDraft(context, id, text)
            val indexStored = context.getSharedPreferences(SECURE_INDEX_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .apply {
                    if (text.isBlank()) remove(id + PRESENT_SUFFIX)
                    else putBoolean(id + PRESENT_SUFFIX, true)
                }
                .commit()
            if (indexStored) removeLegacySecureMetadata(context, id)
        } else {
            context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .apply {
                    if (text.isBlank()) remove(id + TEXT_SUFFIX) else putString(id + TEXT_SUFFIX, text)
                    if (carrierImageUri == null) remove(id + IMAGE_SUFFIX)
                    else putString(id + IMAGE_SUFFIX, carrierImageUri.toString())
                    if (text.isBlank() && carrierImageUri == null) remove(id + PRESENT_SUFFIX)
                    else putBoolean(id + PRESENT_SUFFIX, true)
                }
                .apply()
        }
    }

    fun clear(context: Context, address: String, secure: Boolean) {
        val id = draftId(address, secure)
        if (secure) {
            SecureRepository.clearEncryptedDraft(context, id)
            context.getSharedPreferences(SECURE_INDEX_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(id + PRESENT_SUFFIX)
                .apply()
            removeLegacySecureMetadata(context, id)
        } else {
            context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(id + TEXT_SUFFIX)
                .remove(id + IMAGE_SUFFIX)
                .remove(id + PRESENT_SUFFIX)
                .apply()
        }
    }

    fun hasDraft(context: Context, address: String, secure: Boolean): Boolean {
        val id = draftId(address, secure)
        return if (secure) {
            migrateLegacySecureMetadata(context, id)
            context.getSharedPreferences(SECURE_INDEX_PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(id + PRESENT_SUFFIX, false)
        } else {
            context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(id + PRESENT_SUFFIX, false)
        }
    }

    private fun migrateLegacySecureMetadata(context: Context, id: String) {
        val ordinary = context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
        if (ordinary.getBoolean(id + PRESENT_SUFFIX, false)) {
            val migrated = context.getSharedPreferences(SECURE_INDEX_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(id + PRESENT_SUFFIX, true)
                .commit()
            if (!migrated) return
        }
        removeLegacySecureMetadata(context, id)
    }

    private fun removeLegacySecureMetadata(context: Context, id: String) {
        context.getSharedPreferences(ORDINARY_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(id + TEXT_SUFFIX)
            .remove(id + IMAGE_SUFFIX)
            .remove(id + PRESENT_SUFFIX)
            .apply()
    }

    internal fun draftId(address: String, secure: Boolean): String {
        val canonical = "${if (secure) "vessel" else "signal"}|${address.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
