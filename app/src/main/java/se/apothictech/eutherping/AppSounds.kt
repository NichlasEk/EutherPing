package se.apothictech.eutherping

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

enum class AppSound(internal val resourceId: Int) {
    TERMINAL_TICK(R.raw.terminal_tick),
    SIGNAL_SENT(R.raw.signal_sent),
    SECURE_SEALED(R.raw.secure_sealed),
    SECURE_VERIFIED(R.raw.secure_verified),
    IDENTITY_WARNING(R.raw.identity_warning),
    TERMINAL_ERROR(R.raw.terminal_error),
}

object AppSounds {
    private const val ENABLED_PREFERENCE = "prop_sounds_enabled"
    private const val PLAYBACK_VOLUME = 0.38f

    private val lock = Any()
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<AppSound, Int>()
    private val loadedIds = mutableSetOf<Int>()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (soundPool != null) return
            val pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) synchronized(lock) { loadedIds += sampleId }
            }
            AppSound.entries.forEach { sound ->
                soundIds[sound] = pool.load(context.applicationContext, sound.resourceId, 1)
            }
            soundPool = pool
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_PREFERENCE, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_PREFERENCE, enabled)
            .apply()
    }

    fun play(context: Context, sound: AppSound) {
        if (!isEnabled(context)) return
        initialize(context)
        synchronized(lock) {
            val pool = soundPool ?: return
            val soundId = soundIds[sound] ?: return
            if (soundId !in loadedIds) return
            pool.play(soundId, PLAYBACK_VOLUME, PLAYBACK_VOLUME, 1, 0, 1f)
        }
    }
}
