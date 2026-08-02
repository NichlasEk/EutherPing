package se.apothictech.eutherping.sms

import android.content.Context
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedConversation(
    val id: Int,
    val name: String,
    val initials: String,
    val preview: String,
    val time: String,
    val lane: String,
    val unread: Int,
    val distance: String,
    val smsAddress: String,
    val threadId: Long?,
)

data class CachedConversationIndex(
    val signals: List<CachedConversation>,
    val vessels: List<CachedConversation>,
    val updatedAt: Long,
)

object ConversationIndexCache {
    private const val SCHEMA = 1
    private const val FILE_NAME = "conversation-index-v1.json"
    private const val SECURE_PLACEHOLDER = "Encrypted Secure Ping // syncing…"

    fun load(context: Context): CachedConversationIndex? = runCatching {
        val atomic = AtomicFile(File(context.filesDir, FILE_NAME))
        val root = atomic.openRead().bufferedReader().use { JSONObject(it.readText()) }
        check(root.getInt("schema") == SCHEMA)
        CachedConversationIndex(
            signals = root.getJSONArray("signals").conversations(),
            vessels = root.getJSONArray("vessels").conversations(),
            updatedAt = root.getLong("updatedAt"),
        )
    }.getOrNull()

    fun save(context: Context, index: CachedConversationIndex): Result<Unit> = runCatching {
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("updatedAt", index.updatedAt)
            .put("signals", JSONArray().apply { index.signals.forEach { put(it.json(secure = false)) } })
            .put("vessels", JSONArray().apply { index.vessels.forEach { put(it.json(secure = true)) } })
        val atomic = AtomicFile(File(context.filesDir, FILE_NAME))
        val output = atomic.startWrite()
        try {
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun clear(context: Context) {
        AtomicFile(File(context.filesDir, FILE_NAME)).delete()
    }

    private fun CachedConversation.json(secure: Boolean) = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("initials", initials)
        .put("preview", if (secure) SECURE_PLACEHOLDER else preview)
        .put("time", time)
        .put("lane", lane)
        .put("unread", unread)
        .put("distance", distance)
        .put("smsAddress", smsAddress)
        .put("threadId", threadId ?: JSONObject.NULL)

    private fun JSONArray.conversations() = buildList {
        repeat(length()) { index ->
            val item = getJSONObject(index)
            add(
                CachedConversation(
                    id = item.getInt("id"),
                    name = item.getString("name"),
                    initials = item.getString("initials"),
                    preview = item.getString("preview"),
                    time = item.getString("time"),
                    lane = item.getString("lane"),
                    unread = item.getInt("unread"),
                    distance = item.getString("distance"),
                    smsAddress = item.getString("smsAddress"),
                    threadId = if (item.isNull("threadId")) null else item.getLong("threadId"),
                ),
            )
        }
    }
}
