package com.starclan.starhub.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OwoTrackerApi"

/** 1 lenh nhan duoc tu owo-tracker qua GET /commands/pending */
data class PendingCommand(
    val id: Int,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val status: String = "pending"
)

private data class PendingCommandsResponse(val commands: List<PendingCommand> = emptyList())

/** GET /alerts — canh bao nhe, goi thuong xuyen luc dang farm */
private data class CaptchaInfo(
    val active: Boolean? = null,
    /** So giay ke tu luc OwO bao captcha (bot tinh) - de dem nguoc 10 phut "thoi gian vang" */
    @SerializedName("age_seconds") val ageSeconds: Long? = null
)
private data class ActivityInfo(
    @SerializedName("last_reply_age_seconds") val lastReplyAgeSeconds: Long? = null,
    @SerializedName("last_command_age_seconds") val lastCommandAgeSeconds: Long? = null
)
private data class HuntbotAlertInfo(
    val hunting: Boolean? = null,
    @SerializedName("seconds_left") val secondsLeft: Long? = null,
    @SerializedName("ready_epoch") val readyEpoch: Long? = null
)
private data class AlertsResponse(
    val captcha: CaptchaInfo? = null,
    val activity: ActivityInfo? = null,
    val off: Boolean? = null,
    val huntbot: HuntbotAlertInfo? = null
)

/** Ket qua hoi bot: OwO co dang doi captcha khong, va OwO tra loi lenh gan nhat cach day bao lau */
data class AlertInfo(
    val captchaActive: Boolean,
    val lastReplyAgeSeconds: Long?,
    val lastCommandAgeSeconds: Long?,
    val off: Boolean = false,
    /** Huntbot dang san? con bao nhieu giay (BOT tinh)? gio ve (giay tu 1970) de nhan dien chuyen san */
    val huntbotHunting: Boolean = false,
    val huntbotSecondsLeft: Long? = null,
    val huntbotReadyEpoch: Long? = null,
    /** Captcha da bao cach day bao nhieu giay (null = khong biet / khong co) */
    val captchaAgeSeconds: Long? = null
)

/** Ket qua goi GET /hub — tach ro thanh cong / that bai de man hinh Hub bao loi cho de hieu. */
sealed class HubResult {
    data class Success(val hub: HubResponse) : HubResult()
    data class Failure(val message: String) : HubResult()
}

/**
 * Goi API nho cua owo-tracker (xem README/phan "Ket noi voi AutoMacro" ben
 * repo owo-tracker). Dung HttpURLConnection co san trong Android, khong can
 * them thu vien ngoai.
 */
object OwoTrackerApi {
    private val gson = Gson()

    /** Lay danh sach lenh dang cho. Loi mang -> tra ve danh sach rong, khong lam crash. */
    suspend fun fetchPending(apiUrl: String, token: String): List<PendingCommand> = withContext(Dispatchers.IO) {
        try {
            val url = URL(joinUrl(apiUrl, "/commands/pending"))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "fetchPending: HTTP $code")
                return@withContext emptyList()
            }
            val body = conn.inputStream.bufferedReader().readText()
            gson.fromJson(body, PendingCommandsResponse::class.java)?.commands ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "fetchPending loi: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lay du lieu tong hop cho man hinh Hub (GET /hub). Khac fetchPending o cho:
     * tra ve ro thanh cong hay that bai (kem cau bao loi bang tieng Viet) de
     * man hinh Hub hien cho Ruby biet sai o dau (sai token, chua cap nhat bot...).
     */
    suspend fun fetchHub(apiUrl: String, token: String): HubResult = withContext<HubResult>(Dispatchers.IO) {
        try {
            val url = URL(joinUrl(apiUrl, "/hub"))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000

            val code = conn.responseCode
            if (code == 401) {
                return@withContext HubResult.Failure("Token chưa đúng (401) — kiểm tra lại trong Kết nối owo-tracker")
            }
            if (code == 404) {
                return@withContext HubResult.Failure("Bot chưa có đường /hub (404) — cần cập nhật bot lên bản mới")
            }
            if (code != 200) {
                Log.w(TAG, "fetchHub: HTTP $code")
                return@withContext HubResult.Failure("Bot báo lỗi (HTTP $code)")
            }
            val body = conn.inputStream.bufferedReader().readText()
            if (body.isBlank()) {
                HubResult.Failure("Bot trả về dữ liệu trống")
            } else {
                HubResult.Success(HubParser.parse(body))
            }
        } catch (e: JsonParseException) {
            Log.w(TAG, "fetchHub doc JSON loi: ${e.message}")
            HubResult.Failure("Dữ liệu từ bot không đọc được (${(e.message ?: "lỗi không rõ").take(120)})")
        } catch (e: Exception) {
            Log.w(TAG, "fetchHub loi: ${e.message}")
            HubResult.Failure("Không kết nối được tới bot — kiểm tra mạng và địa chỉ API")
        }
    }

    /**
     * Hoi bot (GET /alerts). farmFlag: "1" = dang farm, "2" = dang nghi giai lao, "0" = vua dung farm,
     * null = chi hoi. Bot dung co nay de canh: mat ket noi dien thoai / OwO im lang thi nhan tin tag Ruby.
     * Tra ve null neu khong hoi duoc (mang loi / bot chua cap nhat).
     */
    suspend fun fetchAlerts(apiUrl: String, token: String, farmFlag: String?): AlertInfo? = withContext(Dispatchers.IO) {
        try {
            val query = if (farmFlag == null) "" else "?farm=$farmFlag"
            val url = URL(joinUrl(apiUrl, "/alerts$query"))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            if (conn.responseCode != 200) {
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().readText()
            val parsed = gson.fromJson(body, AlertsResponse::class.java) ?: return@withContext null
            AlertInfo(
                captchaActive = parsed.captcha?.active == true,
                lastReplyAgeSeconds = parsed.activity?.lastReplyAgeSeconds,
                lastCommandAgeSeconds = parsed.activity?.lastCommandAgeSeconds,
                off = parsed.off == true,
                huntbotHunting = parsed.huntbot?.hunting == true,
                huntbotSecondsLeft = parsed.huntbot?.secondsLeft,
                huntbotReadyEpoch = parsed.huntbot?.readyEpoch,
                captchaAgeSeconds = parsed.captcha?.ageSeconds
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchAlerts loi: ${e.message}")
            null
        }
    }

    /** Chi hoi captcha: true/false, null = khong hoi duoc */
    suspend fun fetchCaptchaActive(apiUrl: String, token: String): Boolean? =
        fetchAlerts(apiUrl, token, null)?.captchaActive

    /** Bot dang TAT app (lenh ,off): true/false, null = khong hoi duoc */
    suspend fun fetchAppOff(apiUrl: String, token: String): Boolean? =
        fetchAlerts(apiUrl, token, null)?.off

    /** Nho bot nhan tin tag Ruby tren Discord (de dien thoai/may khac cung bao) */
    suspend fun notify(apiUrl: String, token: String, text: String, toAlt: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val url = URL(joinUrl(apiUrl, "/notify"))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val body = mutableMapOf("text" to text)
            if (toAlt) body["target"] = "alt"
            val payload = gson.toJson(body)
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (e: Exception) {
            Log.w(TAG, "notify loi: ${e.message}")
        }
    }

    // ---- Man "Cong cu": cong tac / ghi chu / captcha (xem BotTools.kt) ----

    /** Goi 1 duong cua bot, tra ve noi dung JSON khi HTTP 200, null khi loi (mang / sai token / bot chua cap nhat). */
    private suspend fun callBot(apiUrl: String, token: String, method: String, path: String, body: String? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(joinUrl(apiUrl, path)).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 10_000
                    if (body != null) {
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.doOutput = true
                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "callBot $method $path loi: ${e.message}")
                null
            }
        }

    /** Bao bot cac lenh farm dang TAT (id nhu FarmCmd.id) de bot khoi xin app go xen nhung lenh check do. true = bot da nhan */
    suspend fun pushDisabledCommands(apiUrl: String, token: String, disabledIds: List<String>): Boolean =
        callBot(apiUrl, token, "POST", "/app-config", BotToolsJson.appConfigBody(disabledIds)) != null

    suspend fun fetchBotSettings(apiUrl: String, token: String): BotSettings? =
        callBot(apiUrl, token, "GET", "/settings")?.let { BotToolsJson.parseSettings(it) }

    /** Bat/tat 1 cong tac; tra ve cai dat moi nhat bot da luu, null neu loi */
    suspend fun setBotToggle(apiUrl: String, token: String, toggle: BotToggle, enabled: Boolean): BotSettings? =
        callBot(apiUrl, token, "POST", "/settings", BotToolsJson.toggleBody(toggle, enabled))?.let { BotToolsJson.parseSettings(it) }

    suspend fun fetchNotes(apiUrl: String, token: String): List<BotNote>? =
        callBot(apiUrl, token, "GET", "/notes")?.let { BotToolsJson.parseNotes(it) }

    suspend fun addNote(apiUrl: String, token: String, text: String): List<BotNote>? =
        callBot(apiUrl, token, "POST", "/notes", BotToolsJson.addNoteBody(text))?.let { BotToolsJson.parseNotes(it) }

    suspend fun markNoteDone(apiUrl: String, token: String, id: Int): List<BotNote>? =
        callBot(apiUrl, token, "POST", "/notes", BotToolsJson.doneNoteBody(id))?.let { BotToolsJson.parseNotes(it) }

    /** Bao bot da giai captcha xong (nhu `,captcha clear`). true = bot da nhan */
    suspend fun clearCaptcha(apiUrl: String, token: String): Boolean =
        callBot(apiUrl, token, "POST", "/captcha/clear", "{}") != null

    /** Nhu `,teamcheck`: cham doi hinh dang dung. null = loi mang / sai token / bot chua co duong /teamcheck */
    suspend fun fetchTeamCheck(apiUrl: String, token: String): BotTeamCheck? =
        callBot(apiUrl, token, "GET", "/teamcheck")?.let { BotToolsJson.parseTeamCheck(it) }

    /** Nhu `,zoodiff`: so 2 lan ghi zoo gan nhat. kind: "now" (dang giu) | "d" (so da bat) | null (loai ghi gan nhat). */
    suspend fun fetchZooDiff(apiUrl: String, token: String, kind: String? = null): BotZooDiff? =
        callBot(apiUrl, token, "GET", if (kind.isNullOrBlank()) "/zoodiff" else "/zoodiff?kind=$kind")
            ?.let { BotToolsJson.parseZooDiff(it) }

    /** Nhu `,unlocked`: pet da mo khoa theo bac (khong kem so luong). */
    suspend fun fetchUnlocked(apiUrl: String, token: String): BotUnlocked? =
        callBot(apiUrl, token, "GET", "/unlocked")?.let { BotToolsJson.parseUnlocked(it) }

    /** Nhu `,setalt` (xem): acc phu dang nhan DM nhac huntbot. */
    suspend fun fetchAlt(apiUrl: String, token: String): BotAlt? =
        callBot(apiUrl, token, "GET", "/alt")?.let { BotToolsJson.parseAlt(it) }

    /** Nhu `,setalt <id>` / `,setalt xoa` (userId rong = tat). Tra ve gia tri bot da luu; null neu loi (ke ca ID sai dinh dang). */
    suspend fun setAlt(apiUrl: String, token: String, userId: String): BotAlt? =
        callBot(apiUrl, token, "POST", "/alt", BotToolsJson.altBody(userId))?.let { BotToolsJson.parseAlt(it) }

    /** GET /clean: phien don kho cua bot (da lap ke hoach moi chua, xong may buoc, vi sao khong co buoc). null = khong hoi duoc / bot cu. */
    suspend fun fetchClean(apiUrl: String, token: String): BotClean? =
        callBot(apiUrl, token, "GET", "/clean")?.let { BotToolsJson.parseClean(it) }

    /** Nhu `,petset`: nhap tay 1 pet. null = khong goi duoc bot / bot tu choi (400) / bot chua co duong /manual/pet. */
    suspend fun saveManualPet(apiUrl: String, token: String, pet: ManualPet): BotManualResult? =
        callBot(apiUrl, token, "POST", ManualKind.PET.path, BotToolsJson.petBody(pet))?.let { BotToolsJson.parseManual(it) }

    /** Nhu `,weapontypeset` (kind = WEAPON_TYPE) / `,passiveset` (kind = PASSIVE): nhap tay ten + mo ta. kind = PET khong dung o day. */
    suspend fun saveManualDesc(apiUrl: String, token: String, kind: ManualKind, d: ManualDesc): BotManualResult? {
        require(kind != ManualKind.PET) { "pet co du lieu rieng, dung saveManualPet" }
        return callBot(apiUrl, token, "POST", kind.path, BotToolsJson.descBody(d))?.let { BotToolsJson.parseManual(it) }
    }

    /** Nhu `,testalt`: thu DM 1 tin vao acc phu. null = khong goi duoc bot. */
    suspend fun testAlt(apiUrl: String, token: String): BotAltTest? =
        callBot(apiUrl, token, "POST", "/alt/test", "{}")?.let { BotToolsJson.parseAltTest(it) }

    /** Bat/tat che do Event cua bot. Tra ve gia tri bot da luu, hoac null neu loi (mang / sai token). */
    suspend fun setEventMode(apiUrl: String, token: String, enabled: Boolean): Boolean? =
        withContext<Boolean?>(Dispatchers.IO) {
            try {
                val url = URL(joinUrl(apiUrl, "/event-mode"))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.outputStream.use { it.write("""{"enabled":$enabled}""".toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) enabled else null
            } catch (e: Exception) {
                Log.w(TAG, "setEventMode loi: ${e.message}")
                null
            }
        }

    /** Bam Giu ("keep") / Bo ("drop") cho cay vu khi dang duyet. Tra ve cay ke tiep, hoac null neu loi / khong co phien duyet. */
    suspend fun postWeaponReview(apiUrl: String, token: String, decision: String): ReviewDecisionResult? =
        withContext<ReviewDecisionResult?>(Dispatchers.IO) {
            try {
                if (decision != "keep" && decision != "drop" && decision != "unknown") return@withContext null
                val url = URL(joinUrl(apiUrl, "/weapon-review"))
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.doOutput = true
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 10_000
                    conn.outputStream.use { it.write("""{"decision":"$decision"}""".toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode != 200) return@withContext null
                    ReviewDecisionResult.parse(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "postWeaponReview loi: ${e.message}")
                null
            }
        }

    /** 3 danh sach ID vu khi (Giu / Bo / Khong xac dinh) de Ruby tu check tay. null = loi mang / bot chua cap nhat. */
    suspend fun fetchWeaponReviewLists(apiUrl: String, token: String): WeaponReviewLists? =
        callBot(apiUrl, token, "GET", "/weapon-review/lists")?.let { WeaponReviewLists.parse(it) }

    /** Bat tu duyet cac cay "Chua ro" (bot doc dong gon NeonUtil). Tra ve null neu OK, nguoc lai la thong bao loi. */
    suspend fun startAutoNeonReview(apiUrl: String, token: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(joinUrl(apiUrl, "/weapon-review/autoneon")).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.doOutput = true
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 15_000
                    conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode == 200) null
                    else {
                        val body = (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
                        com.google.gson.JsonParser.parseString(body).asJsonObject.get("error")?.asString ?: "lỗi ${conn.responseCode}"
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "startAutoNeonReview loi: ${e.message}")
                "mất kết nối bot"
            }
        }

    /** Dung duyet vu khi (an nut Giu/Bo/?). true = bot da nhan. */
    suspend fun stopWeaponReview(apiUrl: String, token: String): Boolean =
        callBot(apiUrl, token, "POST", "/weapon-review/stop", "{}") != null

    /** Chon 1 cay (ID) de kiem tra tay. true = bot da nhan va dat lam cay dang duyet. */
    suspend fun selectWeaponReview(apiUrl: String, token: String, id: String): Boolean {
        val safe = id.filter { it.isLetterOrDigit() }
        if (safe.isEmpty()) return false
        return callBot(apiUrl, token, "POST", "/weapon-review/select", """{"id":"$safe"}""") != null
    }

    /** Bao bot Hub noi BAT (on=true) / TAT (on=false): Hub tat = bot khong giao viec cho app (nhu `,off`). true = bot da nhan. */
    suspend fun setPower(apiUrl: String, token: String, on: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(joinUrl(apiUrl, "/power"))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.outputStream.use { it.write("""{"on":$on}""".toByteArray(Charsets.UTF_8)) }
                conn.responseCode == 200
            } catch (e: Exception) {
                Log.w(TAG, "setPower loi: ${e.message}")
                false
            }
        }

    /** GET /gemtier — tier gem duoc phep dung cho tung loai. Tra ve null neu loi (mang / sai token / bot chua cap nhat). */
    suspend fun fetchGemTier(apiUrl: String, token: String): GemTierConfig? =
        withContext<GemTierConfig?>(Dispatchers.IO) {
            try {
                val conn = URL(joinUrl(apiUrl, "/gemtier")).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                if (conn.responseCode != 200) {
                    Log.w(TAG, "fetchGemTier: HTTP ${conn.responseCode}")
                    return@withContext null
                }
                GemTierJson.parse(conn.inputStream.bufferedReader().readText())
            } catch (e: Exception) {
                Log.w(TAG, "fetchGemTier loi: ${e.message}")
                null
            }
        }

    /** POST /gemtier — luu tier duoc phep va/hoac uu tien cho cac loai da chinh. Tra ve cai dat bot vua luu, hoac null neu loi. */
    suspend fun saveGemTier(apiUrl: String, token: String, changes: GemTierChanges): GemTierConfig? =
        withContext<GemTierConfig?>(Dispatchers.IO) {
            try {
                val conn = URL(joinUrl(apiUrl, "/gemtier")).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.outputStream.use { it.write(GemTierJson.buildSaveBody(changes).toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode != 200) {
                    Log.w(TAG, "saveGemTier: HTTP ${conn.responseCode}")
                    return@withContext null
                }
                GemTierJson.parse(conn.inputStream.bufferedReader().readText())
            } catch (e: Exception) {
                Log.w(TAG, "saveGemTier loi: ${e.message}")
                null
            }
        }

    /** Gui chu loi khuyen vu khi (`?tag <vu khi>`) len bot; bot tach passive va gui file cho Ruby. true = bot da nhan. */
    suspend fun uploadWeaponAdvice(apiUrl: String, token: String, tags: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(joinUrl(apiUrl, "/weapon-advice"))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                val payload = gson.toJson(mapOf("tags" to tags))
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                conn.responseCode == 200
            } catch (e: Exception) {
                Log.w(TAG, "uploadWeaponAdvice loi: ${e.message}")
                false
            }
        }

    /** Bao owo-tracker biet 1 lenh da xu ly xong (hoac that bai), de no khong gui lai lenh do nua. */
    /**
     * Bao ket qua 1 lenh cho bot. Thu toi 3 lan (cach nhau 1s, 3s) neu loi mang / HTTP 5xx; loi 4xx (sai token, id la)
     * thi thu lai cung vo ich nen dung ngay. Tra ve true khi bot xac nhan (HTTP 2xx), false neu van that bai -
     * noi goi nho lenh da xu ly de lan poll sau khong go lai.
     */
    suspend fun ack(apiUrl: String, token: String, commandId: Int, status: String): Boolean = withContext(Dispatchers.IO) {
        val waitsMs = longArrayOf(0L, 1_000L, 3_000L)
        for ((i, waitMs) in waitsMs.withIndex()) {
            if (waitMs > 0) delay(waitMs)
            var conn: HttpURLConnection? = null
            try {
                val url = URL(joinUrl(apiUrl, "/commands/$commandId/ack"))
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.outputStream.use { it.write("""{"status":"$status"}""".toByteArray()) }
                val code = conn.responseCode // can doc de request thuc su duoc gui di
                if (code in 200..299) return@withContext true
                Log.w(TAG, "ack lenh $commandId: HTTP $code (lan ${i + 1}/${waitsMs.size})")
                if (code in 400..499) return@withContext false
            } catch (e: Exception) {
                Log.w(TAG, "ack loi (lan ${i + 1}/${waitsMs.size}): ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        false
    }

    private fun joinUrl(base: String, path: String): String = base.trimEnd('/') + path
}
