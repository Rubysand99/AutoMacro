# AI_CONTEXT.md — Star Hub (truoc day: AutoMacro / TuyTam)

## Du an la gi
App Android tu dong hoa thao tac tren may (bam, vuot, go chu, mo app) - kieu
Tasker/MacroDroid. Muc dich chinh: ho tro choi OwO Bot tren Discord.
Nguoi dung chi quan sat, app tu thuc hien cac buoc theo "kich ban" da tao.
Repo GitHub: Rubysand99/AutoMacro (https://github.com/Rubysand99/AutoMacro).

Dang ket hop voi du an rieng "owo-tracker" (bot Discord theo doi choi OwO,
repo Python/discord.py rieng) de tao 1 he thong tap trung: owo-tracker la
"bo nao" (biet luc nao can lam gi), AutoMacro la "tay chan" (thuc thi tren
dien thoai). Xem muc "Ket noi voi owo-tracker" ben duoi.

## v31 — ,weaponreview quet lai kho truoc khi duyet (6/11/2026)
- Loi goc: bot lay ID tu danh sach cu trong DB nen go `owo w <ID>` cho cay da ban/ra ("could not find a weapon...").
- Nay `,weaponreview` (bot) xoa danh sach kho cu, nho app go **`owo w`** (khong ID) + bam "forward" toi da 100 lan (15 cay/trang ~ 1500 cay), action `weapon_review_scan`. `CommandGuard` da cho phep `owo w` tran (them test). Phai DUNG farm (app tu choi click_label khi dang farm -> ack failed -> bot bao).
- Bot chi nhan trang co tieu de `<@ID cua ban>'s Weapons` luc quet (`weapon_list_accepted`); xong thi loc quyet dinh Giu/Bo cu khong con trong kho roi go `owo w <ID dau>` (`_after_review_scan`). ID bao "could not find a weapon" -> `review_skip_missing`: bo qua, ghi `missing` (hien o embed), go cay ke. Test `test_v31.py`. App khong doi ngoai CommandGuard.

## v30 — Bot khong xin lenh check da tat (6/11/2026)
- App bao danh sach lenh dang TAT len bot: `POST /app-config {"disabled":[id...]}` (`OwoTrackerApi.pushDisabledCommands`, goi tu `AutoAccessibilityService.syncDisabledCommands` moi lan poll khi doi cau hinh, va moi 15 phut cho chac; chi khi Hub noi dang bat). Bot luu config `app_disabled_cmds`, `auto_check_tick` truyen `skip` vao `auto_refresh.pick_check` nen khong xep lenh do nua (ban v29 chan phia app van la lop du phong).
- id bot hieu: hb, cash, inv, quest, ws, zoo (`auto_refresh.APP_CMD_TO_CHECK_KEY`); id la bi bo. Test: `test_auto_refresh.py` (skip), `test_v30.py` (app-config).

## v29 — Ra soat (6/11/2026)
- Sua: lenh check ma BOT nho app go xen (action `auto_check`: owo cash/inv/quest/ws/zoo/hb) nay cung bi cong tac "Lenh trong farm" chan (truoc do tat Zoo van bi bot nho go owo zoo). App ack `failed`, khong go gi (`FarmCmd.forCheckCommand`). Lenh khac (hbstart, thay gem, quet vu khi...) KHONG bi chan.
- Don: bo 4 string + 2 drawable khong con ai dung (btn_owo_tracker_settings, hub_section_daily/cowoncy/huntbot, bg_hub_root, ic_daily).

## v28 — Tab "Cong cu" dua lenh owo-tracker len app (6/11/2026)
- Bottom nav co 3 tab: Hub / Cong cu / Cai dat. `ToolsFragment.kt` dung UI hoan toan bang code (khong XML), moi lan mo tab la Fragment moi nen tu tai lai.
- Cong tac bot -> `GET/POST /settings` (khoa auto_check, hb_auto, hb_alert; event_mode dung `/event-mode`, app_on dung `/power` nhu truoc), ghi chu -> `/notes`, bao giai captcha -> `POST /captcha/clear`. Ben bot them o owo-tracker (handler `handle_*_settings/notes/captcha_clear`, test `test_v30.py`).
- Tra cuu: `data/HubSearch.kt` (thuan Kotlin, bo dau tieng Viet, toi da 15 ket qua/nhom) tim tren du lieu `/hub` da tai: zoo, dex, kho, vu khi. Parse JSON cong cu o `data/BotTools.kt`. Test: `HubSearchTest`, `BotToolsJsonTest`.
- Chua lam (co the them sau): `,setalt`/`,testalt`, `,petset`, `,weapontypeset`/`,passiveset` (nhap tay), `,teamcheck`, `,zoodiff`, `,unlocked` — can them endpoint rieng ben bot.

## v26 — Bat / tat tung lenh farm + cau hinh (6/11/2026)
- Tab Setting cua Hub noi co khung "Lenh trong farm": moi lenh 1 nut BAT/TAT (15 lenh, 3 nhom: chinh / viec vat / kiem tra) + 2 cau hinh nhanh "Bat tat ca" va "Chi hunt + battle".
- Du lieu: `data/FarmCommandConfig.kt` (thuan Kotlin, co test). Chi luu tap lenh DANG TAT (prefs `star_farm`, key `cmd_disabled`) nen lenh moi them sau mac dinh BAT. KHONG doi `id` cua FarmCmd da dung.
- `FarmController`: `FarmState.commands`, `changeCommands` / `setCommandEnabled`; `runLoop` doc cau hinh MOI VONG (doi giua chung co hieu luc tu vong ke tiep); `runOneChore` bo viec bi tat khoi danh sach random.
- Khong co lenh nao bat -> farm tu choi chay (bao ro ly do).
- ZOO tat = bo ca buoc check zoo luc bat dau lan zoo filler. UPGRADE van gui them `owo hb`. Lenh bot tu go: swap_gem KHONG nam trong cau hinh nay; auto_check (cash/inv/quest/ws/zoo/hb) thi CO bi chan (v29).

## v27 — Bo keystore debug cong khai (6/11/2026)
- Run Actions sau khi Ruby dat 2 secret log "Dung keystore RIENG de ky APK", khong canh bao -> xoa `keystore/debug.keystore`, bo nhanh du phong trong `app/build.gradle`.
- Thieu / sai secret: workflow `exit 1` voi `::error`; `build.gradle` nem GradleException neu khong co STARHUB_KEYSTORE_FILE/PASSWORD (CI luon co du, build local can tu dat env).

## v25 — Keystore RIENG de ky APK (5/11/2026)
- `app/build.gradle`: signingConfig `starhub` (dung cho buildType debug). Co env `STARHUB_KEYSTORE_FILE` + `STARHUB_KEYSTORE_PASSWORD` thi ky bang khoa rieng
  (alias mac dinh `starhub`, `STARHUB_KEY_ALIAS` / `STARHUB_KEY_PASSWORD` tuy chon); khong co thi build BAO LOI (da bo keystore debug du phong o v27).
- `.github/workflows/build.yml`: buoc "Chuan bi keystore rieng" giai ma secret `STARHUB_KEYSTORE_BASE64` ra `$RUNNER_TEMP/starhub.jks`; mat khau lay tu secret `STARHUB_KEYSTORE_PASSWORD`.
  Thieu secret -> `::error`, build dung (v27).
- Khoa duoc sinh 1 lan tren phien lam viec (RSA 2048, hieu luc ~27 nam, PKCS12, CN=Star Hub). Claude KHONG dat duoc GitHub Secrets (proxy chan) nen Ruby tu dan 2 secret
  (xem README muc "Ky APK"). File .jks + mat khau da gui rieng cho Ruby; KHONG nam trong repo (`.gitignore` chan `*.jks`).
- Chu ky SHA-256 cua khoa moi: `FF:E1:69:27:81:B4:B9:0B:3E:C1:A5:A8:77:1A:FB:B8:15:DC:F4:D9:9B:72:EC:89:2D:DC:2F:21:6F:B2:A6:4A` (de doi chieu APK dung khoa nao).
- DOI KHOA = Android coi la app khac: lan dau phai GO ban cu roi cai lai (mat cai dat trong app).
- Da xong o v27: Actions xac nhan dung keystore rieng -> da xoa `keystore/debug.keystore` + nhanh du phong.

## v24 — GO BO han che do kich ban cu (5/11/2026)
- Ruby xac nhan khong dung kich ban nua -> da xoa: `ScriptEditorActivity`, `engine/ScriptEngine`, Room (`AppDatabase`, `ScriptDao`, `ScriptEntity`,
  `ScriptRepository`, `ScriptWithSteps`), `ScriptStep`/`StepType`, `ScriptJson`, `SampleScripts`, `StepSummary`; 13 layout (`activity_script_editor`,
  `dialog_step_*`, `dialog_on_fail_section`, `item_step_row`, `overlay_recording`), 5 drawable chi dung cho chung, 45 chuoi.
- `AutoAccessibilityService` chi con: dong bo owo-tracker (poll 15 giay), go lenh vao Discord, nhac huntbot, wizard cai vi tri o nhap + nut Gui
  (`setupDiscordTargets`, giu `overlay_point_picker` + 2 cham `bg_marker_*`) va `performClickAwait`. Da bo che do ghi, `runScript`, bong bong ghi,
  `performClick`/`performSwipe`. `onAccessibilityEvent` de trong (app doc man hinh chu dong, khong theo su kien).
- `app/build.gradle`: bo plugin `kotlin-kapt`, Room, `recyclerview` (Gson, coroutines giu nguyen). Manifest bo `ScriptEditorActivity`.
- 3 chuoi nguoi dung doi noi dung cho khop (khong con nhac "kich ban"): `accessibility_service_description` (hien trong Cai dat Android),
  `note_owo_sync`, `status_service_disabled`.
- Muon khoi phuc: `git log --diff-filter=D --name-only` roi `git checkout <commit>^ -- <file>` (lich su git con day du).
- Ghi chu cu ben duoi (v16..v22, "Chua co", "Cong nghe", "Cau truc") nhac kich ban/Room la LICH SU, khong con dung voi code hien tai.

## v23 — Don ton dong: giam quyen + test HubDexQuery (5/11/2026)
- `accessibility_service_config.xml`: them `android:packageNames="com.discord"` -> dich vu chi nhan su kien / doc noi dung man hinh cua Discord
  (truoc day nhan tu MOI app). Cac cho doc man hinh van tu kiem tra goi `com.discord` (ButtonClicker, TagCollector, NodeDump) nhu cu.
- `AndroidManifest.xml`: bo quyen `QUERY_ALL_PACKAGES` (nhay cam, khong can), thay bang `<queries><package com.discord/>` de
  `DiscordSender` van goi duoc `getPackageInfo` / `getLaunchIntentForPackage` cho Discord tren Android 11+.
  **CAN THU TREN MAY:** mo Hub noi -> "Thu mo Discord" -> bat farm. Neu bao "Khong tim thay Discord" hoac khong doc duoc man hinh,
  hoan lai 2 thay doi tren (1 dong moi cho).
- He qua: che do ghi (record) + `ScriptEngine.openApp` cho app KHAC khong con dung duoc nua (da khong co loi vao tu giao dien tu v16).
- Bo 2 truong khong dung `hubVersion` / `serverTime` khoi `HubResponse` (Gson bo qua truong la nen khong anh huong bot cu/moi).
- Them `HubDexQueryTest` (loc / sap xep / dong chi so cua Pet Dex).
- DA XONG tu truoc (ghi chu cu o duoi con ghi "chua lam" la loi thoi): token dung EncryptedSharedPreferences (`OwoTrackerPrefs`);
  `FarmController` cap nhat state bang `_state.update` (nguyen tu); TagCollector va Dump man hinh chi doc Discord;
  TagCollector va Farm khong chay chong nhau (`start()` tu choi neu ben kia dang chay).
- (Da xu ly o v24/v25: don code kich ban cu; keystore rieng — xem muc v25 ve viec con lai.) VAN CON: chuoi tieng Viet viet thang trong Kotlin.

## v22 — Duyet vu khi Giu / Bo bang 2 nut tren Hub noi
- Tracker: `,weaponreview` (start|stop|undo|export|reset) -> embed danh sach (dang kiem tra / con lai / ID Giu / ID Bo), queue `owo w <id>` dau tien;
  `POST /weapon-review {decision}` ghi quyet dinh, XOA tin lenh + tin tra loi OwO cua cay do (chua embed danh sach), tra `next_id`; `/hub` co `weapon_review`.
  Cay co SAO hoac dang trang bi = GIU mac dinh, khong dua vao hang doi. CHI GHI DANH SACH, khong tu dismantle/ban.
- App: `FarmController.reviewDecision` (POST roi go `owo w <next>` neu KHONG farm), 2 nut Giu/Bo canh bong bong (chi hien khi phien duyet dang chay).
  Bot can quyen Manage Messages trong kenh de xoa tin. CHUA BIEN DICH tren may.

## v21 — 3 che do toc do farm: Nhanh / Thuong / Cham
- `data/SpeedMode.kt` (thuan Kotlin, co test): KHONG nghi giua lenh khac nhau (hunt->battle go luon; chi cooldown cung lenh hunt->hunt phai cho); moi muc co do dai vong (>=15,5s = cooldown OwO), tan suat + do dai nghi giai lao.
  Thuong = nhip cu. Gioi han burst (3 lenh/12s) va CommandGuard KHONG doi. `FarmState.speed`, luu o prefs "star_farm"/"speed_mode",
  `FarmController.setSpeed`. Vong farm doc `speed` moi vong nen doi giua chung co hieu luc ngay. UI: 3 nut o tab Setting cua Hub noi.
- Moi vong farm go them 1 lan "owo" ngay sau battle (4.6b); da bo "say_owo" khoi danh sach lenh chen.
- Nhanh = rui ro bi OwO nghi ngo cao hon (da ghi chu tren giao dien). CHUA BIEN DICH tren may.

## v20 — Giao dien moi: toi, gon, kieu dashboard (4/11/2026) — DOT 1/3: he mau + style chung
- Chi con giao dien TOI (da xoa values-night/drawable-night/color-night). Token mau o `res/values/colors.xml`;
  ban sao cho code ve tay o `view/UiTheme.kt`; `UiThemeColorsTest` doi chieu 2 noi + kiem tra tuong phan WCAG 4.5:1. Doi mau thi sua CA HAI.
- Quy tac: nhan vang `accent` CHI cho nut chinh (1 nut/man hinh); tim `selected` cho tab dang chon/cot hom nay; xanh/xanh duong/vang/do
  chi la trang thai va luon kem chu hoac icon. Co chu: so 26, tieu de man 20, tieu de the 14, chu thuong 13, chu phu 11.5. Bo goc the 16, o con/nut 12.
- Style: `Widget.AutoMacro.Card/Button(.Tonal=nut vang/.Outlined/.Danger)`, `TextAppearance.StarHub.*`.
- Icon net 1.8dp (`ic_gem, ic_huntbot, ic_quest, ic_daily, ic_coin, ic_chart, ic_alert, ic_pets, ic_sliders, ic_bolt, ic_star, ic_refresh, ic_play, ic_stop...`)
  thay emoji; mau lay tu tint cua view.
- Dot 2 (xong): FarmOverlay dung UiTheme; tab co icon (ic_bolt/ic_sliders/ic_grid/ic_star), nut theo `Btn` (PRIMARY vang/DANGER/ACTIVE/SECONDARY) qua `styleBtn`,
  o so lon Hunt/Battle (tab Farm), thanh do ben gem 4dp, vong % Huntbot (RingView), thong ke 3 so lon + `DayBarsView` 7 ngay,
  bong bong 3 trang thai (vang / tim khi cho / do khi captcha). Row co `build` de chen view tuy y. Icon cho overlay KHONG duoc dung ?attr/theme (Service khong co theme).
- Dot 3 (xong): man Hub (thanh tren "Star Hub" + dong trang thai + nut lam moi icon; o tom tat vien trung tinh, so 26 trang, dong phu mang mau trang thai,
  o icon nen nhat bo goc 10; the chi tiet khong con vach mau, chevron icon), man Cai dat (4 the: Quyen truy cap voi huy hieu, Ket noi owo-tracker voi Switch,
  Hub noi & farm, Tier gem; 1 nut vang Luu o cuoi). Da bo emoji trong strings.xml (icon that do IconView/Vector). CHUA BIEN DICH tren may.

## v19 — Siet an toan lenh tu server sau ra soat bao mat (3/11/2026)
- `CommandGuard.isSafeCommand` doi tu "bat dau bang `owo `" sang DANH SACH CHO PHEP theo dong tu: `owo hb [<so>[h|m|d] [<ma>]]`,
  `owo use <ma>...` (toi 12 ma 1-4 chu so), `owo dex <ten>`, `owo w <ID 4-8 ky tu>`, `owo weapon|cash|inv|quest|ws|zoo`.
  `owo give/sell/pay/sacrifice/...` bi chan. **Them lenh moi o bot (owo-tracker) thi PHAI them 1 dong o CommandGuard + 1 test**,
  neu khong app tu choi lenh do (ack `failed`). Test: `CommandGuardTest`.
- `click_label` tu server chi nhan `forward` (`CommandGuard.isAllowedClickLabel`); `ButtonClicker` chi bam khi cua so dang
  hien la Discord (`com.discord`), nguoi dung chuyen app giua chung thi dung.
- `AutoAccessibilityService.onDestroy` goi `FarmOverlay.hide()` (dung farm + tu hoc tag, go Hub noi) — truoc day service chet
  ma farm van chay tiep.
- `OwoTrackerApi.ack` tra `Boolean`, thu toi 3 lan (0s/1s/3s) khi loi mang/5xx, dong ket noi. Service nho 200 lenh gan nhat
  (`handledCommands`, trong bo nho, kem chu ky action+command_text): lenh da lam ma ack that bai thi lan poll sau CHI bao lai ket qua,
  KHONG go lai. Lenh gui di la ban da `trim()`.
- `FarmController`: ID vu khi tu /hub phai khop `^[A-Za-z0-9]{4,8}$` moi duoc ghep vao `owo weapon <ID>`.
- CHUA BIEN DICH tren may (khong co Android SDK): chi kiem tra regex bang Python + doc lai diff. Xem ket qua GitHub Actions.
- Hub noi (FarmOverlay): sua loi "bam ➖ thu gon xong menu tu bat lai" — `render()` truoc day ep mo menu MOI LAN chay khi con canh bao;
  nay chi tu mo 1 LAN cho moi canh bao moi (`lastExpandedAlert`). Them tu thu gon menu sau 8 giay khong cham (`AUTO_COLLAPSE_MS`,
  `TouchAwareLinearLayout` tinh lai gio moi lan cham); dang co canh bao (vd captcha) thi KHONG tu thu gon. CHUA BIEN DICH tren may.
- Hub noi chia 4 TAB (Farm / Setting / Misc / Event) trong `FarmOverlay`: phan dau (trang thai, dem nguoc, canh bao, Bat dau/Dung, ➖, ✖)
  co dinh o moi tab. Farm = nhom farm+gem+hb; Setting = nut Hunt mode + bat/tat tu thu gon menu; Misc = hoc tag + res+quest+zoo;
  Event = nut Event + nhom event (luon hien, chua co du lieu thi bao "Chua thay tin su kien"). Tab dang chon + che do tu thu gon luu o
  SharedPreferences `starhub_overlay` (khong chua token). Them muc vao tab nao thi dung `addTo/addGroupTo(TAB_x, ...)` trong `show()`.
  CHUA BIEN DICH tren may.
- Thong ke farm theo ngay (Hub noi, tab Misc, nhom "📊 Thong ke"): `data/FarmStats.kt` (thuan Kotlin, co `FarmStatsTest`: `StatsBook`, `DayStats`,
  `StatsFormat`) + `data/FarmStatsStore.kt` (SharedPreferences `starhub_stats`, giu 14 ngay, khong gui len bot). `FarmController` ghi: so phien + gio chay
  (`startSession` sau khi qua buoc kiem tra dau vao, `endSession` o `finally` cua job farm), hunt, battle, captcha (1 lan/dot), pet moi, Slow down
  cao nhat, cowoncy lan doc dau/cuoi ngay (tinh theo NGAY DOC = bay gio - `age_seconds`, nen so lieu cu hom truoc khong bi tinh nham).
  Hub noi hien hom nay + 6 ngay truoc + tong; nut "🗑 Xoa thong ke" o tab Setting (bam 2 lan). Phien chay qua nua dem: gio chay tinh sang ngay moi.
  CHUA BIEN DICH tren may.
- (Ghi chu cu, da cap nhat o v23) Ton dong tu ra soat: phan lon da xong; xem muc v23 de biet cai nao con lai.

## v18 — Chon tier gem duoc phep trong app (2/11/2026)
- Tab Cai dat co khoi "Tier gem khi thay" + nut "Chinh tier gem" -> `GemTierDialog` (dung code, khong layout XML): moi loai gem (Hunting /
  Empowering / Lucky / Special) la 1 nhom chip bat/tat tung tier, kem so gem con trong kho. Bam Luu chi gui cac loai DA DOI.
- API bot: `GET /gemtier` -> {tiers, slots:[{slot,name,allowed,pref,stock}]}; `POST /gemtier` body {"allowed":{"1":["Common",...]}}
  (danh sach rong = tat het; tier/loai la -> 400 va khong doi gi). Cung nguon voi lenh Discord `,gemtier tat|bat`.
- Hop thoai con o chon "Uu tien khi thay" (Spinner: cao nhat / thap nhat / tung tier); POST them khoa `"pref":{"1":"highest|lowest|<tier>"}`.
  `GemTierChanges(allowed, pref)` gom cac loai DA DOI; `GemPref` (thuan Kotlin) doi giua gia tri bot va vi tri o chon.
- Code: `data/GemTierConfig.kt` (model + `GemTierJson`, thuan Kotlin, co `GemTierJsonTest`), `OwoTrackerApi.fetchGemTier/saveGemTier`,
  `GemTierDialog.kt`, `SettingsFragment.openGemTierDialog`. Can bot >= ban co /gemtier, neu khong app bao "can cap nhat bot".
- CHUA BIEN DICH tren may: Kotlin chi kiem tra qua GitHub Actions.

## v17 — So phien ban tu tang (2/11/2026)
- File `VERSION` o goc repo (dang `1.12`). Workflow `Build APK` TU TANG so cuoi TRUOC khi build (push len main, bo qua commit
  `chore(version)`), build + test xanh moi commit lai `chore(version): v1.13` len main. APK mang dung so moi. Chay tay (workflow_dispatch)
  thi giu nguyen so. Muon len so lon: sua tay VERSION thanh `2.0` roi push.
- `app/build.gradle` doc VERSION: `versionName` = so do, `versionCode` = major*10000 + minor (hong/thieu file -> 1.0).
- Hien thi: tab Cai dat co dong "Star Hub v1.x" o cuoi; tab Hub hien them "bot v1.x" canh dong "Cap nhat luc" (bot gui `bot_version`
  trong /hub; `HubParser` doc rieng, bot cu khong gui thi bo qua). Test: `HubParserBotVersionTest`.
- Khi push tay nho `git pull` truoc vi workflow vua them 1 commit.

## v16 — Go tab "Tu dong", thong bao huntbot co dong ho dem nguoc (1/11/2026)
- **Da go tab "Tu dong"** (navAutomatic): xoa AutomaticFragment, ScriptListAdapter, fragment_automatic.xml, item_script.xml,
  ic_tab_automatic.xml, bg_icon_button_tonal.xml + 6 chuoi chi dung cho tab do. App con **2 tab**: Hub, Cai dat. Muc "3 TAB" ben
  duoi la lich su (tab thu 3 khong con).
- **Con lai (chua xoa):** ScriptEditorActivity, Room (ScriptEntity/Dao/Database/Repository), che do ghi (record) + `runScript` trong
  AutoAccessibilityService, SampleScripts. Cac man hinh/che do nay KHONG con loi vao tu giao dien (truoc day chi bam tu tab "Tu dong")
  -> co the don tiep neu Ruby chac chan khong dung kich ban nua. KHONG xoa AutoAccessibilityService, DiscordSender, FarmController,
  NodeTools, ScriptEngine: Hub noi + farm + gui lenh Discord van dung chung.
- **Thong bao huntbot chinh xac hon:** `HuntbotNotifier` them `setWhen(gio ve)` + dong ho dem nguoc he thong
  (`setUsesChronometer` + `setChronometerCountDown`) + chu "Huntbot ve luc HH:mm, con khoang N phut" (gio ve = gio may + `seconds_left`
  do BOT bao, khong so dong ho bot voi may) + `setTimeoutAfter` (tu go sau khi ve 2 phut). Logic thuan o `HuntbotAlertLogic.readyAtMillis`
  / `timeoutAfterMillis` (co test). Chuoi `huntbot_alert_text` doi thanh 2 tham so: `%1$d` phut, `%2$s` gio.
- Phia bot (owo-tracker): tin BAT DAU chuyen moi ("I WILL BE BACK IN 24H ...", khong co chu STILL HUNTING) gio duoc tinh la dang san
  (`parse_huntbot_status`), nen /alerts va /hub co gio ve ngay, khong phai cho Ruby go `owo hb`.
- CHUA BIEN DICH tren may: Kotlin chi duoc kiem tra qua GitHub Actions. Neu Actions bao loi o HuntbotNotifier/MainActivity thi gui log de sua.

## v15 — Doi ten thanh STAR HUB (Ruby chon, 4/10/2026)
- Ten app: **Star Hub**. Ten goi: `com.starclan.starhub` (truoc: `com.tuytam.automacro`). Day la app MOI
  tren Android -> phai nhap lai dia chi API/token, cap lai quyen Accessibility; go ban cu bang tay.
- Icon: mau A — ngoi sao vang (#FAC775) tren nen tim (#3C3489), adaptive icon (mipmap-anydpi-v26).
- Ten style/theme (Theme.AutoMacro, Widget.AutoMacro.*) GIU NGUYEN, chi la ten noi bo.
- Bien moi truong cua bot `AUTOMACRO_API_TOKEN` GIU NGUYEN (khong lien quan ten goi).
- Hub: bo dong "N loai · M mon" in 2 lan o the Kho; the Gem hien gio cap nhat RIENG cho "Du phong trong kho"
  (bot v16 gui `gems.spare_age_seconds`, lay tu owo inv; gem dang dung lay tu owo hunt).
- Tu dong gui lenh Discord viet lai (engine/DiscordSender.kt + engine/NodeTools.kt):
  * Nguyen nhan loi cu (de xuat, chua kiem chung tren may): cu cham chi "bat" roi chay buoc go chu NGAY
    (o nhap chua kip duoc chon); khong mo Discord truoc; SET_TEXT khong doc lai de xac nhan.
  * Moi lenh: mo Discord (dung kenh neu co link trong Cai dat) -> dung neu thay captcha OwO -> tim o nhap
    -> go (SET_TEXT, khong vao thi dan clipboard) va DOC LAI -> bam nut Gui (theo nhan Send/Gui, khong co thi
    toa do) -> doc lai xem chu da roi o nhap chua. Nghi ngau nhien 0.4-1.1s giua buoc, 2.5-5.5s giua lenh.
  * Gap captcha: rung + chuong, tam dung gui lenh 10 phut, lenh do ack "failed".
  * Cai dat them o "Link kenh Discord" (khong bat buoc). Toa do o nhap/nut Gui chi con la phuong an DU PHONG.
  * accessibility_service_config them flagReportViewIds + flagIncludeNotImportantViews (Discord la React Native).
  * ScriptEngine: buoc Bam gio CHO cu cham xong (performClickAwait) + nghi 0.4s; buoc Go chu dung NodeTools.typeInto.
- (Lich su v15) Code Kotlin tung viet khong co Android SDK nen chua bien dich tren may; luon xem ket qua GitHub Actions.

## Trang thai hien tai (da lam duoc)
- Khung project: Gradle, AndroidManifest, build qua GitHub Actions (khong dung gradlew,
  Gradle 8.6 duoc cai truc tiep tren runner). Ky bang debug.keystore CO DINH (commit
  trong repo o keystore/) de cac ban build khong xung dot chu ky khi cai de.
- Bo may chay kich ban (ScriptEngine): doc tung buoc, tim phan tu theo chu/ID (uu tien)
  hoac toa do du phong, ho tro re nhanh "neu loi thi nhay toi buoc X". run() tra ve
  true/false bao co chay het kich ban khong (dung de bao ket qua ve owo-tracker).
- 7 loai buoc: OPEN_APP, WAIT, TAP, SWIPE, TYPE_TEXT (go vao o dang focus, ho tro
  placeholder {{ten}}), CHECK_TEXT, CHECK_EXISTS, NOTIFY.
- Man hinh chinh: danh sach kich ban da luu (Room), nut Chay/Xoa moi kich ban,
  nut chay kich ban mau de test, nut Cai dat ket noi owo-tracker.
- Man hinh tao kich ban (ScriptEditorActivity): dat ten, "+ Them buoc" mo hop thoai
  theo tung loai, bam lai vao 1 buoc da them de SUA (ke ca chon "neu loi" tro toi
  1 buoc them SAU). Luu kich ban serialize sang JSON (Gson) vao Room.
- CHE DO GHI (record): bam nut "Ghi kich ban moi" -> bong bong noi co bang danh sach
  hanh dong danh so, cap nhat ngay khi ghi duoc (TYPE_ACCESSIBILITY_OVERLAY). Tu dong
  bat TAP (theo chu/ID) va OPEN_APP (loc bo ban phim/systemui). 2 nut thu cong tren
  bong bong: 👆 (danh dau 1 diem de Bam) va ➕ (cham 2 diem dau/cuoi de tao buoc Vuot
  chinh xac) - ca 2 co hien cham mau xac nhan vi tri vua cham.
  GIOI HAN: van CHUA tu ghi duoc go chu (phai dung TYPE_TEXT thu cong + 👆 cho nut Gui).
- Ket noi owo-tracker: man hinh Cai dat (nut ⚙️) luu URL API + token (SharedPreferences),
  va vi tri (toa do) O NHAP TIN NHAN + NUT GUI tren Discord - cai 1 LAN DUY NHAT qua
  wizard cham 2 diem. Khi bat, service cu 15 giay goi GET /commands/pending 1 lan;
  MOI lenh nhan duoc (bat ky "action" nao, khong can khop ten kich ban) deu chay dung
  3 buoc: bam o nhap -> go params.command_text -> bam Gui, roi POST /commands/{id}/ack
  bao done/failed. Don gian hoa tu ban dau (bo phan tao kich ban rieng + khop ten,
  vi Ruby thay qua phuc tap) - gio KHONG can Room/kich ban cho phan nay nua.

- Man hinh HUB (tab "OwO Hub" -> HubFragment, xem phan "3 TAB" ben duoi): hien du lieu moi
  nhat owo-tracker da doc duoc tu tin nhan OwO (HuntBot co dong ho dem nguoc, gem dang
  dung + gem du phong, quest, doi hinh, tran dau, pet/zoo, vu khi, kho do). Goi
  GET /hub (cung token voi /commands/pending), tu lam moi moi 30 giay khi dang mo, moi
  giay chi cap nhat dong ho + chu "X phut truoc".
  GIAO DIEN: 6 o tom tat o tren (Daily, HuntBot, Cowoncy, Quest, Gem, Pet) - moi o 1 so to + 1 mau
  trang thai (xanh la=xong/san sang, xanh duong=dem nguoc, cam=de y, do=can lam ngay, xam=chua co
  du lieu; logic o data/HubSummary.kt). Ben duoi la 6 the chi tiet (Gem, Quest, Doi hinh, Pet, Vu khi, Kho) moi the 1 mau rieng, luon co 1 dong
  tom tat, cham de mo/dong (Gem + Quest mo san). Gem/Quest dung ProgressBar that. Mau o res/values
  (+ values-night). Layout: activity_hub, item_hub_tile, item_hub_card.
  Muc nao bot chua tung thay thi hien "Chua co du lieu". HubFormat.kt la ham thuan (khong
  dung Android) chuyen JSON thanh chu; HubModels.kt la cac lop Gson khop JSON cua /hub;
  HubParser.kt doc TUNG MUC rieng (1 muc hong chi bao loi o muc do, khong lam trang ca man hinh).
  So co the rat lon (vd essence) nen dung Long, khong dung Int.
  Co them the Daily (dem nguoc gio nhan + streak), Cowoncy, va Quest (dem nguoc quest ke tiep
  + tung quest: ten, tien do, mo ta, thuong).
  Khong con doc/hien battle (Ruby khong can) - owo-tracker cung da bo phan doc battle.
  Them the Pet Dex (owodex): so lieu GOC tung loai pet (HP/ATT/PR/WP/MAG/MR, gia ban/hien te,
  hang), KE CA loai chua co - chi can Ruby tung go `owo dex <ten>`/`odex <ten>` 1 lan. Khac Zoo
  (chi liet ke dang co), Pet Dex liet ke moi loai da tra, dang co xep truoc. Model: HubPetDex/
  HubPetDexEntry trong HubModels.kt.
## 3 TAB (BottomNavigationView) - thay doi cau truc man hinh chinh  [LICH SU: tab 3 "Tu dong" da go o v16, chi con Hub + Cai dat]
MainActivity gio CHI la khung chua, dieu khien 3 tab qua supportFragmentManager.replace():
  1. navHub       -> HubFragment       (xem GIAO DIEN v2 ben duoi)
  2. navSettings  -> SettingsFragment  (quyen Accessibility + form ket noi owo-tracker, KHONG
                                        con la dialog popup - nam thang trong tab, co nut Luu rieng)
  3. navAutomatic -> AutomaticFragment (danh sach kich ban AutoMacro - chuc nang goc cua app)
Moi lan doi tab la 1 lenh replace() -> Fragment cu bi huy hoan toan, Fragment moi tao lai tu dau.
Vi vay Hub CHI polling du lieu (30s/lan) khi dang o tab do (gan voi viewLifecycleOwner.lifecycleScope,
khong phai lifecycleScope cua Activity nua). File lien quan: res/layout/activity_main.xml (goc, chi co
FrameLayout fragmentContainer + BottomNavigationView), res/layout/fragment_hub.xml, fragment_settings.xml,
fragment_automatic.xml, res/menu/bottom_nav_menu.xml, res/color/bottom_nav_item_color.xml,
res/drawable/ic_tab_hub.xml + ic_tab_settings.xml + ic_tab_automatic.xml.
DA XOA (khong con dung, XOA TAY tren may): HubActivity.kt, res/layout/activity_hub.xml,
res/layout/dialog_owo_tracker_settings.xml, khai bao <activity HubActivity> trong AndroidManifest.xml.

## THIET KE (thong nhat ca 3 tab, lam qua /ui-designer)
He mau: brand_violet (#6C5CE7, mau chinh) + brand_amber (#F5A623, DUY NHAT 1 diem nhan - dung
cho nut quan trong nhat moi man hinh: Ghi kich ban / Luu cai dat) + surface_card_light (nen the).
Dinh nghia o colors.xml + values-night (co ban toi rieng). ShapeAppearance trong themes.xml lam
MOI nut/the trong app tu dong bo goc 12-14dp, khong can khai bao tung noi. styles.xml co san:
Widget.AutoMacro.Card (the vien mau, KHONG do bong - dung cho item_script + 2 khoi trong Cai dat),
Widget.AutoMacro.Button/.Tonal/.Outlined, Widget.AutoMacro.TextInputLayout, TextAppearance.AutoMacro.SectionTitle.
Nen gradient dung chung ca 3 tab: drawable/bg_app_root.xml (doi ten tu bg_hub_root).
Chu so to trong Hub (tvTileValue) dung sans-serif-black ("so lieu la nhan vat chinh").
item_script.xml: the co vach mau ben trai + 2 nut icon tron (bg_icon_button_tonal.xml).
fragment_automatic.xml: co dong trang thai rong (tvEmptyScripts, AutomaticFragment.kt tu bat/tat).
fragment_settings.xml: 2 khoi the rieng (Quyen truy cap / Ket noi owo-tracker), o nhap dang
TextInputLayout bo goc, trang thai quyen doi mau theo bat/tat (SettingsFragment.kt).

## SAU CODE-AUDIT (4 muc da sua)
- view/AsyncLoadCache.kt (thuan Kotlin, khong dinh Android): gop nhieu yeu cau cung link thanh 1 lan tai;
  loi thi nho lai va cho thu lai sau 90s (truoc day loi 1 lan la hong het phien); nguoi xin bi huy thi viec tai
  van chay tiep. EmojiImageLoader chi con la lop mong dung no (+ Log + disconnect). Bam "Lam moi" goi
  EmojiImageLoader.retryFailed() de thu lai icon loi ngay.
- HubFragment: cham 1 the chi ve lai the do (rerenderCard); moi giay chi doi CHU khi chu khac
  (setTextIfChanged), nen/vien/vong %/icon cua 6 o chi ve lai khi AppliedTile doi hoac sau moi lan lay du lieu
  (tileGeneration). Pet Dex hien 30 loai/lan (DEX_PAGE_SIZE), bam "Hien them" de xem tiep.
- Mau: res/color-night/bottom_nav_item_color.xml (tab chua chon trang mo o che do toi); vien/duong ke dung
  @color/border_subtle (co ban sang + toi) thay vi hardcode #22000000/#1E000000.
- AndroidManifest: allowBackup=false (token nam trong SharedPreferences, khong de Google sao luu). Danh
  doi: kich ban AutoMacro (Room) cung khong con tu sao luu. (Da lam sau do: token luu bang EncryptedSharedPreferences.)
- v12 DA SUA them 4 muc xanh: OwoTrackerApi dong ket noi (finally conn?.disconnect()); HubFragment.loadHub
  co co loadingHub nen bam Lam moi nhieu lan khong sinh request chong; zooTileIcon chon theo HANG hiem nhat
  (ZOO_RARITY_ORDER trong HubIcons.kt), cung hang thi lay loai it con nhat; HubFormat.pct() ep % vao 0..100,
  HubFormat.num() hien "?" cho so am (so qua 64-bit bi Gson doi thanh am).
- v13 (theo owo_raw.json that): zoo — so nho trong bang = pet DANG CO; dong cuoi (F-6, CP-16... C-478489) = DA BAT
  tu truoc toi nay. Hub: total_pets = dang co, them caught_total (HubZoo) va caught (HubTier). Bot v14 cung sua parse_team,
  kho chia 2 tin, loc tin OwO cua nguoi khac.
- v14 Pet Dex: the Pet Dex co 3 hang nut loc/sap xep (Sap xep: Mac dinh/Tong/HP/ATT/MAG/PR/MR/WP/Ten; Loc: Tat ca/Dang co/Chua co;
  Hang). Logic thuan Kotlin o data/HubDexQuery.kt; trang thai dexSort/dexFilter/dexRank nam trong HubFragment. Chua co JUnit.
- CON TON DONG (muc xanh): neu bat R8 phai giu lop data.Hub* (hien `minifyEnabled false`); chuoi tieng Viet viet thang trong Kotlin.
  (hubVersion/serverTime khong dung: da bo o v23.)

  GIAO DIEN v2 (icon that + bieu do): moi pet/gem/tier/thanh vien doi hinh/phan thuong quest
  co the mang emoji_id/emoji_animated (ID that cua Discord, xem view/EmojiImageLoader.kt tai
  qua CDN https://cdn.discordapp.com/emojis/<id>.png) hoac emoji (ky tu Unicode co san, cho
  con vat OwO hien bang emoji chuan nhu :bee:). data/HubIcons.kt chon icon phu hop (uu tien
  anh that > Unicode > chu cai dau). view/IconView.kt la 1 o hien icon dung chung (FrameLayout:
  ImageView + TextView), can 1 CoroutineScope (lifecycleScope cua Activity) truyen vao bind().
  view/RingView.kt ve vong tron % bang Canvas (dung cho gem/quest/huntbot/daily). view/
  BarChartView.kt ve cot ngang so sanh so pet theo tier (trong the Zoo, khi mo rong). Nen man
  hinh Hub co gradient nhe (drawable/bg_hub_root.xml + drawable-night). Icon tai qua mang duoc
  cache trong bo nho (EmojiImageLoader), mat khi dong app - khong ghi the nho.
  KHONG co profile: `owo profile` cua OwO la 1 tam ANH (khong co chu) nen bot khong doc duoc.

## Chua co (con thieu)
- (Muc kich ban cu: sua kich ban da luu, keo-tha doi thu tu buoc, tu ghi go chu — KHONG con y nghia, da go bo o v24.)
- Hunt/battle tu dong DA co o `FarmController` (xem v21 - 3 che do toc do). Ve mat AutoMacro khong can lam gi them de ho tro action moi:
  owo-tracker gui "command_text" dung thi AutoMacro tu chay (sau khi them 1 dong o `CommandGuard` + 1 test).

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

## Cau truc thu muc chinh
app/src/main/java/com/starclan/starhub/
  MainActivity.kt                         - CHI dieu khien 3 tab duoi (BottomNavigationView),
                                              khong con chua logic gi khac. Mac dinh mo tab Hub.
  HubFragment.kt                          - tab "OwO Hub" (truoc la HubActivity, gio la Fragment)
  SettingsFragment.kt                     - tab "Cai dat": quyen Accessibility + ket noi owo-tracker
                                              (truoc la dialog_owo_tracker_settings, gio nam thang trong tab)
  service/AutoAccessibilityService.kt      - dong bo owo-tracker, go lenh vao Discord, nhac huntbot, wizard cai vi tri
  engine/                                  - FarmController, DiscordSender, NodeTools, ButtonClicker, TagCollector, QuestClaimer, HuntbotNotifier...
  data/                                    - OwoTrackerApi, OwoTrackerPrefs, CommandGuard, Hub* (model/parser/format/query), SpeedMode, FarmStats...
  view/                                    - FarmOverlay (Hub noi), UiTheme, RingView, DayBarsView, IconView...

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

