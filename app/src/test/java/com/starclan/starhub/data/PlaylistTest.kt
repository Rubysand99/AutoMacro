package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {
    private val yt = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    private val tt = "https://www.tiktok.com/@user/video/7234567890123456789"
    private val fb = "https://www.facebook.com/watch/?v=1234567890"

    // ---- nhan dien link ----
    @Test fun isLink_only_http_and_https() {
        assertTrue(MusicLinks.isLink(yt))
        assertTrue(MusicLinks.isLink("  HTTP://a.com/x "))
        assertFalse(MusicLinks.isLink("content://media/external/audio/1"))
        assertFalse(MusicLinks.isLink("file:///sdcard/a.mp3"))
        assertFalse(MusicLinks.isLink(""))
    }

    @Test fun extract_many_links_from_pasted_text() {
        val text = "Nhac chill:\n$yt\n  $tt, roi $fb).\nlai $yt \nkhong phai link: abc.com"
        assertEquals(listOf(yt, tt, fb), MusicLinks.extract(text))   // bo trung, bo dau cau cuoi, giu thu tu
    }

    @Test fun extract_ignores_junk() {
        assertEquals(emptyList<String>(), MusicLinks.extract(""))
        assertEquals(emptyList<String>(), MusicLinks.extract("http://"))
        assertEquals(emptyList<String>(), MusicLinks.extract("https://localhost/abc"))   // khong co dau cham trong ten mien
        assertEquals(listOf("https://youtu.be/abc123"), MusicLinks.extract("<https://youtu.be/abc123>"))
    }

    @Test fun siteName_known_sites() {
        assertEquals("YouTube", MusicLinks.siteName(yt))
        assertEquals("YouTube", MusicLinks.siteName("https://youtu.be/abc"))
        assertEquals("YouTube", MusicLinks.siteName("https://music.youtube.com/watch?v=abc"))
        assertEquals("TikTok", MusicLinks.siteName("https://vm.tiktok.com/ZMabc/"))
        assertEquals("Facebook", MusicLinks.siteName(fb))
        assertEquals("Facebook", MusicLinks.siteName("https://fb.watch/abcd/"))
        assertEquals("example.org", MusicLinks.siteName("https://www.example.org/a.mp3"))
    }

    @Test fun host_strips_credentials_port_and_www() {
        assertEquals("youtube.com", MusicLinks.host("https://user:pw@www.youtube.com:443/x?y=1"))
        assertEquals("facebook.com", MusicLinks.host("https://m.facebook.com/watch"))
    }

    @Test fun shortLabel_uses_video_id_until_real_title_known() {
        assertEquals("YouTube · dQw4w9WgXcQ", MusicLinks.shortLabel(yt))
        assertEquals("YouTube · abc123", MusicLinks.shortLabel("https://youtu.be/abc123"))
        assertEquals("TikTok", MusicLinks.shortLabel("https://tiktok.com"))
    }

    @Test fun track_displayName_prefers_title() {
        assertEquals("Bai hat", Track(yt, "Bai hat").displayName)
        assertEquals("YouTube · dQw4w9WgXcQ", Track(yt).displayName)
        assertEquals("content://x/1", Track("content://x/1").displayName)   // file chua co ten (UI se tu dat ten tu he thong)
        assertTrue(Track(yt).isLink)
        assertFalse(Track("content://x/1").isLink)
    }

    // ---- sua playlist ----
    @Test fun addTracks_skips_duplicates_and_blank_and_keeps_order() {
        val m = MusicConfig().addTracks(listOf(Track("a"), Track("b"), Track("a"), Track("  ")))
        assertEquals(listOf("a", "b"), m.tracks.map { it.source })
        val m2 = m.addTracks(listOf(Track("b", "ten khac"), Track("c")))
        assertEquals(listOf("a", "b", "c"), m2.tracks.map { it.source })
        assertEquals("", m2.tracks[1].title)   // trung nguon thi giu ban cu
    }

    @Test fun addTracks_caps_at_max() {
        val many = (1..MusicConfig.MAX_TRACKS + 20).map { Track("https://a.com/$it") }
        assertEquals(MusicConfig.MAX_TRACKS, MusicConfig().addTracks(many).tracks.size)
    }

    @Test fun removeAt_and_move() {
        val m = MusicConfig(tracks = listOf(Track("a"), Track("b"), Track("c")))
        assertEquals(listOf("a", "c"), m.removeAt(1).tracks.map { it.source })
        assertSame(m, m.removeAt(5))
        assertSame(m, m.removeAt(-1))
        assertEquals(listOf("b", "a", "c"), m.move(1, -1).tracks.map { it.source })
        assertEquals(listOf("a", "c", "b"), m.move(1, +1).tracks.map { it.source })
        assertSame(m, m.move(0, -1))     // da o dau
        assertSame(m, m.move(2, +1))     // da o cuoi
        assertSame(m, m.move(1, 0))
    }

    @Test fun withTitle_only_changes_matching_track() {
        val m = MusicConfig(tracks = listOf(Track(yt), Track(tt)))
        val m2 = m.withTitle(yt, "Never Gonna")
        assertEquals("Never Gonna", m2.tracks[0].title)
        assertEquals("", m2.tracks[1].title)
        assertSame(m2, m2.withTitle(yt, "Never Gonna"))   // khong doi gi
        assertSame(m, m.withTitle(yt, "  "))              // ten rong bo qua
        assertSame(m, m.withTitle("khong-co", "x"))
    }

    // ---- luu / doc ----
    @Test fun json_round_trip() {
        val tracks = listOf(Track(yt, "Bai \"1\""), Track("content://media/1", "File.mp3"), Track(tt))
        assertEquals(tracks, PlaylistJson.decode(PlaylistJson.encode(tracks)))
    }

    @Test fun json_decode_is_forgiving() {
        assertEquals(emptyList<Track>(), PlaylistJson.decode(null))
        assertEquals(emptyList<Track>(), PlaylistJson.decode(""))
        assertEquals(emptyList<Track>(), PlaylistJson.decode("khong phai json {"))
        assertEquals(emptyList<Track>(), PlaylistJson.decode("{\"a\":1}"))
        assertEquals(listOf(Track("ok", "")), PlaylistJson.decode("[{\"source\":\"ok\"},{\"title\":\"thieu nguon\"},null,{\"source\":\"  \"}]"))
    }

    @Test fun loadTracks_migrates_old_single_file_once() {
        assertEquals(listOf(Track("content://x/1", "Bai cu")), loadTracks(null, "content://x/1", "Bai cu"))
        assertEquals(listOf(Track("content://x/1", "")), loadTracks("", " content://x/1 ", null))
        assertEquals(emptyList<Track>(), loadTracks(null, "", "ten"))
        assertEquals(emptyList<Track>(), loadTracks(null, null, null))
        // da luu playlist (ke ca danh sach rong do Ruby xoa het) -> khong hoi sinh bai cu
        assertEquals(emptyList<Track>(), loadTracks("[]", "content://x/1", "Bai cu"))
        assertEquals(listOf(Track("a", "A")), loadTracks(PlaylistJson.encode(listOf(Track("a", "A"))), "content://x/1", "Bai cu"))
    }

    // ---- chuyen bai ----
    @Test fun nextIndex_sequential_wraps_around() {
        assertEquals(1, PlaylistRules.nextIndex(0, 3, false) { 0 })
        assertEquals(0, PlaylistRules.nextIndex(2, 3, false) { 0 })
        assertEquals(0, PlaylistRules.nextIndex(0, 1, false) { 0 })
        assertEquals(-1, PlaylistRules.nextIndex(0, 0, false) { 0 })
        assertEquals(0, PlaylistRules.nextIndex(-1, 3, false) { 0 })   // chua phat bai nao -> bai dau
    }

    @Test fun nextIndex_shuffle_never_repeats_current_and_covers_all_others() {
        for (cur in 0..3) {
            val seen = (0..2).map { r -> PlaylistRules.nextIndex(cur, 4, true) { r } }.toSet()
            assertEquals((0..3).filter { it != cur }.toSet(), seen)
        }
        assertEquals(0, PlaylistRules.nextIndex(0, 1, true) { 0 })
        // chua phat bai nao: bai CUOI cung phai co the duoc chon
        assertEquals(3, PlaylistRules.nextIndex(-1, 4, true) { it - 1 })
        assertEquals(0, PlaylistRules.nextIndex(-1, 4, true) { 0 })
        assertEquals(0, PlaylistRules.nextIndex(7, 4, false) { 0 })   // bai hien tai khong con trong danh sach -> bai dau
        // random tra ve gia tri ngoai khoang van khong vuot danh sach
        assertTrue(PlaylistRules.nextIndex(1, 3, true) { 99 } in 0..2)
        assertTrue(PlaylistRules.nextIndex(1, 3, true) { -5 } in 0..2)
    }

    @Test fun giveUp_after_every_track_failed_in_a_row() {
        assertFalse(PlaylistRules.shouldGiveUp(2, 3))
        assertTrue(PlaylistRules.shouldGiveUp(3, 3))
        assertTrue(PlaylistRules.shouldGiveUp(0, 0))
        assertTrue(PlaylistRules.shouldGiveUp(1, 1))
    }
}
