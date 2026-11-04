package com.starclan.starhub.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** UiTheme (Hub noi) phai khop tung mau voi res/values/colors.xml (man hinh XML) - mot nguon su that, hai ban sao. */
class UiThemeColorsTest {
    private fun colorsXml(): String {
        val candidates = listOf("src/main/res/values/colors.xml", "app/src/main/res/values/colors.xml")
        val f = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("Khong thay colors.xml tu thu muc ${File(".").absolutePath}")
        return f.readText()
    }

    @Test fun ui_theme_matches_colors_xml() {
        val xml = colorsXml()
        for ((name, hex) in UiTheme.ALL) {
            val m = Regex("<color name=\"" + Regex.escape(name) + "\">(#[0-9A-Fa-f]{6,8})</color>").find(xml)
            assertTrue("colors.xml thieu mau '$name' (hoac khong phai ma #hex)", m != null)
            assertEquals("Mau '$name' lech giua UiTheme va colors.xml", hex.uppercase(), m!!.groupValues[1].uppercase())
        }
    }

    @Test fun all_hex_values_are_valid() {
        for ((name, hex) in UiTheme.ALL) assertTrue("$name: $hex", Regex("^#[0-9A-Fa-f]{6}$").matches(hex))
    }

    /** Chu phai doc duoc tren nen the / nen app (WCAG AA 4.5:1 cho chu thuong) */
    @Test fun text_colors_are_readable_on_cards() {
        fun lum(hex: String): Double {
            fun ch(i: Int): Double {
                val v = hex.substring(1 + i * 2, 3 + i * 2).toInt(16) / 255.0
                return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * ch(0) + 0.7152 * ch(1) + 0.0722 * ch(2)
        }
        fun ratio(a: String, b: String): Double {
            val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
            return (hi + 0.05) / (lo + 0.05)
        }
        for (fg in listOf(UiTheme.TEXT_PRIMARY, UiTheme.TEXT_SECONDARY, UiTheme.TEXT_MUTED, UiTheme.OK, UiTheme.INFO, UiTheme.WARN, UiTheme.BAD, UiTheme.SELECTED)) {
            for (bg in listOf(UiTheme.BG, UiTheme.SURFACE, UiTheme.RAISED)) {
                assertTrue("$fg tren $bg chi ${"%.2f".format(ratio(fg, bg))}:1", ratio(fg, bg) >= 4.5)
            }
        }
        assertTrue(ratio(UiTheme.ACCENT_ON, UiTheme.ACCENT) >= 4.5)
        assertTrue(ratio(UiTheme.BAD, UiTheme.BAD_TINT) >= 4.5)
        assertTrue(ratio(UiTheme.WARN, UiTheme.WARN_TINT) >= 4.5)
    }
}
