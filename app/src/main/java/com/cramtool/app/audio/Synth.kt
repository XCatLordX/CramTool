package com.cramtool.app.audio

import com.cramtool.app.data.SoundStyle
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin

enum class Cue { CORRECT, WRONG, FINISH }

/**
 * 以程式合成原創提示音（不使用任何外部音效檔），輸出 16-bit mono WAV。
 * 純 Kotlin，可在 JVM 單元測試中驗證。
 */
object Synth {
    const val RATE = 44_100

    enum class Wave { SINE, BELL, TRIANGLE, SQUARE }

    data class Note(
        val freq: Double,
        val startMs: Int,
        val durMs: Int,
        val wave: Wave,
        val gain: Double = 1.0,
        /** 衰減速度，越大越短促 */
        val decay: Double = 5.0,
        /** 頻率滑動（結束時相對倍率），1.0 = 不滑動 */
        val slideTo: Double = 1.0,
    )

    private fun hz(semitonesFromA4: Int) = 440.0 * Math.pow(2.0, semitonesFromA4 / 12.0)
    // 常用音高
    private val C4 = hz(-9); private val E4 = hz(-5); private val G3 = hz(-14); private val A3 = hz(-12)
    private val C5 = hz(3); private val E5 = hz(7); private val G5 = hz(10); private val A5 = hz(12)
    private val C6 = hz(15); private val E6 = hz(19); private val G6 = hz(22); private val Cs6 = hz(16)

    fun notesFor(style: SoundStyle, cue: Cue): List<Note> = when (style) {
        SoundStyle.BRIGHT -> when (cue) {
            Cue.CORRECT -> listOf(Note(C6, 0, 140, Wave.BELL, decay = 7.0), Note(G6, 90, 260, Wave.BELL, decay = 6.0))
            Cue.WRONG -> listOf(Note(E4, 0, 150, Wave.TRIANGLE, 0.9, 4.0), Note(C4, 130, 260, Wave.TRIANGLE, 0.9, 4.0))
            Cue.FINISH -> listOf(
                Note(C5, 0, 160, Wave.BELL), Note(E5, 110, 160, Wave.BELL),
                Note(G5, 220, 160, Wave.BELL), Note(C6, 330, 520, Wave.BELL, decay = 3.5),
            )
        }
        SoundStyle.SOFT -> when (cue) {
            Cue.CORRECT -> listOf(
                Note(E5, 0, 220, Wave.SINE, 0.8, 9.0), Note(A5, 70, 240, Wave.SINE, 0.8, 9.0),
                Note(Cs6, 140, 360, Wave.SINE, 0.8, 7.0),
            )
            Cue.WRONG -> listOf(Note(A3, 0, 380, Wave.SINE, 1.0, 5.0, slideTo = 0.94))
            Cue.FINISH -> listOf(
                Note(A5, 0, 300, Wave.SINE, 0.7, 6.0), Note(E5, 0, 300, Wave.SINE, 0.5, 6.0),
                Note(Cs6, 160, 320, Wave.SINE, 0.7, 6.0), Note(E6, 320, 700, Wave.SINE, 0.7, 3.0),
            )
        }
        SoundStyle.RETRO -> when (cue) {
            Cue.CORRECT -> listOf(
                Note(C5, 0, 60, Wave.SQUARE, 0.5, 1.0), Note(E5, 60, 60, Wave.SQUARE, 0.5, 1.0),
                Note(G5, 120, 60, Wave.SQUARE, 0.5, 1.0), Note(C6, 180, 140, Wave.SQUARE, 0.5, 3.0),
            )
            Cue.WRONG -> listOf(Note(G3 * 1.5, 0, 320, Wave.SQUARE, 0.45, 2.0, slideTo = 0.55))
            Cue.FINISH -> listOf(
                Note(C5, 0, 90, Wave.SQUARE, 0.45, 1.0), Note(E5, 90, 90, Wave.SQUARE, 0.45, 1.0),
                Note(G5, 180, 90, Wave.SQUARE, 0.45, 1.0), Note(C6, 270, 90, Wave.SQUARE, 0.45, 1.0),
                Note(G5, 360, 90, Wave.SQUARE, 0.45, 1.0), Note(C6, 450, 360, Wave.SQUARE, 0.45, 2.5),
            )
        }
    }

    fun render(notes: List<Note>): ShortArray {
        val totalMs = notes.maxOf { it.startMs + it.durMs } + 20
        val buf = DoubleArray(RATE * totalMs / 1000)
        for (n in notes) {
            val start = RATE * n.startMs / 1000
            val len = RATE * n.durMs / 1000
            var phase = 0.0
            for (i in 0 until len) {
                val t = i.toDouble() / len
                val f = n.freq * (1 + (n.slideTo - 1) * t)
                phase += 2 * PI * f / RATE
                val raw = when (n.wave) {
                    Wave.SINE -> sin(phase)
                    Wave.BELL -> sin(phase) + 0.35 * sin(2 * phase) + 0.12 * sin(3.01 * phase)
                    Wave.TRIANGLE -> 2 / PI * kotlin.math.asin(sin(phase))
                    Wave.SQUARE -> sign(sin(phase))
                }
                val attack = (i / (RATE * 0.004)).coerceAtMost(1.0) // 4ms 起音，避免爆音
                val release = ((len - i) / (RATE * 0.012)).coerceAtMost(1.0) // 12ms 收尾
                val env = attack * release * exp(-n.decay * t)
                val idx = start + i
                if (idx < buf.size) buf[idx] += raw * env * n.gain
            }
        }
        val peak = buf.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        val scale = 0.8 / peak * Short.MAX_VALUE
        return ShortArray(buf.size) { (buf[it] * scale).toInt().toShort() }
    }

    fun wav(samples: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        val out = ByteArrayOutputStream()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + samples.size * 2); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(samples.size * 2)
        out.write(h.array()); out.write(data.array())
        return out.toByteArray()
    }

    fun cueWav(style: SoundStyle, cue: Cue): ByteArray = wav(render(notesFor(style, cue)))
}
