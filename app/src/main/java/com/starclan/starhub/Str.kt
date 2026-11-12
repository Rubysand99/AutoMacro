package com.starclan.starhub

import android.content.Context
import androidx.annotation.StringRes
import com.starclan.starhub.data.TextSource
import com.starclan.starhub.data.Texts

/**
 * Lấy chuỗi từ res/values/strings.xml ở những nơi KHÔNG có sẵn Context (các `object` engine, lambda cập nhật trạng thái...).
 * Được khởi tạo 1 lần trong [StarHubApp.onCreate], trước mọi Activity / Service. Chưa khởi tạo thì trả chuỗi rỗng
 * (thà thiếu chữ còn hơn làm sập dịch vụ truy cập). Chỗ nào đã có Context (Fragment, View) vẫn nên dùng thẳng getString.
 * Đối số có thể null (in ra "null" như nội suy chuỗi của Kotlin).
 */
object Str {
    @Volatile
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        // Cho các object thuần Kotlin (data/*) lấy chữ theo TÊN chuỗi, xem data/Texts.kt
        Texts.source = ByName
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val c = app ?: return ""
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }

    /**
     * Tra chuỗi theo tên cho code thuần Kotlin (không dùng được `R`). Không tìm thấy tên -> trả lại chính tên đó (không sập).
     * Cần giữ chuỗi trong APK: build không bật shrinkResources (nếu bật phải thêm res/raw/keep.xml giữ `@string/cleanst_*`, `farmcmd_*`...).
     */
    private object ByName : TextSource {
        override fun get(name: String, vararg args: Any?): String {
            val c = app ?: return name
            val id = c.resources.getIdentifier(name, "string", c.packageName)
            if (id == 0) return name
            return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
        }
    }
}
