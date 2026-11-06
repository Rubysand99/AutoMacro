package com.starclan.starhub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.starclan.starhub.databinding.ActivityMainBinding

/**
 * Khung chinh: chi dieu khien 4 tab duoi cung, moi tab la 1 Fragment rieng.
 *  - navHub       -> HubFragment       (xem du lieu OwO tu owo-tracker)
 *  - navTools     -> ToolsFragment     (cong tac bot, tra cuu, ghi chu: cac lenh owo-tracker ngay tren app)
 *  - navConfig    -> ConfigFragment    (nhac nen, tier gem)
 *  - navSettings  -> SettingsFragment  (quyen Accessibility + ket noi owo-tracker)
 * (Tab "Tu dong" — danh sach kich ban AutoMacro — da bi go bo.)
 * Mac dinh mo tab Hub. Chuyen tab bang replace() nen moi Fragment tu khoi dong lai
 * (Hub tu lay du lieu moi) — dung y, khong phai loi.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.navHub -> HubFragment()
                R.id.navTools -> ToolsFragment()
                R.id.navConfig -> ConfigFragment()
                R.id.navSettings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.navHub
            requestNotificationPermissionIfNeeded()
        }
    }

    /** Android 13+ phai xin quyen moi hien duoc thong bao "huntbot con 10 phut nua ve". */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1001
    }
}
