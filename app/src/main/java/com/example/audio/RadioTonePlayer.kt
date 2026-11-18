package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class RadioTonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun playTxStartChirp() {
        scope.launch {
            playSequence(
                listOf(
                    ToneSegment(freqHz = 980.0, durationMs = 45, amplitude = 0.25),
                    ToneSegment(freqHz = 1320.0, durationMs = 55, amplitude = 0.28)
                )
            )
        }
    }

    fun playRogerBeep() {
        scope.launch {
            playSequence(
                listOf(
                    ToneSegment(freqHz = 1280.0, durationMs = 55, amplitude = 0.24),
                    ToneSegment(freqHz = 0.0, durationMs = 20, amplitude = 0.0),
                    ToneSegment(freqHz = 1560.0, durationMs = 80, amplitude = 0.26)
                )
            )
        }
    }

    fun playBusyAlert() {
        scope.launch {
            playSequence(
                listOf(
                    ToneSegment(freqHz = 440.0, durationMs = 90, amplitude = 0.28),
                    ToneSegment(freqHz = 0.0, durationMs = 40, amplitude = 0.0),
                    ToneSegment(freqHz = 380.0, durationMs = 110, amplitude = 0.28)
                )
            )
        }
    }

    private data class ToneSegment(
        val freqHz: Double,
        val durationMs: Int,
        val amplitude: Double
    )

    private fun playSequence(segments: List<ToneSegment>) {
        val sampleRate = 24_000
        val totalSamples = segments.sumOf { (sampleRate * it.durationMs) / 1000 }
        if (totalSamples <= 0) return

        val pcm = ShortArray(totalSamples)
        var offset = 0
        for (seg in segments) {
            val count = (sampleRate * seg.durationMs) / 1000
            val fadeSamples = (count / 6).coerceAtLeast(1)
            for (i in 0 until count) {
                val env = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > count - fadeSamples -> (count - i).toDouble() / fadeSamples
                    else -> 1.0
                }
                val sample = if (seg.freqHz > 0.0) {
                    sin(2.0 * PI * seg.freqHz * i / sampleRate) * seg.amplitude * env
                } else {
                    0.0
                }
                pcm[offset + i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            offset += count
        }

        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((segments.sumOf { it.durationMs } + 60).toLong())
            track.stop()
            track.release()
        }
    }
}
