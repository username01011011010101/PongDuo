package fr.pongduo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Effets sonores (SoundPool) + musique en boucle (MediaPlayer). Préférences mémorisées. */
class Sound(private val context: Context) {

    enum class Fx { HIT1, HIT2, WALL, POINT, SERVE, WIN }

    private val prefs = context.getSharedPreferences("pongduo", Context.MODE_PRIVATE)

    var musicOn = prefs.getBoolean("music", true)
        private set
    var sfxOn = prefs.getBoolean("sfx", true)
        private set

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = mapOf(
        Fx.HIT1 to pool.load(context, R.raw.sfx_hit1, 1),
        Fx.HIT2 to pool.load(context, R.raw.sfx_hit2, 1),
        Fx.WALL to pool.load(context, R.raw.sfx_wall, 1),
        Fx.POINT to pool.load(context, R.raw.sfx_point, 1),
        Fx.SERVE to pool.load(context, R.raw.sfx_serve, 1),
        Fx.WIN to pool.load(context, R.raw.sfx_win, 1),
    )

    private var music: MediaPlayer? = null
    private var active = false   // l'application est au premier plan

    fun play(fx: Fx) {
        if (!sfxOn) return
        ids[fx]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun toggleMusic() {
        musicOn = !musicOn
        prefs.edit().putBoolean("music", musicOn).apply()
        if (musicOn) startMusic() else stopMusic()
    }

    fun toggleSfx() {
        sfxOn = !sfxOn
        prefs.edit().putBoolean("sfx", sfxOn).apply()
    }

    fun onResume() {
        active = true
        if (musicOn) startMusic()
    }

    fun onPause() {
        active = false
        music?.pause()
    }

    fun release() {
        stopMusic()
        pool.release()
    }

    private fun startMusic() {
        if (!active) return
        val mp = music ?: MediaPlayer.create(context, R.raw.music)?.also {
            it.isLooping = true
            it.setVolume(0.45f, 0.45f)
            music = it
        } ?: return
        if (!mp.isPlaying) mp.start()
    }

    private fun stopMusic() {
        music?.let {
            it.stop()
            it.release()
        }
        music = null
    }
}
