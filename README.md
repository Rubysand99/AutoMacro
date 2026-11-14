# Star Hub (AutoMacro)

App Android tự động hóa thao tác trên máy (bấm, vuốt, gõ chữ, mở app) kiểu Tasker/MacroDroid, ghép với
bot [owo-tracker](https://github.com/Rubysand99/owo-tracker): owo-tracker là "bộ não" (biết lúc nào cần làm gì),
Star Hub là "tay chân" (thực thi trên điện thoại) và có màn hình **Hub** hiển thị dữ liệu bot đọc được.

> Tên app: **Star Hub** — tên gói `com.starclan.starhub` (trước đây là `com.tuytam.automacro`).
> Trạng thái hiện tại + quy ước khi sửa code: `AI_CONTEXT.md`. Lịch sử thay đổi từng phiên bản: `docs/LICH_SU.md`.

## Build APK
Push lên nhánh `main` trên GitHub, tab **Actions** tự build và cho tải APK debug ở phần **Artifacts** của workflow
`Build APK` (artifact `starhub-debug-apk`). Không cần gradlew, không cần PC.
Sau bước build, workflow chạy thêm unit test JVM (`gradle testDebugUnitTest`); test đỏ thì workflow dừng và không tăng số phiên bản.

## Chọn tier gem được phép dùng
Tab **Config** → khối "Gem" → **Chỉnh tier gem**: mỗi loại gem (Hunting / Empowering / Lucky / Special) có các chip
bật/tắt từng tier; chip tắt thì bot không bao giờ nhờ app dùng tier đó (kèm số gem còn trong kho). Dưới chip có ô **Ưu tiên khi thay**
(tier cao nhất / thấp nhất còn viên, hoặc 1 tier cụ thể; hết tier đó thì lấy tier thấp hơn gần nhất đang bật). Bấm **Lưu** để gửi lên bot (cùng
nguồn với lệnh Discord `,gemtier tat|bat`). Cần bot bản mới có đường `/gemtier`.

## Số phiên bản (tự tăng)
File `VERSION` (vd `1.12`) tự tăng 1 sau mỗi lần cập nhật lên `main` (build + test xanh), APK mang đúng số mới. Xem ở tab **Cài đặt**
(cuối trang: "Star Hub v1.12"); tab **Hub** hiện thêm số phiên bản của bot (vd "bot v1.5"). Muốn lên số lớn: sửa `VERSION` thành `2.0`
rồi push. Push tay thì nhớ `git pull` trước vì workflow vừa thêm 1 commit.

## Cài APK và bật quyền
1. Cài file APK tải từ Artifacts. Vì đổi tên gói nên đây là app **mới**: gỡ bản cũ bằng tay, nhập lại địa chỉ API/token.
2. Mở app → tab **Cài đặt** → "Bật quyền Accessibility" → tìm **Star Hub** trong danh sách → bật.
3. Nhập **Địa chỉ API** (phải là `https://...`, Android chặn `http://`) và **token** giống `AUTOMACRO_API_TOKEN` của bot, rồi bấm Lưu.

## Thông báo huntbot
Khi huntbot còn khoảng 10 phút nữa là về, app hiện thông báo trên điện thoại (mỗi chuyến 1 lần) kèm **giờ về** và
**đồng hồ đếm ngược** tự chạy, nên mở máy muộn vẫn thấy số còn lại đúng. Service Accessibility hỏi bot
mỗi phút nên **không cần mở app hay bật Hub nổi**, chỉ cần đã bật "đồng bộ owo-tracker" trong Cài đặt. Lần đầu mở app trên
Android 13+ sẽ hỏi quyền thông báo — hãy bấm Cho phép. Giờ huntbot do bot tính nên không phụ thuộc đồng hồ điện thoại.

## Hub nổi
- **Luôn sáng màn hình:** Hub nổi đang bật thì màn hình không bao giờ tự tắt.
- **Đếm ngược khi chờ:** khi app nghỉ / giãn nhịp / chờ cooldown, panel hiện "lý do · m:ss" kèm thanh chạy về 0, và bong bóng
  ⭐ đổi thành đồng hồ (thấy được cả khi menu đang thu gọn) để biết app không bị đứng.
- **Captcha:** hiện đồng hồ đỏ 10 phút "thời gian vàng" tính từ lúc OwO báo; chuông **reo mãi cho đến khi bấm "Tắt chuông"**
  trên Hub. Giải xong gõ `,captcha clear` thì đồng hồ tự biến mất.
- **Bật Hub = bot ON, tắt Hub = bot OFF** (app tự báo cho bot; bot ngừng giao việc khi Hub tắt).

## Tab "Config" (nhạc nền + gem)
- **Nhạc nền**: bật công tắc, bấm **Chọn file nhạc** (mp3/…), chỉnh âm lượng, bấm **Nghe thử** để kiểm tra. Bắt đầu farm thì nhạc tự phát lặp lại, dừng farm (kể cả khi gặp captcha) thì nhạc tự tắt; chuông báo captcha vẫn kêu riêng.
- **Gem**: nút chỉnh tier gem (xem mục trên).
- Mọi lựa chọn ở tab này lưu ngay trên máy, không cần bấm Lưu.

## Tab "Công cụ"
(Các công tắc bot và chữ tìm kiếm được nhớ trên máy: mở tab là thấy lại ngay lựa chọn lần trước, rồi app hỏi bot cập nhật.)

Đưa các lệnh của owo-tracker lên app, khỏi phải gõ trong Discord (cần bot bản mới có `/settings`, `/notes`, `/captcha/clear`):
- **Công tắc bot:** tự chen lệnh check (`,autocheck`), tự chạy lại huntbot (`,hbauto`), nhắc huntbot qua DM (`,hbalert`), chế độ Event (`,eventmode`),
  cho bot giao việc cho app (`,off`). Khi OwO đang đòi captcha hiện khung đỏ + nút **Đã giải captcha xong** (`,captcha clear`).
- **Nhập tay:** khi bot chưa tự ghi nhận được từ `owo dex` / `owo weapon`, chọn **Pet** / **Loại vũ khí** / **Passive**, điền ô rồi bấm **Lưu vào bot**
  (như `,petset` `,weapontypeset` `,passiveset`). Pet có rank + 6 chỉ số HP/ATT/PR/WP/MAG/MR (số nguyên 0–100000); hai loại còn lại có tên + mô tả.
  App kiểm tra ngay trên máy (cùng luật với bot) và nói rõ ô nào sai; nhập lại cùng tên thì ghi đè bản cũ. Cần bot bản mới có đường `/manual/*`.
- **Tra cứu:** gõ tên (không cần dấu) để tìm trong zoo, dex loài, kho đồ, vũ khí + passive — như `,zoo` `,pets` `,inv` `,myweapons` nhưng ngay trên dữ liệu Hub đã tải.
- **Ghi chú:** thêm / đánh dấu xong, đồng bộ với `,note` của bot.

## Lưu ý
- APK được ký bằng **keystore riêng** (xem mục "Ký APK" bên dưới) để các bản build cài đè lên nhau được. Chưa cài keystore riêng
  thì build báo lỗi (không còn ký bằng khóa công khai).
- Service Accessibility chỉ nhận sự kiện của Discord (`packageNames="com.discord"`); việc đọc màn hình khi chạy đều đọc chủ động
  qua `rootInActiveWindow`. Chế độ ghi / chạy kịch bản cũ đã được gỡ bỏ (v24), app chỉ còn Hub nổi + farm + lệnh từ owo-tracker.

## Bảo mật
- **Token API** lưu bằng `EncryptedSharedPreferences` (khóa trong Android Keystore); token cũ ở kho thường tự được chuyển sang
  và xóa khỏi kho cũ ngay lần mở app kế tiếp.
- **Lọc lệnh từ bot:** app chỉ gõ vào Discord những lệnh dạng `owo ...` (1 dòng, tối đa 200 ký tự). Nội dung khác từ server
  bị từ chối và báo `failed`; số lần bấm lặp và khoảng nghỉ cũng bị ép vào mức an toàn (`CommandGuard.kt`).
- **Quyền Accessibility giới hạn trong Discord:** dịch vụ chỉ nhận sự kiện / đọc nội dung màn hình của `com.discord` (cần để tìm
  ô nhập/nút Gửi). Chỉ bật khi bạn tin bản APK này, và tắt khi không dùng.
- **Không xin `QUERY_ALL_PACKAGES`:** app chỉ khai báo `<queries>` cho `com.discord`, không biết máy cài những app nào khác.
- Vì APK chỉ ký bằng keystore riêng (không còn keystore công khai trong repo), người khác không thể ký APK trùng chữ ký của bạn;
  chỉ cài APK do chính bạn build từ GitHub Actions của mình.

## Ký APK (keystore riêng)
APK được ký bằng khóa **chỉ bạn giữ**, đưa vào CI qua GitHub Secrets (repo → Settings → Secrets and variables → Actions → New repository secret):

| Secret | Nội dung |
|---|---|
| `STARHUB_KEYSTORE_BASE64` | file `.jks` mã hóa base64 trên **một dòng** (`base64 -w0 starhub.jks`) |
| `STARHUB_KEYSTORE_PASSWORD` | mật khẩu kho khóa (cũng là mật khẩu khóa) |
| `STARHUB_KEY_ALIAS` *(không bắt buộc)* | mặc định `starhub` |
| `STARHUB_KEY_PASSWORD` *(không bắt buộc)* | mặc định bằng mật khẩu kho khóa |

- Workflow `Build APK` giải mã secret thành file tạm rồi đặt `STARHUB_KEYSTORE_FILE`; `app/build.gradle` đọc biến đó. Thiếu secret thì
  build **báo lỗi** (cố ý, để không ký nhầm bằng khóa công khai).
- **Sao lưu file `.jks` + mật khẩu ở chỗ an toàn.** Mất khóa thì mọi bản sau không cài đè được lên bản cũ (phải gỡ app rồi cài lại).
- **Đổi khóa ký = Android coi là app khác:** lần đầu cài APK ký bằng khóa mới phải **gỡ bản cũ trước** (mất cài đặt trong app: địa chỉ API,
  token, vị trí Discord, thống kê farm). Nhớ ghi lại các thông tin đó trước khi gỡ.
- Không bao giờ commit file `.jks` (đã chặn trong `.gitignore`).

