# Star Hub (AutoMacro)

App Android tự động hóa thao tác trên máy (bấm, vuốt, gõ chữ, mở app) kiểu Tasker/MacroDroid, ghép với
bot [owo-tracker](https://github.com/Rubysand99/owo-tracker): owo-tracker là "bộ não" (biết lúc nào cần làm gì),
Star Hub là "tay chân" (thực thi trên điện thoại) và có màn hình **Hub** hiển thị dữ liệu bot đọc được.

> Tên app: **Star Hub** — tên gói `com.starclan.starhub` (trước đây là `com.tuytam.automacro`).
> Chi tiết trạng thái, thiết kế và lịch sử thay đổi nằm trong `AI_CONTEXT.md`.

## Build APK
Push lên nhánh `main` trên GitHub, tab **Actions** tự build và cho tải APK debug ở phần **Artifacts** của workflow
`Build APK` (artifact `starhub-debug-apk`). Không cần gradlew, không cần PC.
Sau bước build, workflow chạy thêm unit test JVM (`gradle testDebugUnitTest`); bước này chưa chặn build.

## Cài APK và bật quyền
1. Cài file APK tải từ Artifacts. Vì đổi tên gói nên đây là app **mới**: gỡ bản cũ bằng tay, nhập lại địa chỉ API/token.
2. Mở app → tab **Cài đặt** → "Bật quyền Accessibility" → tìm **Star Hub** trong danh sách → bật.
3. Nhập **Địa chỉ API** (phải là `https://...`, Android chặn `http://`) và **token** giống `AUTOMACRO_API_TOKEN` của bot, rồi bấm Lưu.

## Thông báo huntbot
Khi huntbot còn khoảng 10 phút nữa là về, app hiện thông báo trên điện thoại (mỗi chuyến 1 lần). Service Accessibility hỏi bot
mỗi phút nên **không cần mở app hay bật Hub nổi**, chỉ cần đã bật "đồng bộ owo-tracker" trong Cài đặt. Lần đầu mở app trên
Android 13+ sẽ hỏi quyền thông báo — hãy bấm Cho phép. Giờ huntbot do bot tính nên không phụ thuộc đồng hồ điện thoại.

## Hub nổi
- **Luôn sáng màn hình:** Hub nổi đang bật thì màn hình không bao giờ tự tắt.
- **Đếm ngược khi chờ:** khi app nghỉ / giãn nhịp / chờ cooldown, panel hiện "lý do · m:ss" kèm thanh chạy về 0, và bong bóng
  ⭐ đổi thành đồng hồ (thấy được cả khi menu đang thu gọn) để biết app không bị đứng.
- **Captcha:** hiện đồng hồ đỏ 10 phút "thời gian vàng" tính từ lúc OwO báo; chuông **reo mãi cho đến khi bấm "Tắt chuông"**
  trên Hub. Giải xong gõ `,captcha clear` thì đồng hồ tự biến mất.
- **Bật Hub = bot ON, tắt Hub = bot OFF** (app tự báo cho bot; bot ngừng giao việc khi Hub tắt).

## Lưu ý
- APK được ký bằng `keystore/debug.keystore` nằm trong repo (mật khẩu mặc định `android`) để các bản build cài đè lên nhau
  được. Phù hợp dùng cá nhân; **đừng phát hành công khai** theo cách này.
- Service Accessibility chỉ đăng ký 2 loại sự kiện (đổi cửa sổ, bấm) dùng cho chế độ ghi kịch bản; việc đọc màn hình khi chạy
  đều đọc chủ động qua `rootInActiveWindow`.
