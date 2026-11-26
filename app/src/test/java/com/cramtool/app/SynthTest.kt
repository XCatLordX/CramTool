package com.cramtool.app

import com.cramtool.app.audio.Cue
import com.cramtool.app.audio.Synth
import com.cramtool.app.data.SoundStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SynthTest {
    @Test fun everyCueRendersAudibleShortWav() {
        for (style in SoundStyle.entries) for (cue in Cue.entries) {
            val samples = Synth.render(Synth.notesFor(style, cue))
            val ms = samples.size * 1000 / Synth.RATE
            assertTrue("$style $cue 長度 $ms ms", ms in 150..1500)
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("$style $cue 不該無聲或爆音（peak=$peak）", peak in 20_000..Short.MAX_VALUE.toInt())
            // 開頭與結尾要接近 0，避免喀聲
            assertTrue(abs(samples.first().toInt()) < 2000 && abs(samples.last().toInt()) < 2000)
        }
    }

    @Test fun wavHeaderIsValid() {
        val wav = Synth.cueWav(SoundStyle.BRIGHT, Cue.CORRECT)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4)); assertEquals("data", String(wav, 36, 4))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(wav.size - 8, b.getInt(4))
        assertEquals(Synth.RATE, b.getInt(24))
        assertEquals(wav.size - 44, b.getInt(40))
    }

    @Test fun correctAndWrongSoundDifferent() {
        for (style in SoundStyle.entries) {
            assertTrue(!Synth.cueWav(style, Cue.CORRECT).contentEquals(Synth.cueWav(style, Cue.WRONG)))
        }
    }
}
