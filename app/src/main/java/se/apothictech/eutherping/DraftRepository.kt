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
    private const val PREFERENCES = "eutherping_conversation_drafts"
    private const val TEXT_SUFFIX = ".text"
    private const val IMAGE_SUFFIX = ".image"
    private const val PRESENT_SUFFIX = ".present"

    fun load(context: Context, address: String, secure: Boolean): StoredConversationDraft {
        val id = draftId(address, secure)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val text = if (secure) {
            SecureRepository.loadEncryptedDraft(context, id).orEmpty()
        } else {
            preferences.getString(id + TEXT_SUFFIX, null).orEmpty()
        }
        val image = if (secure) null else preferences.getString(id + IMAGE_SUFFIX, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
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
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (secure) {
            SecureRepository.saveEncryptedDraft(context, id, text)
            preferences.edit()
                .remove(id + TEXT_SUFFIX)
                .remove(id + IMAGE_SUFFIX)
                .apply {
                    if (text.isBlank()) remove(id + PRESENT_SUFFIX)
                    else putBoolean(id + PRESENT_SUFFIX, true)
                }
                .apply()
        } else {
            preferences.edit()
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
        SecureRepository.clearEncryptedDraft(context, id)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(id + TEXT_SUFFIX)
            .remove(id + IMAGE_SUFFIX)
            .remove(id + PRESENT_SUFFIX)
            .apply()
    }

    fun hasDraft(context: Context, address: String, secure: Boolean): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(draftId(address, secure) + PRESENT_SUFFIX, false)

    private fun draftId(address: String, secure: Boolean): String {
        val canonical = "${if (secure) "vessel" else "signal"}|${address.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
