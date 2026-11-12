package com.starclan.starhub

import android.app.Application

/** Lớp Application: chỉ để nạp [Str] trước khi bất kỳ thành phần nào của app chạy. */
class StarHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Str.init(this)
    }
}
