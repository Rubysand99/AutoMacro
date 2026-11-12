package com.starclan.starhub.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mã nguồn thuần Kotlin lấy chữ qua [Texts] theo TÊN, nên gõ sai tên chỉ lộ ra lúc chạy trên máy: test này chặn trước. */
class TextsKeysTest {
    init { TestTexts.install() }

    private val prefixes = listOf("speed_", "farmgroup_", "farmcmd_", "cleanst_")

    private fun dataSources(): List<File> {
        val dir = listOf("src/main/java/com/starclan/starhub/data", "app/src/main/java/com/starclan/starhub/data")
            .map { File(it) }.firstOrNull { it.isDirectory } ?: error("Không thấy thư mục data/")
        return dir.listFiles { f -> f.extension == "kt" }!!.toList()
    }

    @Test fun every_name_used_in_data_sources_exists_in_strings_xml() {
        val re = Regex("\"((?:${prefixes.joinToString("|")})[a-z0-9_]+)\"")
        val used = dataSources().flatMap { f -> re.findAll(f.readText()).map { it.groupValues[1] to f.name }.toList() }
        assertTrue("không tìm thấy tên chuỗi nào, regex hỏng?", used.size > 30)
        val missing = used.filter { it.first !in TestTexts.names() }
        assertTrue("thiếu trong strings.xml: $missing", missing.isEmpty())
    }

    @Test fun no_unused_strings_with_these_prefixes() {
        // chuoi dung qua khoa ghep (farmcmd_<id>_label|shown) duoc tinh la da dung
        val code = dataSources().joinToString("\n") { it.readText() }
        val dynamic = FarmCmd.values().flatMap { listOf("farmcmd_${it.id}_label", "farmcmd_${it.id}_shown") }.toSet()
        val unused = TestTexts.names()
            .filter { n -> prefixes.any { n.startsWith(it) } }
            .filter { it !in dynamic && !code.contains("\"$it\"") }
        assertTrue("chuỗi không ai dùng: $unused", unused.isEmpty())
    }

    @Test fun enum_labels_come_from_strings_xml() {
        for (m in SpeedMode.values()) assertTrue(m.name, m.label.isNotBlank())
        for (g in FarmCmdGroup.values()) assertTrue(g.name, g.label.isNotBlank())
        for (c in FarmCmd.values()) {
            assertTrue("${c.name} label", c.label.isNotBlank())
            assertTrue("${c.name} shown", c.shown.isNotBlank())
        }
        // giu nguyen chu nguoi dung vao nhung chu truoc khi chuyen
        assertEquals("Nhanh", SpeedMode.FAST.label); assertEquals("Thường", SpeedMode.NORMAL.label); assertEquals("Chậm", SpeedMode.SLOW.label)
        assertEquals("Lệnh chính mỗi vòng", FarmCmdGroup.MAIN.label)
        assertEquals("owo weapon <ID>", FarmCmd.WEAPON.shown)
        assertEquals("Nâng cấp huntbot", FarmCmd.UPGRADE.label)
    }

    @Test fun placeholders_format_like_android() {
        assertEquals("Chờ NeonUtil gửi file và Tom lập kế hoạch… còn 7s", TestTexts.get("cleanst_wait_file_left", 7L))
        assertEquals("Đã dọn xong 5/5 bước. Gõ nw inv export lần nữa để kiểm tra kho còn lại.", TestTexts.get("cleanst_done", 5))
        assertFalse(TestTexts.get("cleanst_wait_next", 1, 2).endsWith(" "))
    }

    @Test fun missing_name_is_loud_in_tests() {
        val ok = try { TestTexts.get("khong_co_chuoi_nay"); false } catch (e: IllegalStateException) { true }
        assertTrue(ok)
    }
}
