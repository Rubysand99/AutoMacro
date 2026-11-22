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

    // ---- bat/tat tung bai + che do phat ----
    private fun cfg(mode: PlayMode, vararg t: Track) = MusicConfig(tracks = t.toList(), mode = mode)

    @Test fun json_keeps_enabled_and_pickOrder_and_old_data_defaults_to_enabled() {
        val tracks = listOf(Track("a", "A", enabled = false, pickOrder = 0), Track("b", "B", enabled = true, pickOrder = 7))
        assertEquals(tracks, PlaylistJson.decode(PlaylistJson.encode(tracks)))
        // du lieu luu tu ban truoc (khong co enabled/pick) -> bai van BAT
        assertEquals(listOf(Track("x", "X", true, 0)), PlaylistJson.decode("[{\"source\":\"x\",\"title\":\"X\"}]"))
        assertEquals(0L, PlaylistJson.decode("[{\"source\":\"x\",\"pick\":-5}]")[0].pickOrder)
    }

    @Test fun loadMode_migrates_old_shuffle_switch() {
        assertEquals(PlayMode.SHUFFLE, loadMode(null, true))
        assertEquals(PlayMode.ADDED, loadMode("", false))
        assertEquals(PlayMode.PICKED, loadMode("picked", true))      // da co che do moi thi bo qua cong tac cu
        assertEquals(PlayMode.ADDED, loadMode("la-gi-do", false))
    }

    @Test fun disabled_tracks_are_never_played_and_do_not_count() {
        val m = cfg(PlayMode.ADDED, Track("a"), Track("b", enabled = false), Track("c"))
        assertEquals(2, m.playableCount)
        assertEquals(listOf(0, 2), m.playOrder())
        assertEquals(2, m.nextIndex(0) { 0 })
        assertEquals(0, m.nextIndex(2) { 0 })   // het danh sach -> quay lai bai dau, bo qua bai tat
        assertTrue(MusicConfig(enabled = true, tracks = m.tracks).shouldPlay)
        val none = MusicConfig(enabled = true, tracks = listOf(Track("a", enabled = false)))
        assertFalse(none.shouldPlay)
        assertTrue(none.hasTrack)
        assertFalse(none.hasPlayable)
        assertEquals(-1, none.nextIndex(-1) { 0 })
    }

    @Test fun added_mode_follows_list_order_and_move_changes_it() {
        val m = cfg(PlayMode.ADDED, Track("a", pickOrder = 3), Track("b", pickOrder = 1), Track("c", pickOrder = 2))
        assertEquals(listOf(0, 1, 2), m.playOrder())
        assertEquals(listOf(1, 2, 3), (0..2).map { m.rankOf(it) })
        assertEquals(listOf("b", "a", "c"), m.move(1, -1).tracks.map { it.source })
    }

    @Test fun picked_mode_follows_order_tracks_were_enabled() {
        var m = cfg(PlayMode.PICKED, Track("a"), Track("b"), Track("c"), Track("d"))
            .setTrackEnabled(0, false).setTrackEnabled(1, false).setTrackEnabled(2, false).setTrackEnabled(3, false)
        assertEquals(0, m.playableCount)
        m = m.setTrackEnabled(2, true).setTrackEnabled(0, true).setTrackEnabled(3, true)
        assertEquals(listOf(2, 0, 3), m.playOrder())               // c, a, d: dung thu tu bam chon
        assertEquals(listOf(2, 0, 1, 3), listOf(0, 1, 2, 3).map { m.rankOf(it) })   // a=2, b(tat)=0, c=1, d=3
        assertEquals(3, m.nextIndex(0) { 0 })
        assertEquals(2, m.nextIndex(3) { 0 })                      // het vong -> quay ve bai chon dau tien
        // tat roi bat lai bai a -> a xuong cuoi thu tu chon
        val again = m.setTrackEnabled(0, false).setTrackEnabled(0, true)
        assertEquals(listOf(2, 3, 0), again.playOrder())
    }

    @Test fun picked_mode_old_tracks_without_order_keep_list_order() {
        val m = cfg(PlayMode.PICKED, Track("a"), Track("b"), Track("c"))   // du lieu cu: pickOrder = 0 het
        assertEquals(listOf(0, 1, 2), m.playOrder())
        val m2 = m.addTracks(listOf(Track("d")))
        assertEquals(listOf(0, 1, 2, 3), m2.playOrder())
        assertTrue(m2.tracks[3].pickOrder > 0)
    }

    @Test fun shuffle_mode_only_picks_enabled_and_never_repeats_current() {
        val m = cfg(PlayMode.SHUFFLE, Track("a"), Track("b", enabled = false), Track("c"), Track("d"))
        for (cur in listOf(0, 2, 3)) {
            val seen = (0..1).map { r -> m.nextIndex(cur) { r } }.toSet()
            assertEquals(setOf(0, 2, 3) - cur, seen)
        }
        assertEquals(setOf(0, 2, 3), (0..2).map { r -> m.nextIndex(-1) { r } }.toSet())   // chua phat gi: chon trong ca 3 bai bat
        assertEquals(0, cfg(PlayMode.SHUFFLE, Track("a")).nextIndex(0) { 0 })
        assertEquals(0, m.rankOf(0))   // ngau nhien thi khong co thu hang
    }

    @Test fun current_track_disabled_mid_play_moves_on_sensibly() {
        val added = cfg(PlayMode.ADDED, Track("a"), Track("b", enabled = false), Track("c"))
        assertEquals(2, added.nextIndex(1) { 0 })      // dang phat b roi b bi tat -> bai bat ke sau trong danh sach
        val last = cfg(PlayMode.ADDED, Track("a"), Track("b"), Track("c", enabled = false))
        assertEquals(0, last.nextIndex(2) { 0 })       // khong con bai nao phia sau -> bai dau
        val picked = cfg(PlayMode.PICKED, Track("a", pickOrder = 2), Track("b", enabled = false), Track("c", pickOrder = 1))
        assertEquals(2, picked.nextIndex(1) { 0 })     // che do chon: ve bai chon dau tien
    }

    @Test fun setTrackEnabled_ignores_bad_index_and_no_ops() {
        val m = cfg(PlayMode.ADDED, Track("a"))
        assertSame(m, m.setTrackEnabled(5, false))
        assertSame(m, m.setTrackEnabled(-1, true))
        assertSame(m, m.setTrackEnabled(0, true))          // da bat
        assertSame(m, m.withMode(PlayMode.ADDED))
        assertEquals(PlayMode.SHUFFLE, m.withMode(PlayMode.SHUFFLE).mode)
        assertEquals(0L, m.setTrackEnabled(0, false).tracks[0].pickOrder)
    }
}
