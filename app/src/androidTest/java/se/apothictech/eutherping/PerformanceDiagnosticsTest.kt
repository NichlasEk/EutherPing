package se.apothictech.eutherping

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PerformanceDiagnosticsTest {
    @Test
    fun reportPersistsOnlyWhitelistedNumericPerformanceData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PerformanceDiagnostics.clear(context)
        PerformanceDiagnostics.record(
            context,
            event = "conversation_page",
            durationMs = 37,
            values = mapOf("visible" to 20, "limit" to 20, "secure" to 0),
        )

        val report = PerformanceDiagnostics.report(context)
        assertTrue(report.contains("conversation_page duration_ms=37"))
        assertTrue(report.contains("visible=20"))
        assertTrue(report.contains("excludes phone numbers"))
        assertFalse(report.contains("message body"))
        assertFalse(report.contains("+4670"))

        val destination = File(context.cacheDir, "mms_view/diagnostics-test.txt").apply {
            parentFile?.mkdirs()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.securefiles", destination)
        PerformanceDiagnostics.export(context, uri).getOrThrow()
        assertTrue(destination.readText().contains("conversation_page"))
        destination.delete()
        PerformanceDiagnostics.clear(context)
    }
}
