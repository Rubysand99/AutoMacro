package com.starclan.starhub

import android.content.Context
import androidx.annotation.StringRes

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
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val c = app ?: return ""
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }
}
