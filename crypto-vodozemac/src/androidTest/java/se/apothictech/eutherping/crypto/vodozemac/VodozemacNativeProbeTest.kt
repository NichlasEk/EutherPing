// SPDX-License-Identifier: Apache-2.0

package se.apothictech.eutherping.crypto.vodozemac

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodozemacNativeProbeTest {
    @Test
    fun ratchetRunsInRealAndroidNativeLibrary() {
        val metrics = JSONObject(VodozemacNativeProbe.runProbe())

        assertEquals("0.10.0", metrics.getString("version"))
        assertEquals("reply", metrics.getString("replyPlaintext"))
        assertTrue(metrics.getInt("publicationBytes") <= 256)
        assertTrue(metrics.getInt("initialCiphertextBytes") <= 256)
        assertTrue(metrics.getInt("sessionCiphertextBytes") <= 128)
        assertTrue(metrics.getBoolean("outOfOrderAccepted"))
        assertTrue(metrics.getBoolean("reloadAccepted"))
        assertTrue(metrics.getBoolean("duplicateRejected"))
        assertTrue(metrics.getBoolean("nonContributoryKeyRejected"))
        assertTrue(metrics.getBoolean("identityMismatchRejected"))
    }
}
