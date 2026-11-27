package com.example.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.CartoonLanguage
import com.example.model.CartoonScene
import com.example.model.CartoonStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.sin

class AudioSynthesizer(private val context: Context) {

    suspend fun synthesizeSceneAudio(
        scene: CartoonScene,
        language: CartoonLanguage,
        style: CartoonStyle,
        includeVoiceover: Boolean,
        includeBgm: Boolean,
        includeSfx: Boolean,
        outputWavFile: File
    ): File = withContext(Dispatchers.IO) {
        val sampleRate = SAMPLE_RATE
        val durationSec = scene.durationSeconds.coerceIn(5L, 180L).toInt()
        val totalSamples = sampleRate * durationSec
        val mixBuffer = FloatArray(totalSamples)

        // 1. Optional AI/System TTS Voiceover
        var voiceSamples: ShortArray? = null
        if (includeVoiceover && scene.narrationText.isNotBlank()) {
            val tempVoiceWav = File(outputWavFile.parentFile, "tts_${scene.sceneIndex}.wav")
            val ok = synthesizeTtsToWav(scene.narrationText, language, tempVoiceWav)
            if (ok && tempVoiceWav.exists() && tempVoiceWav.length() > 44) {
                voiceSamples = readWavAsMono16kOr44k(tempVoiceWav, sampleRate)
            }
            tempVoiceWav.delete()
        }

        // 2. Mix Voiceover into mixBuffer (starts at 0.4s)
        val voiceStartIdx = (sampleRate * 0.4f).toInt()
        val voiceEndIdx = if (voiceSamples != null) {
            (voiceStartIdx + voiceSamples.size).coerceAtMost(totalSamples)
        } else 0

        if (voiceSamples != null) {
            for (i in voiceStartIdx until voiceEndIdx) {
                val srcIdx = i - voiceStartIdx
                if (srcIdx < voiceSamples.size) {
                    mixBuffer[i] += (voiceSamples[srcIdx] / 32768f) * 0.92f
                }
            }
        }

        // 3. Mix procedural mood-matched Background Music (ducks under voiceover!)
        if (includeBgm) {
            addStyleBackgroundMusic(
                buffer = mixBuffer,
                sampleRate = sampleRate,
                style = style,
                seedOffset = scene.sceneIndex.toInt(),
                voiceStart = voiceStartIdx,
                voiceEnd = voiceEndIdx
            )
        }

        // 4. Mix procedural Sound Effect cue
        if (includeSfx) {
            addSfxCue(
                buffer = mixBuffer,
                sampleRate = sampleRate,
                cue = scene.sfxCue
            )
        }

        // 5. Soft-clip & write 16-bit PCM WAV
        outputWavFile.parentFile?.mkdirs()
        writePcm16Wav(outputWavFile, mixBuffer, sampleRate)
        outputWavFile
    }

    private suspend fun synthesizeTtsToWav(
        text: String,
        language: CartoonLanguage,
        outFile: File
    ): Boolean = withTimeoutOrNull(20_000L) {
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    tts?.shutdown()
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                val engine = tts ?: run {
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                val targetLocale = Locale.forLanguageTag(language.localeTag)
                val langSupport = engine.isLanguageAvailable(targetLocale)
                if (langSupport >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = targetLocale
                } else {
                    engine.language = Locale.US
                }
                engine.setSpeechRate(0.95f)

                val utteranceId = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(false)
                    }
                })

                val params = Bundle()
                val res = engine.synthesizeToFile(text.take(2500), params, outFile, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    engine.shutdown()
                    if (cont.isActive) cont.resume(false)
                }
            }
            cont.invokeOnCancellation {
                runCatching { tts?.stop(); tts?.shutdown() }
            }
        }
    } ?: false

    private fun readWavAsMono16kOr44k(file: File, targetRate: Int): ShortArray? {
        return try {
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val header = ByteArray(44)
                if (bis.read(header) < 44) return null
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt().coerceAtLeast(1)
                val srcRate = bb.getInt(24).coerceAtLeast(8000)
                val rawBytes = bis.readBytes()
                val srcSamplesCount = rawBytes.size / (2 * channels)
                if (srcSamplesCount <= 0) return null

                val srcMono = ShortArray(srcSamplesCount)
                val rawBb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until srcSamplesCount) {
                    var sum = 0
                    for (c in 0 until channels) {
                        sum += rawBb.getShort((i * channels + c) * 2).toInt()
                    }
                    srcMono[i] = (sum / channels).toShort()
                }
                if (srcRate == targetRate) return srcMono

                val ratio = srcRate.toDouble() / targetRate.toDouble()
                val outLen = (srcSamplesCount / ratio).toInt().coerceAtLeast(1)
                ShortArray(outLen) { outIdx ->
                    val srcPos = (outIdx * ratio).toInt().coerceIn(0, srcSamplesCount - 1)
                    srcMono[srcPos]
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading WAV: ${e.message}")
            null
        }
    }

    private fun addStyleBackgroundMusic(
        buffer: FloatArray,
        sampleRate: Int,
        style: CartoonStyle,
        seedOffset: Int,
        voiceStart: Int,
        voiceEnd: Int
    ) {
        val scale = when (style) {
            CartoonStyle.HORROR -> doubleArrayOf(220.0, 233.08, 261.63, 293.66, 311.13)
            CartoonStyle.ADVENTURE, CartoonStyle.THREE_D -> doubleArrayOf(261.63, 293.66, 329.63, 392.0, 440.0, 523.25)
            CartoonStyle.FANTASY -> doubleArrayOf(293.66, 329.63, 369.99, 440.0, 493.88, 587.33)
            else -> doubleArrayOf(329.63, 392.0, 440.0, 523.25, 587.33, 659.25)
        }
        val beatSamples = (sampleRate * 0.45).toInt()
        for (i in buffer.indices) {
            val t = i.toDouble() / sampleRate
            val beatIdx = (i / beatSamples) + seedOffset
            val noteFreq = scale[beatIdx % scale.size]
            val bassFreq = scale[0] * 0.5
            val beatProgress = (i % beatSamples).toFloat() / beatSamples.toFloat()
            val env = (1f - beatProgress).coerceIn(0f, 1f) * 0.65f + 0.35f

            val melody = sin(2.0 * PI * noteFreq * t).toFloat() * env * 0.14f
            val bass = sin(2.0 * PI * bassFreq * t).toFloat() * 0.10f
            val duck = if (i in voiceStart until voiceEnd) 0.32f else 0.85f
            buffer[i] += (melody + bass) * duck
        }
    }

    private fun addSfxCue(buffer: FloatArray, sampleRate: Int, cue: String) {
        val start = (sampleRate * 0.12f).toInt()
        val length = (sampleRate * 0.55f).toInt()
        for (j in 0 until length) {
            val idx = start + j
            if (idx >= buffer.size) break
            val p = j.toFloat() / length.toFloat()
            val env = sin(PI * p).toFloat()
            val t = j.toDouble() / sampleRate
            val sample = when (cue.lowercase()) {
                "thunder" -> (kotlin.random.Random.nextFloat() * 2f - 1f) * (1f - p) * 0.35f
                "boing" -> sin(2.0 * PI * (180.0 + 380.0 * sin(p * 14.0)) * t).toFloat() * env * 0.28f
                "whoosh" -> (kotlin.random.Random.nextFloat() * 2f - 1f) * env * 0.22f
                "chime", "sparkle" -> (
                    sin(2.0 * PI * (784.0 + 440.0 * p) * t) +
                        0.5 * sin(2.0 * PI * 1568.0 * t)
                    ).toFloat() * env * 0.18f
                else -> sin(2.0 * PI * (320.0 + 220.0 * p) * t).toFloat() * env * 0.18f
            }
            buffer[idx] += sample
        }
    }

    private fun writePcm16Wav(file: File, samples: FloatArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
            dos.writeBytes("RIFF")
            dos.writeInt(Integer.reverseBytes(36 + dataSize))
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            dos.writeInt(Integer.reverseBytes(16))
            dos.writeShort(java.lang.Short.reverseBytes(1).toInt()) // PCM
            dos.writeShort(java.lang.Short.reverseBytes(1).toInt()) // Mono
            dos.writeInt(Integer.reverseBytes(sampleRate))
            dos.writeInt(Integer.reverseBytes(sampleRate * 2))
            dos.writeShort(java.lang.Short.reverseBytes(2).toInt())
            dos.writeShort(java.lang.Short.reverseBytes(16).toInt())
            dos.writeBytes("data")
            dos.writeInt(Integer.reverseBytes(dataSize))

            val byteBuf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clamped = (s.coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort()
                byteBuf.putShort(clamped)
            }
            dos.write(byteBuf.array())
        }
    }

    companion object {
        private const val TAG = "AudioSynthesizer"
        const val SAMPLE_RATE = 44100
    }
}
