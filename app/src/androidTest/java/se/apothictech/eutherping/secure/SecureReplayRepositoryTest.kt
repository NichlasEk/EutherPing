package se.apothictech.eutherping.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureReplayRepositoryTest {
    @Test
    fun acceptsFreshFrameOnceAndRejectsExactReplay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_800_000_000_000L
        SecureReplayRepository.clear(context)
        try {
            val frame = frame(id = "once", timestamp = now)
            assertEquals(SecureFrameAcceptance.ACCEPTED, SecureReplayRepository.accept(context, frame, now))
            assertEquals(SecureFrameAcceptance.DUPLICATE, SecureReplayRepository.accept(context, frame, now + 1))
        } finally {
            SecureReplayRepository.clear(context)
        }
    }

    @Test
    fun rejectsIdCollisionWithDifferentAuthenticatedCiphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_800_000_000_000L
        SecureReplayRepository.clear(context)
        try {
            assertEquals(
                SecureFrameAcceptance.ACCEPTED,
                SecureReplayRepository.accept(context, frame(id = "collision", timestamp = now), now),
            )
            assertEquals(
                SecureFrameAcceptance.INVALID,
                SecureReplayRepository.accept(
                    context,
                    frame(id = "collision", timestamp = now, ciphertextHash = "b".repeat(64)),
                    now + 1,
                ),
            )
        } finally {
            SecureReplayRepository.clear(context)
        }
    }

    @Test
    fun staleOrImplausiblyFutureFramesAreNotRecorded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_800_000_000_000L
        SecureReplayRepository.clear(context)
        try {
            val tooOld = now - 30L * 24 * 60 * 60 * 1000 - 1
            val tooNew = now + 24L * 60 * 60 * 1000 + 1
            assertEquals(
                SecureFrameAcceptance.STALE,
                SecureReplayRepository.accept(context, frame(id = "old", timestamp = tooOld), now),
            )
            assertEquals(
                SecureFrameAcceptance.STALE,
                SecureReplayRepository.accept(context, frame(id = "future", timestamp = tooNew), now),
            )
            assertEquals(
                SecureFrameAcceptance.ACCEPTED,
                SecureReplayRepository.accept(context, frame(id = "old", timestamp = now), now),
            )
        } finally {
            SecureReplayRepository.clear(context)
        }
    }

    private fun frame(
        id: String,
        timestamp: Long,
        ciphertextHash: String = "a".repeat(64),
    ) = AuthenticatedSecureFrame(
        kind = "message",
        id = id,
        timestamp = timestamp,
        senderFingerprint = "peer-fingerprint",
        ciphertextSha256 = ciphertextHash,
    )
}
