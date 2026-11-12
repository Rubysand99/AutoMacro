package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicConfigTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that
    @Test fun default_is_off_and_has_no_track() {
        val m = MusicConfig()
        assertFalse(m.enabled)
        assertFalse(m.hasTrack)
        assertFalse(m.shouldPlay)
        assertEquals(MusicConfig.DEFAULT_VOLUME, m.volume)
    }

    @Test fun plays_only_when_enabled_and_track_chosen() {
        assertFalse(MusicConfig(enabled = true).shouldPlay)                      // bat nhung chua chon file
        assertFalse(MusicConfig(enabled = false, uri = "content://x/1").shouldPlay) // co file nhung tat
        assertTrue(MusicConfig(enabled = true, uri = "content://x/1").shouldPlay)
        assertFalse(MusicConfig(enabled = true, uri = "   ").shouldPlay)          // uri rong
    }

    @Test fun volume_is_clamped_and_converted_to_gain() {
        assertEquals(0, MusicConfig.clampVolume(-5))
        assertEquals(100, MusicConfig.clampVolume(250))
        assertEquals(0.5f, MusicConfig(volume = 50).gain, 0.0001f)
        assertEquals(1f, MusicConfig(volume = 999).gain, 0.0001f)
        assertEquals(0f, MusicConfig(volume = -1).gain, 0.0001f)
    }

    @Test fun tools_cache_never_keeps_captcha_flag() {
        val saved = ToolsCache.forStorage(BotSettings(autoCheck = false, hbAlert = true, captchaActive = true))
        assertFalse(saved.captchaActive)
        assertFalse(saved.autoCheck)
        assertTrue(saved.hbAlert)
    }
}
