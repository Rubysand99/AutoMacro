package com.starclan.starhub.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Nguồn chuỗi cho test JVM: đọc THẲNG res/values/strings.xml (không cần Android / `R`), nên test vẫn so khớp đúng chữ người dùng thấy.
 * Dùng: `TestTexts.install()` ở đầu mỗi lớp test có đụng tới chữ (gắn vào [Texts]).
 * Quy tắc theo Android: `getString(id)` không đối số trả nguyên chuỗi, có đối số thì định dạng kiểu `%1$s`.
 */
object TestTexts : TextSource {
    private val strings: Map<String, String> by lazy { load() }

    fun install() {
        Texts.source = this
    }

    /** Tên mọi chuỗi có trong strings.xml (để test kiểm tra tên dùng trong mã nguồn). */
    fun names(): Set<String> = strings.keys

    override fun get(name: String, vararg args: Any?): String {
        val raw = strings[name] ?: error("Thiếu chuỗi \"$name\" trong res/values/strings.xml")
        return if (args.isEmpty()) raw else String.format(raw, *args)
    }

    private fun load(): Map<String, String> {
        val file = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map { File(it) }.firstOrNull { it.isFile }
            ?: error("Không thấy strings.xml (chạy test từ thư mục app/ hoặc gốc repo)")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            out[n.attributes.getNamedItem("name").nodeValue] = androidUnescape(n.textContent)
        }
        return out
    }

    /** Bỏ cặp nháy kép bao ngoài + xử lý `\n` `\'` `\"` `\\` `\@` `\?` `\uXXXX` như aapt. */
    private fun androidUnescape(text: String): String {
        var s = text.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val d = s[i + 1]
                when (d) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6
                    }
                    else -> { sb.append(d); i += 2 }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
