package com.starclan.starhub

import android.app.Application
import android.util.Log
import com.starclan.starhub.data.LegacyMusicCleanup
import kotlin.concurrent.thread

/** Lớp Application: nạp [Str] trước khi bất kỳ thành phần nào của app chạy, và dọn dữ liệu cũ ở luồng nền. */
class StarHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Str.init(this)
        thread(name = "legacy-cleanup", isDaemon = true) {
            try {
                LegacyMusicCleanup.run(noBackupFilesDir, filesDir)
                deleteSharedPreferences(LegacyMusicCleanup.ENGINE_NAME)
            } catch (e: Exception) {
                Log.w("StarHubApp", "Khong don duoc du lieu nhac cu (se thu lai lan mo sau): ${e.message}")
            }
        }
    }
}
