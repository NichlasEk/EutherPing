package se.apothictech.eutherping.secure

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import se.apothictech.eutherping.crypto.ProtocolAddress
import se.apothictech.eutherping.crypto.ProtocolPreKeyPublication
import se.apothictech.eutherping.crypto.SecureProtocolDescriptor
import se.apothictech.eutherping.crypto.storage.SecureSessionStateRepository
import se.apothictech.eutherping.crypto.vodozemac.VodozemacProvider

/**
 * Process-wide Vodozemac runtime for the primary on-device Secure identity.
 *
 * Startup creates the opaque account and one signed pre-key publication once.
 * Account state and the reusable public publication are committed through
 * [SecureSessionStateRepository]. Only an in-process copy is cached outside
 * that authenticated encrypted container.
 */
object SecureRatchetRuntime {
    internal const val NAMESPACE = "eutherping-primary-ratchet-v1"
    private const val LOCAL_ADDRESS = "eutherping-primary-device"
    private const val PUBLICATION_KEY = "app/primary_prekey_publication"

    private val startupRequested = AtomicBoolean(false)
    private val lock = Any()

    @Volatile
    private var cachedPublication: ProtocolPreKeyPublication? = null

    val descriptor: SecureProtocolDescriptor = VodozemacProvider().descriptor

    fun start(context: Context, onFailure: (Throwable) -> Unit = {}) {
        if (!startupRequested.compareAndSet(false, true)) return
        Thread(
            {
                ensureReady(context).exceptionOrNull()?.let(onFailure)
            },
            "EutherPing-ratchet-init",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun ensureReady(context: Context): Result<ProtocolPreKeyPublication> = runCatching {
        synchronized(lock) {
            val repository = SecureSessionStateRepository(context.applicationContext, NAMESPACE)
            cachedPublication?.copyPayload() ?: loadPublication(repository)?.also {
                cachedPublication = it
            }?.copyPayload() ?: createPublication(repository).also {
                cachedPublication = it
            }.copyPayload()
        }
    }

    private fun createPublication(
        repository: SecureSessionStateRepository,
    ): ProtocolPreKeyPublication {
        val engine = VodozemacProvider().createEngine(ProtocolAddress(LOCAL_ADDRESS), repository)
        val publication = engine.createPreKeyPublication()
        check(publication.providerId == descriptor.providerId) { "Unexpected ratchet provider" }
        repository.transaction("app:publication") { state ->
            state.write(PUBLICATION_KEY, publication.payload)
        }
        return publication
    }

    private fun loadPublication(
        repository: SecureSessionStateRepository,
    ): ProtocolPreKeyPublication? {
        val payload = repository.transaction("app:publication") { state ->
            state.read(PUBLICATION_KEY)
        } ?: return null
        require(payload.size == 164) { "Stored ratchet publication is malformed" }
        return ProtocolPreKeyPublication(descriptor.providerId, payload)
    }

    private fun ProtocolPreKeyPublication.copyPayload() = copy(payload = payload.copyOf())

    internal fun resetProcessCacheForTesting() = synchronized(lock) {
        cachedPublication?.payload?.fill(0)
        cachedPublication = null
        startupRequested.set(false)
    }

    internal fun deleteAllStateForVerifiedReset(context: Context) = synchronized(lock) {
        resetProcessCacheForTesting()
        SecureSessionStateRepository(context.applicationContext, NAMESPACE)
            .deleteAllStateForVerifiedReset()
    }
}
