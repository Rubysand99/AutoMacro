package com.starclan.starhub.engine

import android.util.Log
import com.starclan.starhub.data.CommandGuard
import com.starclan.starhub.service.AutoAccessibilityService
import kotlinx.coroutines.delay

/**
 * Cong tac "Don kho tu dong" o Hub (mac dinh TAT, tu tat khi dong Hub / khi co loi). Chi khi BAT thi app moi lam cac lenh
 * `clean_*` cua bot; TAT thi de nguyen cho (het han tu huy).
 */
object CleanupGate {
    @Volatile
    var enabled: Boolean = false
}

/**
 * Lam MOT buoc don kho SAU KHI lenh da duoc go vao Discord (service goi sau DiscordSender.send):
 *  - star: bam nut sao (nhan "star_outline_dim") tren embed `owo w <ID>`; da sao (star_yellow) thi coi nhu xong.
 *  - tier / id: doi tin xac nhan cua OwO; doc chu tren man hinh, CHI bam "Dismantle" khi CommandGuard.confirmMatches dung
 *    (so cay khop so bot tinh / dung ID). Lech hoac khong doc duoc -> bam "Cancel" va bao that bai (bot dung ca phien).
 */
object CleanupRunner {
    private const val TAG = "CleanupRunner"

    suspend fun run(service: AutoAccessibilityService, mode: String, params: Map<String, String>): Boolean {
        delay(3_500)   // doi OwO tra loi
        return when (mode) {
            "star" -> star(service)
            "tier", "id" -> dismantle(service, mode, params)
            else -> false
        }
    }

    private suspend fun star(service: AutoAccessibilityService): Boolean {
        if (ButtonClicker.has(service, "star_outline_dim")) {
            if (!ButtonClicker.clickOnce(service, "star_outline_dim")) return false
            delay(1_500)
            return true
        }
        // Khong co nut sao chua danh dau: da sao san thi OK, khong thay gi ca thi that bai
        return ButtonClicker.has(service, "star_yellow")
    }

    private suspend fun dismantle(service: AutoAccessibilityService, mode: String, params: Map<String, String>): Boolean {
        var waited = 0
        while (!ButtonClicker.has(service, "dismantle") && waited < 6_000) { delay(1_000); waited += 1_000 }
        if (!ButtonClicker.has(service, "dismantle")) {
            // Khong co gi de ra (bac trong, cay da het) -> bo qua neu bot danh dau optional
            return params["optional"] == "1"
        }
        val text = ButtonClicker.textAbove(service, "dismantle")
        val ok = CommandGuard.confirmMatches(
            mode, text, params["expect"]?.toIntOrNull(), params["expect_max"]?.toIntOrNull(), params["expect_id"]
        )
        if (!ok) {
            Log.w(TAG, "Tin xac nhan khong khop (mode=$mode) - bam Cancel")
            ButtonClicker.clickOnce(service, "cancel")
            return false
        }
        if (!ButtonClicker.clickOnce(service, "dismantle")) return false
        delay(2_500)
        return true
    }
}
