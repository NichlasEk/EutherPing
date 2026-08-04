package se.apothictech.eutherping.secure

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject

internal data class AuthenticatedSecureFrame(
    val kind: String,
    val id: String,
    val timestamp: Long,
    val senderFingerprint: String,
    val ciphertextSha256: String,
)

enum class SecureFrameAcceptance {
    ACCEPTED,
    DUPLICATE,
    STALE,
    INVALID,
    NOT_AUTHENTICATED_FRAME,
}

object SecureReplayRepository {
    private const val PREFS = "eutherping_secure_replay_v1"
    private const val MAX_ACCEPTED_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_FUTURE_SKEW_MS = 24L * 60 * 60 * 1000
    private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    private const val MAX_RECORDS = 4_096

    @Synchronized
    internal fun accept(
        context: Context,
        frame: AuthenticatedSecureFrame,
        now: Long = System.currentTimeMillis(),
    ): SecureFrameAcceptance {
        if (!isFresh(frame.timestamp, now)) return SecureFrameAcceptance.STALE
        if (frame.id.isBlank() || frame.kind.isBlank() || frame.senderFingerprint.isBlank() ||
            !frame.ciphertextSha256.matches(Regex("[0-9a-f]{64}"))
        ) return SecureFrameAcceptance.INVALID

        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storageKey = storageKey(frame)
        preferences.getString(storageKey, null)?.let { stored ->
            val previousHash = runCatching { JSONObject(stored).getString("hash") }.getOrNull()
                ?: return SecureFrameAcceptance.INVALID
            return if (previousHash == frame.ciphertextSha256) {
                SecureFrameAcceptance.DUPLICATE
            } else {
                SecureFrameAcceptance.INVALID
            }
        }

        val editor = preferences.edit()
        val retained = preferences.all.mapNotNull { (key, raw) ->
            val acceptedAt = (raw as? String)?.let {
                runCatching { JSONObject(it).getLong("acceptedAt") }.getOrNull()
            } ?: return@mapNotNull null
            if (acceptedAt < now - RETENTION_MS) {
                editor.remove(key)
                null
            } else {
                key to acceptedAt
            }
        }.sortedByDescending { it.second }
        retained.drop(MAX_RECORDS - 1).forEach { (key, _) -> editor.remove(key) }
        editor.putString(
            storageKey,
            JSONObject()
                .put("hash", frame.ciphertextSha256)
                .put("acceptedAt", now)
                .toString(),
        )
        return if (editor.commit()) SecureFrameAcceptance.ACCEPTED else SecureFrameAcceptance.INVALID
    }

    internal fun isFresh(timestamp: Long, now: Long): Boolean =
        timestamp >= now - MAX_ACCEPTED_AGE_MS && timestamp <= now + MAX_FUTURE_SKEW_MS

    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun storageKey(frame: AuthenticatedSecureFrame): String {
        val canonical = "${frame.senderFingerprint}|${frame.kind}|${frame.id}".toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(canonical).joinToString("") {
            "%02x".format(it)
        }
    }
}
