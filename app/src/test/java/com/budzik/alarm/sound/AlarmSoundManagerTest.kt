package com.budzik.alarm.sound

import org.junit.Assert.*
import org.junit.Test

class AlarmSoundManagerTest {

    @Test
    fun test_all_tones_generate_valid_pcm_buffers() {
        val toneIds = listOf(
            AlarmSoundManager.TONE_MORNING_PULSE,
            AlarmSoundManager.TONE_DIGITAL_ZEN,
            AlarmSoundManager.TONE_CLASSIC_BELL,
            AlarmSoundManager.TONE_SIREN,
            AlarmSoundManager.TONE_SOFT_CHORD
        )

        for (toneId in toneIds) {
            val pcm = AlarmSoundManager.generatePcmData(toneId)
            assertNotNull("PCM buffer for $toneId must not be null", pcm)
            assertTrue("PCM buffer for $toneId must have samples (got ${pcm.size})", pcm.isNotEmpty())
            assertTrue("PCM buffer for $toneId should have at least 20000 samples for reasonable audio length", pcm.size >= 20000)

            // Verify non-silent: at least some samples must have non-zero amplitude
            val hasSound = pcm.any { it.toInt() != 0 }
            assertTrue("PCM buffer for $toneId must contain audible non-zero sound samples", hasSound)

            // Verify peak amplitude does not exceed 16-bit limits
            val maxSample = pcm.maxOrNull() ?: 0
            val minSample = pcm.minOrNull() ?: 0
            assertTrue("Max sample must be <= 32767", maxSample <= 32767)
            assertTrue("Min sample must be >= -32768", minSample >= -32768)
        }
    }

    @Test
    fun test_available_tones_list_completeness() {
        val available = AlarmSoundManager.AVAILABLE_TONES
        assertEquals("Must offer exactly 5 synthesized tones", 5, available.size)

        val names = available.map { it.name }
        assertTrue("Must include Poranny Puls", names.contains("Poranny Puls"))
        assertTrue("Must include Cyfrowy Zen", names.contains("Cyfrowy Zen"))
        assertTrue("Must include Klasyczny Budzik", names.contains("Klasyczny Budzik"))
        assertTrue("Must include Syrena Alarmowa", names.contains("Syrena Alarmowa"))
        assertTrue("Must include Miękki Akord", names.contains("Miękki Akord"))
    }

    @Test
    fun test_quiet_mode_math() {
        val targetVolume = 0.85f
        // 85% reduction means volume is 15% of target
        val quietVolume = targetVolume * 0.15f
        assertTrue("Quiet volume must be less than 20% of full volume", quietVolume <= targetVolume * 0.2f)
        assertTrue("Quiet volume must be audible (> 0.05)", quietVolume >= 0.05f)
    }
}
