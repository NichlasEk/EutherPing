package se.apothictech.eutherping

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.Window
import androidx.metrics.performance.JankStats
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

internal object PerformanceDiagnostics {
    private const val DIRECTORY = "diagnostics"
    private const val FILE_NAME = "performance-v1.jsonl"
    private const val MAX_BYTES = 256 * 1024L
    private const val KEEP_LINES = 300

    @Synchronized
    fun record(
        context: Context,
        event: String,
        durationMs: Long,
        values: Map<String, Long> = emptyMap(),
    ) {
        val safeEvent = event.filter { it.isLetterOrDigit() || it == '_' }.take(48)
        if (safeEvent.isBlank()) return
        val payload = JSONObject().apply {
            put("at", System.currentTimeMillis())
            put("event", safeEvent)
            put("duration_ms", durationMs.coerceAtLeast(0L))
            values.forEach { (key, value) ->
                val safeKey = key.filter { it.isLetterOrDigit() || it == '_' }.take(32)
                if (safeKey.isNotBlank()) put(safeKey, value)
            }
        }
        val file = file(context)
        file.parentFile?.mkdirs()
        file.appendText(payload.toString() + "\n")
        if (file.length() > MAX_BYTES) {
            val retained = file.readLines().takeLast(KEEP_LINES)
            file.writeText(retained.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun report(context: Context): String = buildString {
        appendLine("EutherPing local diagnostics")
        appendLine("Generated: ${Instant.now()}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android API: ${Build.VERSION.SDK_INT}")
        appendLine("Memory class MiB: ${context.getSystemService(android.app.ActivityManager::class.java).memoryClass}")
        appendLine()
        appendLine("Privacy: this report contains timings, frame counts, app/device versions, and numeric row counts only.")
        appendLine("It deliberately excludes phone numbers, contacts, message text, attachment names, keys, addresses, and network endpoints.")
        appendLine()
        appendLine("Performance events (oldest to newest):")
        val file = file(context)
        if (!file.exists() || file.length() == 0L) {
            appendLine("No events recorded yet.")
        } else {
            file.forEachLine { line ->
                runCatching { JSONObject(line) }.getOrNull()?.let { event ->
                    append(event.optLong("at"))
                    append(" ")
                    append(event.optString("event"))
                    append(" duration_ms=")
                    append(event.optLong("duration_ms"))
                    event.keys().asSequence()
                        .filterNot { it == "at" || it == "event" || it == "duration_ms" }
                        .sorted()
                        .forEach { key -> append(" $key=${event.optLong(key)}") }
                    appendLine()
                }
            }
        }
    }

    fun export(context: Context, destination: Uri): Result<Unit> = runCatching {
        checkNotNull(context.contentResolver.openOutputStream(destination, "w")) {
            "Android did not open the selected diagnostics file"
        }.bufferedWriter().use { it.write(report(context)) }
    }

    @Synchronized
    fun clear(context: Context): Boolean = file(context).let { !it.exists() || it.delete() }

    private fun file(context: Context): File = File(File(context.filesDir, DIRECTORY), FILE_NAME)
}

internal class FramePerformanceTracker(context: Context, window: Window) {
    private val appContext = context.applicationContext
    private val totalFrames = AtomicLong()
    private val jankyFrames = AtomicLong()
    private val totalDurationNanos = AtomicLong()
    private val jankStats = JankStats.createAndTrack(window) { frame ->
        totalFrames.incrementAndGet()
        totalDurationNanos.addAndGet(frame.frameDurationUiNanos)
        if (frame.isJank) jankyFrames.incrementAndGet()
    }

    fun flush() {
        val frames = totalFrames.getAndSet(0L)
        if (frames == 0L) return
        val janky = jankyFrames.getAndSet(0L)
        val duration = totalDurationNanos.getAndSet(0L)
        PerformanceDiagnostics.record(
            appContext,
            event = "render_frames",
            durationMs = duration / 1_000_000L,
            values = mapOf("frames" to frames, "janky" to janky),
        )
    }

    fun stop() {
        flush()
        jankStats.isTrackingEnabled = false
    }
}
