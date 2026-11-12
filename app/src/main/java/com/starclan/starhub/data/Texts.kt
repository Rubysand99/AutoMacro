package com.starclan.starhub.data

/** Nguồn chuỗi giao diện theo TÊN (`name` trong res/values/strings.xml). Thuần Kotlin: không dính `R` / Android nên test JVM dùng được. */
interface TextSource {
    fun get(name: String, vararg args: Any?): String
}

/**
 * Cầu nối cho các object / enum thuần Kotlin (CleanupStatusText, FarmCmd, SpeedMode...) lấy chữ từ strings.xml
 * mà KHÔNG đổi chữ ký hàm (nơi gọi không phải truyền thêm gì).
 *
 * - App: `Str.init` (gọi trong StarHubApp.onCreate) gắn bản đọc resource thật.
 * - Test JVM: `TestTexts.install()` gắn bản đọc thẳng file strings.xml.
 * - Chưa gắn gì: trả lại chính TÊN chuỗi (thà hiện tên còn hơn làm sập dịch vụ truy cập).
 *
 * Đối số theo kiểu `%1$s` / `%1$d` như getString. Tên chuỗi mới nhớ thêm vào strings.xml (`TextsKeysTest` kiểm tra mọi tên trong mã nguồn đều có).
 */
object Texts {
    @Volatile
    var source: TextSource = object : TextSource {
        override fun get(name: String, vararg args: Any?): String = name
    }

    fun get(name: String, vararg args: Any?): String = source.get(name, *args)
}
