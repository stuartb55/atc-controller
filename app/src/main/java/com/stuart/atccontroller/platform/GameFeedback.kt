package com.stuart.atccontroller.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.stuart.atccontroller.ui.SettingsUiState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Small, dependency-free feedback layer. The ambient bed is synthesized into a seamless static
 * PCM loop, keeping the premium game completely offline and avoiding licensed audio assets.
 *
 * Ambient preparation and every [AudioTrack] operation are confined to [audioExecutor]. Public
 * methods only update the requested state and enqueue reconciliation, so PCM generation and the
 * blocking static-buffer write never run on the main thread.
 */
class GameFeedback(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, AUDIO_THREAD_NAME)
        }
    private val ambientRequested = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private val ambientAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private val focusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            dispatchAudio { handleAudioFocusChange(focusChange) }
        }

    private val audioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(ambientAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()

    // The following fields are accessed only by audioExecutor.
    private var ambientTrack: AudioTrack? = null
    private var tone: ToneGenerator? = null
    private var toneVolumePercent = -1
    private var requestedAmbientVolume = 0f
    private var focusRequestActive = false
    private var playbackAllowedByFocus = false
    private var blockedByPermanentFocusLoss = false

    fun applySettings(settings: SettingsUiState) {
        if (closed.get()) return

        val musicVolume = settings.musicVolume.normalizedFeedbackVolume()
        ambientRequested.set(musicVolume > 0f)
        dispatchAudio {
            requestedAmbientVolume = musicVolume
            if (ambientRequested.get()) {
                // A new explicit playback request is allowed to try for focus again after a loss.
                blockedByPermanentFocusLoss = false
                ensureAmbientPlayback()
            } else {
                suspendAmbientPlayback()
            }
        }
    }

    fun clearance(settings: SettingsUiState) {
        if (closed.get()) return
        val effectsVolume = settings.effectsVolume.normalizedFeedbackVolume()
        if (effectsVolume > 0f) {
            dispatchAudio { playTone(ToneGenerator.TONE_PROP_ACK, 75, effectsVolume) }
        }
        if (settings.hapticsEnabled) vibrate(28, 55)
    }

    fun warning(settings: SettingsUiState) {
        if (closed.get()) return
        val effectsVolume = settings.effectsVolume.normalizedFeedbackVolume()
        if (effectsVolume > 0f) {
            dispatchAudio { playTone(ToneGenerator.TONE_SUP_ERROR, 180, effectsVolume) }
        }
        if (settings.hapticsEnabled) vibrate(90, 95)
    }

    fun missionComplete(settings: SettingsUiState) {
        if (closed.get()) return
        val effectsVolume = settings.effectsVolume.normalizedFeedbackVolume()
        if (effectsVolume > 0f) {
            dispatchAudio { playTone(ToneGenerator.TONE_PROP_PROMPT, 240, effectsVolume) }
        }
        if (settings.hapticsEnabled) vibrate(45, 70)
    }

    /** Prevents audio continuing, or delayed focus starting it, while the app is backgrounded. */
    fun onBackground() {
        ambientRequested.set(false)
        dispatchAudio { suspendAmbientPlayback() }
    }

    private fun ensureAmbientPlayback() {
        if (
            closed.get() ||
            !ambientRequested.get() ||
            blockedByPermanentFocusLoss
        ) {
            return
        }

        val track = ambientTrack ?: prepareAmbientTrack()?.also { ambientTrack = it } ?: return
        if (closed.get() || !ambientRequested.get()) return

        if (!focusRequestActive) requestAudioFocus()
        if (!playbackAllowedByFocus) return

        runCatching {
            track.setVolume(requestedAmbientVolume)
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        }.onFailure {
            releaseAmbientTrack()
            abandonAudioFocus()
        }
    }

    private fun prepareAmbientTrack(): AudioTrack? {
        val samples = ShortArray(SAMPLE_COUNT)
        for (index in samples.indices) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0 &&
                (closed.get() || !ambientRequested.get())
            ) {
                return null
            }

            val time = index.toDouble() / SAMPLE_RATE
            val slowPulse = 0.72 + 0.28 * sin(2.0 * PI * 0.25 * time)
            val signal =
                sin(2.0 * PI * 55.0 * time) * 0.42 +
                    sin(2.0 * PI * 82.5 * time) * 0.22 +
                    sin(2.0 * PI * 110.0 * time) * 0.10
            samples[index] =
                (signal * slowPulse * Short.MAX_VALUE * SYNTHESIS_GAIN).toInt().toShort()
        }

        if (closed.get() || !ambientRequested.get()) return null

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(ambientAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .build()
        }.getOrNull() ?: return null

        val configured = runCatching {
            check(track.state == AudioTrack.STATE_INITIALIZED) {
                "Ambient AudioTrack did not initialize"
            }
            check(
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) == samples.size,
            ) {
                "Ambient PCM buffer was not written completely"
            }
            check(track.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS) {
                "Ambient loop points could not be configured"
            }
            track.setVolume(requestedAmbientVolume)
        }.isSuccess

        if (!configured || closed.get() || !ambientRequested.get()) {
            track.runCatching { release() }
            return null
        }
        return track
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        val result =
            manager.runCatching { requestAudioFocus(audioFocusRequest) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                focusRequestActive = true
                playbackAllowedByFocus = true
            }

            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                focusRequestActive = true
                playbackAllowedByFocus = false
            }

            else -> {
                focusRequestActive = false
                playbackAllowedByFocus = false
            }
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusRequestActive = true
                playbackAllowedByFocus = true
                blockedByPermanentFocusLoss = false
                if (ambientRequested.get()) {
                    ensureAmbientPlayback()
                } else {
                    suspendAmbientPlayback()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (ambientRequested.get()) {
                    ambientTrack?.runCatching { setVolume(requestedAmbientVolume * DUCK_VOLUME_FACTOR) }
                } else {
                    suspendAmbientPlayback()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                playbackAllowedByFocus = false
                pauseAmbientTrack()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                playbackAllowedByFocus = false
                focusRequestActive = false
                blockedByPermanentFocusLoss = true
                pauseAmbientTrack()
            }
        }
    }

    private fun suspendAmbientPlayback() {
        pauseAmbientTrack()
        abandonAudioFocus()
        blockedByPermanentFocusLoss = false
    }

    private fun pauseAmbientTrack() {
        ambientTrack?.runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) pause()
            setVolume(requestedAmbientVolume)
        }
    }

    private fun abandonAudioFocus() {
        if (focusRequestActive) {
            audioManager?.runCatching { abandonAudioFocusRequest(audioFocusRequest) }
        }
        focusRequestActive = false
        playbackAllowedByFocus = false
    }

    private fun releaseAmbientTrack() {
        ambientTrack?.runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
            release()
        }
        ambientTrack = null
    }

    private fun playTone(toneType: Int, durationMillis: Int, volume: Float) {
        val requestedPercent = (volume.normalizedFeedbackVolume() * 100f).roundToInt().coerceIn(1, 100)
        if (toneVolumePercent != requestedPercent) {
            tone?.runCatching { release() }
            tone = null
            toneVolumePercent = requestedPercent
        }
        val generator = tone ?: runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, requestedPercent)
        }.getOrNull()?.also { tone = it } ?: return
        generator.runCatching { startTone(toneType, durationMillis) }
    }

    private fun dispatchAudio(action: () -> Unit) {
        if (closed.get()) return
        runCatching {
            audioExecutor.execute {
                if (!closed.get()) runCatching(action)
            }
        }
    }

    private fun vibrate(durationMillis: Long, amplitude: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.takeIf(Vibrator::hasVibrator)?.vibrate(
            VibrationEffect.createOneShot(durationMillis, amplitude.coerceIn(1, 255)),
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ambientRequested.set(false)

        // This is ordered after any in-flight preparation. Preparation observes closed and exits
        // early, while cleanup itself remains off the Activity destruction path.
        runCatching {
            audioExecutor.execute {
                pauseAmbientTrack()
                abandonAudioFocus()
                releaseAmbientTrack()
                tone?.runCatching { release() }
                tone = null
                toneVolumePercent = -1
            }
        }
        audioExecutor.shutdown()
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val LOOP_SECONDS = 4
        const val SAMPLE_COUNT = SAMPLE_RATE * LOOP_SECONDS
        const val CANCELLATION_CHECK_INTERVAL = 2_048
        const val SYNTHESIS_GAIN = 0.10
        const val DUCK_VOLUME_FACTOR = 0.26f
        const val AUDIO_THREAD_NAME = "game-feedback-audio"
    }
}

internal fun Float.normalizedFeedbackVolume(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f
