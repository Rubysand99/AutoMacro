# AI_CONTEXT.md — Star Hub (trước đây: AutoMacro / TuyTam)

> File này chỉ giữ **trạng thái hiện tại + quy ước bắt buộc**. Nhật ký từng phiên bản (v15 → v39, kèm chi tiết thiết kế và
> các ghi chú đã lỗi thời) nằm ở [`docs/LICH_SU.md`](docs/LICH_SU.md). Bot nằm ở repo riêng `Rubysand99/owo-tracker`
> (có `README.md` mô tả API đầy đủ). Tài liệu viết tiếng Việt không dấu ở các mục cũ là do lịch sử, mục mới nên viết có dấu.


## Du an la gi
App Android tu dong hoa thao tac tren may (bam, vuot, go chu, mo app) - kieu
Tasker/MacroDroid. Muc dich chinh: ho tro choi OwO Bot tren Discord.
Nguoi dung chi quan sat, app tu thuc hien cac buoc theo "kich ban" da tao.
Repo GitHub: Rubysand99/AutoMacro (https://github.com/Rubysand99/AutoMacro).

Dang ket hop voi du an rieng "owo-tracker" (bot Discord theo doi choi OwO,
repo Python/discord.py rieng) de tao 1 he thong tap trung: owo-tracker la
"bo nao" (biet luc nao can lam gi), AutoMacro la "tay chan" (thuc thi tren
dien thoai). Xem muc "Ket noi voi owo-tracker" ben duoi.

## Quy ước BẮT BUỘC khi sửa code (rút từ lịch sử)
**Hai phía bot ↔ app phải đi cùng nhau**
- Bot (owo-tracker) thêm lệnh mới cho app gõ → thêm 1 dòng vào danh sách cho phép của `data/CommandGuard.kt` + 1 test ở `CommandGuardTest`,
  nếu không app từ chối lệnh (ack `failed`). `CommandGuard` luôn chặn epic/mythic/legendary/fabled/`all` khi dismantle.
- Luật kiểm tra nhập tay pet / loại vũ khí / passive có **2 bản**: `tools_api.py` (bot) và `ManualInput` trong `data/BotTools.kt` (app). Sửa 1 nơi thì sửa nơi kia.
- Đổi API bot → cập nhật README của bot; thêm/đổi lệnh Discord → cập nhật `HELP_GROUPS` (`test_help.py` sẽ báo nếu quên).
- Bot cũ chưa có đường API mới → app phải hiện thông báo "cập nhật bot", không báo lỗi khó hiểu.

**Chuỗi chữ**
- Chữ hiển thị đặt ở `res/values/strings.xml`. Code Android dùng `Str.get(R.string.x, ...)` hoặc `getString`; object thuần Kotlin (test JVM không dùng được `R`) dùng `Texts.get("ten", ...)` theo TÊN chuỗi.
- Không dùng `Str.get` trong `const val` (lỗi biên dịch). Chuỗi không để khoảng trắng đầu/cuối (Android cắt bỏ) — nối bằng code.
- Tiền tố tên chuỗi của `data/*.kt` phải có trong danh sách `prefixes` của `TextsKeysTest`. Thêm lệnh farm mới thì thêm 2 chuỗi `farmcmd_<id>_label` và `farmcmd_<id>_shown`.
- Nếu sau này bật `shrinkResources`: giữ chuỗi truy cập theo tên bằng `res/raw/keep.xml`. Nếu bật R8/minify: giữ lớp `data.Hub*` (Gson).

**Dữ liệu & giao diện**
- Không đổi `id` của `FarmCmd` đã dùng (cấu hình lưu theo id, chỉ lưu tập lệnh đang TẮT nên lệnh mới mặc định BẬT).
- Đổi màu thì sửa CẢ `res/values/colors.xml` và `view/UiTheme.kt` (`UiThemeColorsTest` đối chiếu + kiểm tra tương phản 4.5:1). Nhãn vàng `accent` chỉ cho nút chính (1 nút/màn hình).
- Icon cho overlay (Service không có theme) KHÔNG dùng `?attr/...`.
- Số có thể rất lớn (essence...) → dùng `Long`, không dùng `Int`.

**Build, phiên bản, ký APK**
- Môi trường làm việc của AI thường KHÔNG có Android SDK: chỉ biên dịch + chạy JUnit được phần thuần Kotlin (`data/*`). Phần còn lại phải xem kết quả GitHub Actions (`build.yml`; bước unit test không còn `continue-on-error`).
- `VERSION` do workflow tự tăng bằng commit `chore(version)` sau mỗi push lên `main` → **luôn `git pull` trước khi push**. Muốn lên số lớn thì sửa tay `VERSION`.
- APK ký bằng keystore riêng qua GitHub Secrets (`STARHUB_KEYSTORE_BASE64`, `STARHUB_KEYSTORE_PASSWORD`; xem README). Không bao giờ commit `.jks`. Đổi khóa = Android coi là app khác.
- Quy ước làm việc của chủ repo: commit và push thẳng vào `main`, không cần nhánh phụ.

**An toàn khi chạy**
- Dịch vụ Accessibility chỉ nhận sự kiện của `com.discord`. Lệnh từ bot chỉ được gõ nếu qua `CommandGuard` (1 dòng `owo ...` ≤ 200 ký tự, danh sách cho phép theo động từ).
- Rủi ro cần nhớ: OwO có hệ thống chống macro (captcha/nghi ngờ) và tự động hóa có thể vi phạm điều khoản Discord → giữ độ trễ ngẫu nhiên (jitter) khi làm phần hunt/battle; giữ nguyên giới hạn burst (3 lệnh/12 giây) và `CommandGuard`.
- Việc chưa kiểm chứng trên máy thật được ghi ở từng mục trong `docs/LICH_SU.md` (tìm "CHUA BIEN DICH" / "Chua thu tren may that").


## Ket noi voi owo-tracker (repo rieng)
- owo-tracker (Python, chay Railway) co API rieng: GET /commands/pending,
  POST /commands/{id}/ack, GET /hub (du lieu cho man hinh Hub), xac thuc bang header Authorization: Bearer <token>.
  Token nay KHAC MONGODB_URI - dien thoai khong bao gio cam chuoi ket noi Mongo that.
- Ca 2 phia phai dat CUNG 1 token: owo-tracker qua bien moi truong AUTOMACRO_API_TOKEN,
  AutoMacro qua man hinh Cai dat trong app (luu SharedPreferences, khong phai Room).
- AutoMacro chi can biet 2 toa do (o nhap tin nhan + nut Gui Discord), cai 1 lan qua
  nut "📍 Cai vi tri..." trong man hinh Cai dat. TU DONG dung cho MOI lenh owo-tracker
  gui toi sau nay (khong can tao/dat ten kich ban rieng cho tung loai lenh nua).
  Lenh tu server chi can co field "command_text" trong params la chay duoc.
- Case dau tien da lam: "swap_gem" - owo-tracker tu phat hien gem het do ben, ghi lenh
  kem item_code/gem_tier/command_text da chon san (tier thap nhat con lai).

## Cong nghe
- Kotlin, Android SDK (minSdk 26, targetSdk/compileSdk 34)
- AccessibilityService: dispatchGesture (bam/vuot theo toa do), performAction ACTION_CLICK
  va ACTION_SET_TEXT (bam/go truc tiep vao node), findAccessibilityNodeInfosByText/ByViewId,
  findFocus(FOCUS_INPUT) cho buoc go chu
- TYPE_ACCESSIBILITY_OVERLAY (WindowManager) cho man hinh chon diem (wizard cai vi tri Discord) + Hub noi
- Gson doc JSON tu owo-tracker; EncryptedSharedPreferences luu token
- Kotlin Coroutines cho farm, gui lenh va vong lap dong bo owo-tracker
- HttpURLConnection (co san trong Android, khong them thu vien) de goi API owo-tracker
- Build qua GitHub Actions (.github/workflows/build.yml) — chay `gradle assembleDebug`

## Cấu trúc thư mục chính
`app/src/main/java/com/starclan/starhub/`
- `MainActivity.kt` — khung chứa, 4 tab dưới (BottomNavigationView, mỗi lần đổi tab là `replace()` Fragment mới): **Hub** (`HubFragment`) · **Công cụ** (`ToolsFragment`) · **Config** (`ConfigFragment`) · **Cài đặt** (`SettingsFragment`). Mặc định mở Hub.
- `StarHubApp.kt` (khởi tạo `Str`), `Str.kt`, `GemTierDialog.kt`.
- `service/AutoAccessibilityService.kt` — đồng bộ owo-tracker (poll 15 giây), gõ lệnh vào Discord, nhắc huntbot, wizard cài vị trí ô nhập + nút Gửi.
- `engine/` — `FarmController`, `CleanupRunner`, `DiscordSender`, `NodeTools`, `ButtonClicker`, `TagCollector`, `QuestClaimer`, `HuntbotNotifier`, `MusicPlayer`, `AlarmPlayer`, `NodeDump`.
- `data/` — `OwoTrackerApi`, `OwoTrackerPrefs`, `CommandGuard`, `Hub*` (model/parser/format/query/search/summary/icons), `BotTools`, `GemTierConfig`, `SpeedMode`, `FarmCommandConfig`, `FarmStats(+Store)`, `CleanupStatus`, `AppConfig`, `ConfigPrefs`, `Texts`, `CountdownFormat`, `HuntbotAlertLogic`, `ApiUrlCheck`. **Các file này thuần Kotlin, có test JVM.**
- `view/` — `FarmOverlay` (Hub nổi, 4 tab Farm/Setting/Misc/Event), `UiTheme`, `RingView`, `DayBarsView`, `BarChartView`, `WaitCountdownView`, `IconView`, `EmojiImageLoader`, `AsyncLoadCache`.
- Chức năng đã gỡ (không còn trong code): kịch bản + Room + chế độ ghi (v24), tab "Tự động" (v16), keystore debug công khai (v27).


## Ghi chu quan trong
- Nguoi dung phai tu vao Cai dat may bat quyen Accessibility (khong the tu bat bang code)
- Moi lan cai de APK moi tu build, Android co the khoa lai "Cai dat han che" - phai vao
  Thong tin ung dung -> menu 3 cham (hoac tim "cai dat han che" trong Cai dat) -> Cho phep
  cai dat han che -> roi moi bat lai duoc quyen Accessibility
- Neu dinh dang Google Play sau nay: can khai bao ro muc dich dung quyen Accessibility
  (app da bo QUERY_ALL_PACKAGES o v23), Google xet kha ky quyen Accessibility — dung rieng/cai APK thu cong
  thi khong van de gi
- RUI RO CAN NHO: OwO Bot co he thong chong macro (bat sus/captcha), tu dong choi ho
  co the vi pham dieu khoan Discord - can than khi mo rong sang hunt/battle tu dong,
  nen them do tre ngau nhien (jitter) thay vi canh dung giay khi lam phan do.

## Nhắc huntbot + tự cập nhật khi đang farm (mới)
- Bot (owo-tracker `auto_refresh.py` + vòng nền `auto_refresh_loop`): khi app ĐANG FARM (`/alerts?farm=1`), bot xếp lệnh
  `auto_check` (params: `command_text`, `kind`) cho mục dữ liệu đã cũ — app chạy nó như mọi lệnh có `command_text` (chen giữa
  lúc farm, giống đổi gem). Không farm / `,off` / captcha / Slow down gần đây / còn lệnh chờ -> không xếp. `,autocheck on|off`.
- `/alerts` trả thêm `huntbot: {hunting, seconds_left, ready_epoch}` (bot tính, app không so đồng hồ máy).
  `AutoAccessibilityService` hỏi mỗi 60 giây (kể cả Hub nổi tắt) -> `HuntbotNotifier` bắn thông báo khi còn 60..600 giây,
  mỗi chuyến 1 lần (nhớ `ready_epoch` đã báo trong SharedPreferences, lệch < 30 phút = cùng chuyến).
- Logic quyết định nằm ở `HuntbotAlertLogic.kt` (thuần, có test) và khớp `huntbot_alert_due()` bên bot.

## Hub nổi: đếm ngược, chuông captcha, bật/tắt = ON/OFF (mới)
- `FarmState.waitEndMs/waitTotalMs/waitLabel`: đặt trong `restFor`, `awaitBurstSlot`, backoff lỗi và chờ cooldown cuối vòng
  (`waitWithCountdown`). `FarmState.captchaEndMs` = hạn 10 phút giải captcha (bot trả `captcha.age_seconds` trong /alerts).
  `WaitCountdownView` (view/) vẽ chữ + thanh tiến độ, cập nhật mỗi 0,1 giây; `FarmOverlay.render` chọn captcha > chờ;
  bong bóng hiện đồng hồ qua `onTick`. Định dạng ở `CountdownFormat` (thuần, có test).
- `AlarmPlayer` reo cho đến khi `stopAlarm()` (không còn tự tắt sau 2 phút).
- `FLAG_KEEP_SCREEN_ON` đặt ngay khi tạo cửa sổ Hub nổi (không còn phụ thuộc đang farm).
- (OFF = chỉ ngừng giao việc cho app, bot vẫn theo dõi bình thường) `FarmOverlay.show()/hide()` gọi `FarmController.reportHubPower(on)` -> `POST /power {"on": bool}` -> bot `set_app_off(!on)`.
- Bot: `tracked_guild_id` (`,setserver`) thay `tracked_channel_id` (`,setchannel`); gem `[0/xx]` đặt lệnh đổi gem ngay.

## Sau audit bao mat (11/2026)
- `data/CommandGuard.kt` (thuan Kotlin, co test): chi cho go lenh `owo ...` 1 dong <= 200 ky tu; ep `click_times` 1..100 va khoang
  nghi 0.8..10 giay. `AutoAccessibilityService.pollOwoTrackerOnce` goi no truoc khi go; lenh sai bi ack `failed`.
- Token API luu bang EncryptedSharedPreferences (`OwoTrackerPrefs`, dependency `androidx.security:security-crypto`), tu chuyen
  token cu o kho thuong. CHUA BIEN DICH tren may: neu Actions bao loi o OwoTrackerPrefs thi gui log de sua.
- Workflow build: buoc unit test khong con `continue-on-error`.
