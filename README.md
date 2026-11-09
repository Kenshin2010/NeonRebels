# NEON REBELS — Biệt Đội Nổi Loạn

Bản thử nghiệm game hành động đi cảnh 2D offline, viết bằng **libGDX + Kotlin**. An (kiếm điện) và Vy (súng năng lượng) vượt khu phố Nova mất điện, đánh bại **Thiết Giáp Gác Cổng**, kích hoạt trạm phát sóng và tìm tín hiệu của Linh.

Mã nguồn gameplay, giao diện và hai bộ khởi chạy đã được triển khai. Ngày 09/11/2026 đã build được APK debug và `:core:test`/`:lwjgl3:jar` qua Gradle; **chưa chạy giao diện libGDX trên thiết bị/desktop**.

## Cấu hình và mở dự án

| Thành phần | Phiên bản |
| --- | --- |
| JDK chạy Gradle | 25, theo `gradle/gradle-daemon-jvm.properties` có sẵn |
| Gradle wrapper | 9.6.0, giữ nguyên |
| Android Gradle Plugin | 9.5.1, giữ nguyên |
| Android compile/target SDK | 37, giữ nguyên; cài Android SDK Platform 37 |
| SDK Build Tools | 36.0.0 theo mặc định AGP 9.5 |
| Android tối thiểu | API 24 / Android 7.0 |
| Java source/target, Kotlin `jvmTarget` | 11 |
| Kotlin | 2.2.10 (plugin `org.jetbrains.kotlin.jvm` cho `core`/`lwjgl3`; `app` dùng Kotlin tích hợp sẵn của AGP 9) |
| libGDX | 1.14.2, ghim trong `gradle/libs.versions.toml` |

Cài Android Studio có hỗ trợ AGP 9.5, mở **thư mục gốc**, chọn Gradle JDK 25 và đồng bộ Gradle. Cài SDK/Build Tools qua SDK Manager. Đường dẫn SDK nằm trong `local.properties` riêng của máy, không commit. Lần đồng bộ đầu cần mạng để tải dependency; game sau khi cài chạy offline. Không cần tự cài NDK vì dùng native libGDX dựng sẵn.

Cơ sở chọn phiên bản: [AGP 9.5 yêu cầu Gradle 9.6.0 và hỗ trợ API 37](https://developer.android.com/build/releases/agp-9-5-0-release-notes); [Gradle hỗ trợ Java 25 từ 9.1](https://docs.gradle.org/9.1.0/release-notes.html); [libGDX 1.14.2](https://libgdx.com/news/2026/05/gdx-1-14-1). Khả năng tương thích theo tài liệu chưa thay thế cho việc build thực tế dự án này.

## Chạy và tạo APK

Chạy tại thư mục gốc; Windows dùng `gradlew.bat` thay `./gradlew`.

```bash
./gradlew :lwjgl3:run                         # Desktop 1280x720 (16:9)
./gradlew :lwjgl3:run --args="--wide"           # Desktop 1600x720 (20:9)
./gradlew :app:assembleDebug                  # Tạo APK debug
./gradlew :app:installDebug                   # Cài vào thiết bị/máy ảo đã kết nối
adb shell am start -n com.manroid.neonrebels/.AndroidLauncher
```

APK dự kiến tại `app/build/outputs/apk/debug/app-debug.apk`. Trong Android Studio, chọn cấu hình mô-đun `app` và Run trên máy ảo hoặc thiết bị bật USB debugging. Desktop cần OpenGL và phiên đồ họa; trên macOS, task `run` tự thêm `-XstartOnFirstThread`.

## Cách chơi

Luồng màn hình: **Menu chính** → **Bản đồ chiến dịch** → **Chọn đội** → trận → **Kết quả**; **Xưởng nâng cấp** mở được từ Menu, Bản đồ, Chọn đội và Kết quả, rồi quay lại đúng màn đã gọi. Chi tiết quy tắc (mở khóa, đội đúng 2 người, công thức và bảng giá nâng cấp, nhận thưởng chống lặp theo `runId`, lưu có checksum và bản dự phòng, phần chưa triển khai) xem `docs/UI_GAME_LOGIC.md`.

- Chỉ màn `streets_01` (1-1, gồm trận Thiết Giáp Gác Cổng) có gameplay; 14 màn còn lại hiện "Sắp có". Khang là thẻ xem trước, chưa chọn được.
- Chỉ số gốc: An 1200 máu / 100 sát thương / hồi kỹ năng 8 s; Vy 1000 / 60 / 6 s; kẻ địch được nhân theo cùng tỉ lệ. Nâng cấp cấp 0–5 áp dụng từ lượt chơi mới.
- Linh kiện: hạ địch cộng vào quỹ tạm của lượt; chỉ khi thắng mới nhận quỹ tạm + thưởng màn (100, boss 250, +100 lần thắng đầu). Thua hoặc rời màn không nhận gì.
- Ảnh nền 4 màn menu cắt từ `ui/01_UI_Menu_va_tien_trinh.png` bằng `python3 scripts/extract-ui-art.py` (Pillow): giữ hình minh họa, logo và chân dung; nút/khung in sẵn bị xóa hoặc làm mờ vì mọi nút, thẻ, ô màn, thanh thông số và hộp thoại đều là thành phần UI vẽ bằng code (có trạng thái thường/nhấn/vô hiệu/đang xử lý). Tọa độ trong các màn menu là pixel của ảnh nền (gốc trên-trái).
- Chữ tiêu đề/nút dùng font nghiêng `fonts/neon-italic.fnt` (Noto Sans Bold Italic, OFL), sinh cùng `scripts/generate-font.py`.
- Ảnh nền phóng từ ô ~780 px nên hơi mềm; cần bản vẽ gốc độ phân giải cao để sắc nét.

Từ bản đồ chọn **CHƠI**. Vượt đường phố hướng dẫn, robot tuần tra, các bục/vực, điểm sửa chữa tại khoảng x=109, đấu trường từ x=115 đến x=143, rồi trạm phát sóng tại x=154. Lấy đà nhảy trước mép bục; dùng chuỗi bục cao để qua vực rộng. Điểm sửa chữa hồi đầy và hồi sinh cả hai nhân vật **một lần mỗi lượt**. Rơi vực mất 240 HP và trở về vị trí an toàn. Robot tuần tra đứng lại và hiện vạch cam 0,35 giây trước khi đánh: lùi khỏi tầm hoặc đánh trúng robot để hủy đòn.

| Hành động | Desktop | Android |
| --- | --- | --- |
| Đi trái/phải | A/D hoặc mũi tên | Hai nút mũi tên góc trái dưới |
| Nhảy | Space | NHẢY |
| Đánh, giữ để lặp | J | ĐÁNH |
| Né | K | NÉ |
| Kỹ năng | L | KỸ NĂNG |
| Đổi An/Vy | Q | ĐỔI |
| Tạm dừng | Escape | Nút ‖ góc phải trên hoặc nút Back |
| Tiếp tục/chơi lại | Enter ở bảng thông báo | TIẾP TỤC/CHƠI LẠI |
| Về menu | M ở bảng thông báo | MENU CHÍNH |

Nhấn riêng từng lần để nhảy, né, dùng kỹ năng hoặc đổi nhân vật. Có thể giữ di chuyển và đồng thời chạm nhảy/đánh bằng ngón khác. Kéo ra khỏi nút sẽ nhả nút đó. Menu cho phép bật/tắt hình nút cảm ứng; nút tạm dừng vẫn hiện khi tắt.

Màn chiến đấu theo `ui/02_UI_Chien_dau.png`: góc trái có chân dung khung lục giác của nhân vật đang đánh, thanh máu kèm số (ví dụ 1200 / 1200), nhân vật dự bị nhỏ hơn bên dưới; khi boss thức dậy có thanh boss kèm chân dung và cảnh báo đòn; nút tạm dừng vuông ở góc phải. Vùng báo đòn của boss là ô đỏ nhấp nháy có sọc và dấu "!", trùng đúng vùng gây sát thương. Nút cảm ứng: mũi tên trái/phải ở góc trái; cụm phải gồm ĐÁNH/BẮN (to, viền cam, đổi theo nhân vật), NHẢY, ĐỔI (hiện chân dung người sẽ đổi sang), và tầng trên NÉ, KỸ NĂNG (vòng hồi chiêu và số giây). Camera nâng mặt đường lên `ButtonLayout.DECK_TOP` = 240 đơn vị HUD; mọi nút gameplay nằm dưới mức này nên không che nhân vật/kẻ địch. Vùng chạm tròn rộng hơn hình vẽ 10 đơn vị và không chồng nhau. Tắt hiện nút thì camera giữ vị trí cũ và HUD hiện gợi ý phím ở đầu màn.

Logic màn chiến đấu (trạng thái trận, combo, né, đổi, rơi, robot/drone/boss, trạm, hội thoại, hướng dẫn, pause) và bảng thông số: `docs/UI_02_COMBAT_LOGIC.md`.

Hội thoại Linh (cốt truyện/hướng dẫn): khi vào màn và khi boss thức dậy, nhưng chỉ ở các lượt của màn chưa từng thắng. Trong lúc hội thoại, game dừng hẳn như tạm dừng; TIẾP hoặc chạm để sang câu sau, BỎ QUA/Back để bỏ cả đoạn. Câu hướng dẫn điều khiển tự đổi theo nút cảm ứng hay bàn phím. Thế giới dùng chính hình trong ảnh thiết kế: `scripts/build-world-art.py` cắt An (thế kiếm điện), robot khiên, robot lưỡi laser, nền thành phố (tòa hologram, trăng hồng, cầu đổ nát, băng rôn đầu lâu) và mặt cầu đá có ống neon/dây leo từ `ui/04_Gameplay_An.png`, Vy (thế bắn) từ `ui/05_Gameplay_Vy.png`, Thiết Giáp Gác Cổng từ `ui/02_UI_Chien_dau.png`. Nhân vật được tách nền bằng OpenCV GrabCut. Đạo cụ ảnh thiết kế không có (trạm hồi phục, rào năng lượng đấu trường, cửa đấu trường, tháp phát sóng có chảo và đèn nháy, biển cảnh báo vực, drone) được vẽ pixel art cùng bảng màu trong script. Kết quả: atlas `sprites/world.atlas` + `world.png`, `actors.properties` (tâm thân, chiều cao, hướng nhìn), `bg_far.png`, `ground.png`, `ledge.png`; tên vùng là hợp đồng trong `view/SpriteNames.kt` (kiểm bởi `SpriteAssetsTest`). Script cần Pillow, numpy, opencv-python-headless (chỉ khi dựng lại art; kết quả đã nằm trong repo): `python3 -m venv /tmp/art && /tmp/art/bin/pip install pillow numpy opencv-python-headless && /tmp/art/bin/python scripts/build-world-art.py`. Giới hạn: thiết kế chỉ có **một tư thế** cho mỗi nhân vật, nên chuyển động được làm bằng biến đổi (nhún khi chạy, nghiêng khi nhảy, lao/giật khi đánh, bóng mờ khi lướt, nháy trắng khi trúng đòn, ngã và mờ khi gục). Muốn hoạt ảnh từng khung cần họa sĩ vẽ thêm khung với cùng tên vùng trong atlas; pipeline nhân vật nhiều khung được mô tả ở mục cuối README; thêm khung `an_stance`/`vy_stance` đơn thuần chưa kích hoạt chuỗi nhân vật.

An có chuỗi ba đòn kiếm và lướt chém; Vy bắn theo hướng nhìn và bắn xuyên nhiều mục tiêu. Mỗi nhân vật giữ máu và hồi chiêu riêng; đổi nhân vật có thời gian chờ một giây. Nhân vật dự bị không hiện diện và không nhận sát thương. Hết máu tự đổi sang người còn sống, cả hai hết máu thì thua.

Boss luân phiên đấm đất và lao tới. Vạch cảnh báo cùng chữ HUD báo đòn trước khi gây sát thương; nhảy để tránh sóng đất, né khi boss lao tới, phản công trong lúc nghỉ. Dưới 40% máu, thời gian nghỉ ngắn hơn nhưng báo đòn không bị rút ngắn. Cổng chỉ mở khi boss chết; chạm trạm sau cổng mới thắng.

## Tính năng đã triển khai

- Một màn chơi có đầu/cuối, hai nhân vật, robot tuần tra, drone bắn đạn và boss có trạng thái.
- Va chạm AABB tách trục, chia bước di chuyển tối đa 0,15 đơn vị để chống xuyên nền/tường; coyote time và bộ đệm nhảy.
- Mô phỏng cố định 120 Hz, tối đa 12 bước bù; camera giới hạn trong bản đồ rộng 160 đơn vị.
- Hitbox kiếm độc lập hình vẽ, mỗi đòn chỉ trúng mỗi mục tiêu một lần; đẩy lùi, bất tử sau trúng đòn và khi né.
- Kho 64 viên đạn tái sử dụng, thu hồi theo va chạm, thời gian sống và vùng hoạt động; đạn xuyên có mặt nạ mục tiêu đã trúng.
- Menu, HUD hai thanh máu/hồi kỹ năng, thanh máu boss, tạm dừng, thắng/thua, chơi lại bằng `World` mới.
- Xóa trạng thái input khi tạm dừng, đổi màn, resize hoặc mất focus. Trở về ứng dụng vẫn phải chọn TIẾP TỤC; thời gian gameplay và hồi chiêu không chạy trong lúc dừng.
- Tiến trình chiến dịch lưu một ảnh chụp có phiên bản + checksum (kèm bản dự phòng) trong Preferences; cài đặt lưu khóa riêng. Lưu khi tạo/reset chiến dịch, chọn màn, vào trận, nâng cấp, nhận kết quả, đổi cài đặt. Không lưu dở trận. Xem `docs/UI_GAME_LOGIC.md`.
- `FitViewport` 16:9 trong vùng màn hình an toàn: màn 20:9 có khoảng trống hai bên, không kéo giãn/cắt nút. Có xét safe inset của libGDX.
- Hình học neon tạo trực tiếp: màu riêng, chân chuyển động, vệt kiếm/lướt, nhấp nháy trúng đòn, xác địch tạm thời. Asset gồm font UI, bundle văn bản và ảnh nền menu (`assets/ui/`); không quảng cáo, tài khoản, backend hay quyền mạng.

**Ngôn ngữ/âm thanh:** giao diện game mặc định tiếng Việt (`i18n/strings.properties`), tự dùng tiếng Anh (`strings_en.properties`) khi máy đặt ngôn ngữ tiếng Anh. Chữ vẽ bằng font bitmap `fonts/neon-ui.fnt` sinh từ Noto Sans Bold (SIL OFL 1.1, giấy phép kèm tại `fonts/OFL.txt`), đủ dấu tiếng Việt, không cần thư viện native FreeType. Chưa có âm thanh; không tham chiếu tệp âm thanh hoặc sprite chưa tồn tại.

**Asset:** nằm tại `app/src/main/assets/` (Android dùng trực tiếp; `lwjgl3` thêm thư mục này vào resources nên desktop đọc qua classpath). Thêm chuỗi mới: khai báo khóa ở **cả hai** file `.properties`, gọi `ui.tr("khóa")`. Thêm ký tự ngoài bộ hiện có: bổ sung vào `scripts/generate-font.py` rồi chạy `python3 scripts/generate-font.py` (cần Pillow và Noto Sans Bold).

## Cấu trúc và sở hữu tài nguyên

- `core/.../model/`: mô phỏng Kotlin thuần, không import libGDX (`World`, `Player`, `Enemy`, `Boss`, `Level`, `CollisionSystem`, `FixedStepper`, `GameConfig`). Thông số chính nằm trong `GameConfig`.
- `core/.../input/`: ánh xạ phím/cảm ứng thành `Controls`; `PointerButtons` quản lý nhiều ngón độc lập, `ButtonLayout` dùng chung cho vẽ và kiểm tra chạm.
- `core/.../view/`: hình học, HUD, bảng màu và viewport; không quyết định sát thương.
- `MainGame`, `MenuScreen`, `GameScreen`, `SaveManager`: vòng đời màn hình và Preferences. `MainGame` sở hữu/dispose font, batch, shape renderer đúng một lần; màn hình mượn tài nguyên. Không dùng Stage hoặc texture bên ngoài.
- `app/`: Android launcher và tài nguyên Android có sẵn được giữ lại; task `extractGdxNatives` tạo JNI libraries cho arm64-v8a, armeabi-v7a, x86 và x86_64 trong `build/`.
- `lwjgl3/`: desktop launcher, xử lý mất/nhận focus.
- `core/src/test/`, `scripts/test-model.sh`: kiểm thử gameplay có thể chạy không cần libGDX hoặc SDK. `LocalizationTest` kiểm tra hai ngôn ngữ cùng bộ khóa, mọi khóa dùng trong mã đều tồn tại và font có đủ ký tự.

## Kiểm tra và kết quả thực tế

```bash
./scripts/test-model.sh                      # Bash + JDK >= 11 + kotlinc (hoặc kotlin-compiler-embeddable trong cache Gradle)
./gradlew :core:test                         # JUnit 4: bộ hồi quy gameplay + LocalizationTest
./gradlew :core:modelTest                    # Chạy bộ hồi quy trực tiếp qua Gradle
./gradlew :lwjgl3:classes :app:assembleDebug :app:lintDebug
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Kết quả trong môi trường triển khai ngày 06/11/2026:

| Kiểm tra đã thực hiện | Kết quả |
| --- | --- |
| `./scripts/test-model.sh` (sau khi thêm báo đòn robot) | **Đạt 160 kiểm tra**; không có `kotlinc` nên script dùng `kotlin-compiler-embeddable` 2.2.0 trong cache Gradle, `-jvm-target 11` |
| JUnit 4.13.2 từ cache, biên dịch `GameplayTest.kt`/`ModelChecks.kt` bằng Kotlin 2.2.0 và chạy `org.junit.runner.JUnitCore com.manroid.neonrebels.model.GameplayTest` | **Đạt: `OK (1 test)`**, bytecode Java 11 (major 55); không chạy qua Gradle |
| JUnit chạy `LocalizationTest` (biên dịch cùng `FontSpec.kt`) | **Đạt: `OK (3 tests)`** |
| Dựng ảnh mô phỏng menu/HUD/bảng thua bằng Python từ chính `.fnt`/`.png` và bundle, theo tọa độ trong mã | Chữ tiếng Việt và tiếng Anh nằm trong nút/panel; đây là mô phỏng, không phải ảnh chụp từ libGDX |
| `./gradlew --offline :core:compileKotlin` | Gradle khởi động được nhưng dừng: plugin `org.jetbrains.kotlin.jvm:2.2.10` chưa có trong cache, không có mạng. libGDX cũng chưa có trong cache |
| Parse XML bằng `ElementTree`, TOML bằng `tomllib`, kiểm tra launcher ngang/không xin quyền, `bash -n scripts/test-model.sh` | Đạt |
| `./gradlew --version` | Không hoàn tất: cache wrapper mặc định chỉ đọc |
| Gradle 9.6.0 đã cài, dùng `GRADLE_USER_HOME=/tmp/nova-gradle-home`, chạy `:core:modelTest :lwjgl3:classes :app:assembleDebug --offline --no-daemon --console=plain` | Bị chặn lúc khởi tạo: `Could not determine a usable wildcard IP for this machine`; chưa tới bước biên dịch |
| `curl -I` tới Maven Central | Shell không phân giải được DNS; cache không có libGDX |
| `adb devices` | ADB không mở được socket: `Operation not permitted`; chưa xác minh thiết bị |

Ngày 09/11/2026 (có mạng):

| Kiểm tra đã thực hiện | Kết quả |
| --- | --- |
| `./gradlew :app:assembleDebug` (trước khi sửa) | Lỗi cấu hình: AGP 9 cấm thêm `Provider` vào `sourceSets.jniLibs.srcDir` |
| Sửa `app/build.gradle.kts`: nối `extractGdxNatives` qua `androidComponents.onVariants { sources.jniLibs?.addGeneratedSourceDirectory(...) }` rồi `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL**; APK chứa `libgdx.so` cho arm64-v8a, armeabi-v7a, x86, x86_64 |
| `./gradlew :core:test :lwjgl3:jar` | **BUILD SUCCESSFUL** |

Bộ hồi quy kiểm tra vật lý, nhảy/coyote/buffer, đổi nhân vật và chết tự đổi, bất tử/né/rơi vực, kiếm một-hit-mỗi-mục-tiêu, cooldown, đạn xuyên/thu hồi, robot/drone, chu kỳ boss và tăng tốc, hồi phục, thắng/thua, reset lượt chơi, pause và số bước bù, quản lý nhiều ngón. Các ca nhảy dùng đúng trọng lực/tốc độ để kiểm tra đường qua bục. Một ca điều khiển nhân vật với HP/sát thương mặc định đánh bại boss và đi tới trạm để thắng.

**Giới hạn kiểm chứng:** phần libGDX/Android đã biên dịch và tạo được APK debug, nhưng chưa chạy OpenGL (chưa xác nhận libGDX đọc được `neon-ui.fnt` và `I18NBundle` trên thiết bị, chưa thấy chữ thật trên màn hình), chưa thử multi-touch trên thiết bị thật, Preferences qua khởi động lại hoặc native libraries trên từng ABI. Kiểm thử pointer/khung nút là kiểm thử logic, không thay thế nghiệm thu màn hình thật. Các kiểm thử Android mẫu có sẵn chỉ kiểm tra package; không phải kiểm thử UI game.

## Danh sách nghiệm thu trên máy có môi trường build

1. Sync, chạy desktop ở 16:9 và `--wide` 20:9; mở màn từ menu, resize, kiểm tra chữ/nút không bị cắt.
2. Android: giữ mũi tên phải và nhấn NHẢY/ĐÁNH bằng ngón khác; xác nhận nút không che nhân vật/kẻ địch và ngón cái chạm thoải mái; kéo ngón khỏi nút; thử hai ngón cùng một nút rồi nhả từng ngón.
3. Giữ di chuyển/đánh rồi bấm nút tạm dừng, chuyển ứng dụng hoặc mở bảng thông báo; quay lại phải đứng yên đến khi TIẾP TỤC và không bị kẹt nút.
4. Thử combo An, đạn xuyên Vy, đổi giữa không trung, cooldown riêng, tự đổi khi một nhân vật chết; kiểm tra rơi vực và điểm hồi phục một lần.
5. Thắng boss, đi tới trạm; để cả hai chết rồi CHƠI LẠI; xác nhận không giữ đạn/kẻ địch/cổng/trạng thái cũ.
6. Cài mới → CHƠI MỚI → Bản đồ → Chọn đội → VÀO TRẬN; tắt/mở ứng dụng → TIẾP TỤC về bản đồ; đi Xưởng từ Chọn đội rồi quay lại (giữ đội nháp và màn); mua nâng cấp rồi mở lại ứng dụng; nhấn nhanh nhiều lần không mở nhiều màn/mua trùng; HỦY ở hộp thoại CHƠI MỚI không mất tiến trình; mở Xưởng từ bảng kết quả rồi quay lại không nhận thưởng thêm; kiểm tra chữ tiếng Việt rõ nét ở menu, HUD và nhãn trong thế giới (đổi ngôn ngữ máy sang tiếng Anh để xem bản Anh); kiểm tra APK trên arm64 và emulator x86_64.

## Thay placeholder và mở rộng

1. Thêm sprite atlas vào `app/src/main/assets/` (đã dùng chung cho `app` và `lwjgl3`); thay hàm vẽ trong `WorldRenderer` bằng `SpriteBatch`/animation, giữ nguyên hitbox và mô phỏng.
2. Thêm âm thanh thật và chủ sở hữu tài nguyên rõ ràng; giữ khả năng chơi khi âm thanh tắt/không có.
3. Chuyển dữ liệu `Level.platforms` và vị trí kẻ địch sang dữ liệu màn, rồi thêm màn mới. Kiểm thử lại tầm nhảy, điểm an toàn và đấu trường trước khi tăng nội dung.
4. Sau khi chạy trên thiết bị, chỉnh kích thước/vị trí nút (`ButtonLayout.CX/CY/R`), độ khó và hiệu ứng phản hồi theo trải nghiệm thực tế.


## Pipeline hoạt ảnh nhân vật nhiều khung

Phần code đã hỗ trợ chuyển từng clip sang **ảnh tư thế độc lập** trong atlas tùy chọn
`app/src/main/assets/sprites/characters.atlas`. Chưa thêm ảnh nhân vật mới trong thay đổi này.
**Hiện không có clip An/Vy nhiều khung thật**: cả 22 clip vẫn dùng fallback ảnh cũ/uốn lưới.
Hai khung `drone_fly` trong `world.atlas` là hoạt ảnh có sẵn của drone, không phải bộ chạy An/Vy.
Robot/boss và gameplay giữ nguyên.

Các lớp chính: `view/animation/AnimationClip.kt` (manifest, thời lượng, đoạn pha),
`CharacterAnimator.kt` (chọn trạng thái sau mỗi fixed step), `CharacterFrames.kt`
(tải clip, socket, vẽ `AtlasSprite` có trim/rotate). `WorldRenderer` dùng frame độc lập khi
**toàn bộ** clip có đủ region và canvas hợp lệ; clip thiếu/quy tắc sai dùng fallback nguyên chuỗi,
không trộn ảnh mới/cũ giữa các frame. Chỉ bản debug ghi lý do một lần mỗi clip mỗi lần tải;
không ghi từ hàm render. Death thiếu hình vẫn dùng xác nghiêng/tối của renderer cũ, không dùng idle.

### Asset còn thiếu và thứ tự đưa vào

| Giai đoạn | Chuỗi An cần vẽ | Số khung dự kiến | Chuỗi Vy cần vẽ | Số khung dự kiến |
| --- | --- | --- | --- | --- |
| 1 | `idle`, `run` | 4, 8 | `idle`, `run` | 4, 8 |
| 2 | `jump`, `fall`, `land` | 3, 3, 3 | `jump`, `fall`, `land` | 3, 3, 3 |
| 3 | `attack1`, `attack2`, `attack3` | 6 mỗi chuỗi | `shoot` | 3 |
| 3 | `hurt`, `death` | 3, 6 | `hurt`, `death` | 3, 6 |
| 4 | `dodge`, `skill` | 4, 6 | `dodge`, `skill` | 4, 6 |

**Tất cả ô trong bảng đang thiếu ảnh độc lập.** Làm An idle/run trước, nghiệm thu chân/tỷ lệ/neo,
sau đó jump/fall/land, các đòn/hurt/death và cuối cùng Vy/kỹ năng. Không xóa fallback hoặc ảnh nguồn
trước khi clip thay thế đã được xem trong preview và thử trong trận. Đây là danh sách yêu cầu art,
không phải tuyên bố đã có animation hoàn thiện.

### Quy ước vẽ, cấu hình và đóng atlas

Nguồn mỗi frame là PNG RGBA trong suốt, tên `an_idle_00.png`, `an_idle_01.png`,
`an_run_00.png`…; Vy dùng `vy_*`. Vẽ độc lập từng tư thế; không nhân bản, xoay hoặc kéo giãn
một ảnh thành bộ chạy. Giữ thiết kế, độ sáng, màu quần áo, tỷ lệ đầu/thân và tỷ lệ nhân vật;
không cắt chặt từng ảnh theo bounding box rồi tự đặt chân ở đáy. Giữ canvas và vị trí chân nhất quán,
chừa khoảng cho toàn bộ kiếm/súng/tay. Script kiểm tra hình thức không đánh giá được chất lượng tư thế
hoặc phát hiện mọi kiểu biến đổi giả; vẫn phải xem chuyển động thật.

Manifest `sprites/animations.properties` là danh sách **asset cần có**, không chứng nhận asset tồn tại.
Ví dụ cấu hình mặc định cho An idle:

```properties
an.idle.frames=an_idle_00,an_idle_01,an_idle_02,an_idle_03
an.idle.fps=6
an.idle.end=LOOP
an.idle.canvas=256,192
an.idle.height=3.2
an.idle.anchor=96,16
an.idle.facesRight=true
```

- `frames`: danh sách tên theo hậu tố số tăng dần, không trùng. Mỗi clip cần ít nhất hai khung.
  Có thể đổi số lượng trong manifest trước khi nhập ảnh.
- `fps`: FPS mặc định (12 nếu bỏ trống); tùy chọn `durations=.10,.15,.10,.20` ghi giây cho từng khung, cùng số lượng.
- `end`: `LOOP` cho idle/run; `STATE` cho jump/fall/land/dodge/attack/shoot/skill/hurt (hết hình giữ
  khung cuối tới khi gameplay đổi trạng thái); `HOLD` cho death (giữ khung cuối).
- `canvas`: kích thước **gốc trước trim/rotate**; toàn bộ frame của clip phải khớp. `height` là chiều
  cao cả canvas theo đơn vị thế giới, không phải collider. Cùng chiều cao nhân vật trên các canvas;
  mặc định 192 px / 3.2 đơn vị = 60 px/đơn vị; cơ thể cao khoảng 150 px sẽ tương ứng 2.5 đơn vị như ảnh An cũ. Điều chỉnh theo art thực tế trước nghiệm thu.
- `anchor`: tọa độ pixel trên canvas gốc, gốc dưới trái, đặt tại tâm chân/collider. Điểm này luôn
  được đặt ở `(Player.centerX(), Player.y)`. Không dịch model để sửa một ảnh đặt neo sai.
- Override theo frame khi thật cần: `an_run_03.anchor=96,16`; ưu tiên sửa art/canvas nhất quán.
- Socket tùy chọn theo clip hoặc từng region: `an.attack1.hand=x,y`, `an.attack1.tip=x,y`,
  `an_attack1_02.tip=x,y`, `vy.shoot.muzzle=x,y`. Đơn vị/canvas cùng quy tắc với anchor.
  Thiếu hand/tip chỉ tắt sét bám kiếm; thiếu muzzle chỉ tắt chớp nòng, không ẩn đạn đang bay.
- Đạn vẫn sinh trong `World.shoot` từ `Player.muzzleX/Y`, **không từ render hoặc metadata art**.
  Khi vẽ Vy, canh socket nòng súng vào tâm viên đạn logic ở preview (ô cyan); muzzle mặc định là
  mục tiêu authoring gần điểm hiện có, cần kiểm tra cả hai hướng. Không sửa tầm đạn/hitbox để chạy
  theo kích thước ảnh. Các socket là điểm gắn hiệu ứng; không quyết định sát thương/cooldown.
- Attack An cần `phaseEnds=2,4` với 6 khung: startup dùng khung 0–1, active 2–3, recovery 4–5.
  Đây là chỉ số kết thúc **không bao gồm**. Thay số khung thì đổi ranh giới tương ứng. Renderer
  ánh xạ tiến độ từng pha gameplay vào đoạn hình bằng trọng số thời lượng frame; FPS không thay
  tốc độ gây sát thương. Dodge/lướt An cũng khớp tiến độ hành động gameplay.

Nhập từng bộ đã vẽ bằng Pillow (công cụ không vẽ hay sinh tư thế):

```bash
python3 scripts/pack-character-animations.py --source /path/to/independent-pngs --check
python3 scripts/pack-character-animations.py --source /path/to/independent-pngs
python3 scripts/test-character-packer.py
```

Nguồn phải chứa đầy đủ frame của mỗi clip muốn nhập; clip không có file nào được bỏ qua và giữ
fallback. Có một phần clip thì từ chối. PNG sai canvas, nền không trong suốt, rỗng hoặc trùng pixel
bị từ chối. Importer không resize/trim/rotate/chỉnh màu, dùng padding và filtering `Nearest`;
kết quả gồm `characters.atlas`, `characters-0.png`… tại assets/sprites. Mỗi lần chạy dựng lại atlas
nhân vật từ **toàn bộ thư mục nguồn**: giữ các PNG của clip đã nhập trước đó trong cùng nguồn;
không chạy lại `build-world-art.py` để tạo hoạt ảnh nhân vật.

Có thể dùng TexturePacker ngoài thay importer: giữ tên đầy đủ `an_run_00` hoặc cặp name/index
`an_run` + `index: 0`. Runtime hỗ trợ trim và pack rotate 90° qua `AtlasSprite`, giữ offset trong
canvas gốc và mirror điểm neo/socket. Các góc pack khác bị từ chối về fallback. Không dùng
`TextureRegion` kéo trực tiếp theo packed size cho nhân vật mới. Texture nhân vật mới dùng
`Nearest`; ảnh vẽ cũ vẫn giữ filtering hiện có.

### Clock và preview debug

Bộ chọn animation được cập nhật **sau từng fixed step**, không reset mỗi render. Đổi nhân vật,
đổi trạng thái, bắt đầu đòn mới hoặc phát bắn mới mới reset clock. Run thay tốc độ theo `abs(vx)`
so với `GameConfig.SPEED`, kẹp 0.5–1.5×. Jump/fall/land độc lập. Hành động ưu tiên theo model;
animation bị ngắt khi trúng đòn/chết/đổi người không tiếp tục tạo hit hoặc phát đạn.
`actionSerial`/`shotSerial` trên Player chỉ là tín hiệu hiển thị, không đổi gameplay.

Pause, hội thoại và intro không chạy clock thế giới. Riêng sau thua, mô phỏng đã kết thúc:
cho death phát nốt bằng thời gian trình bày giới hạn delta, rồi giữ khung cuối; ra nền đóng băng
cả đoạn này. Chơi lại tạo renderer/clock mới. Preview có Player riêng, không tạo run, không lưu
chiến dịch, không có AI hoặc đạn gây sát thương.

```bash
./gradlew :lwjgl3:run --args="--animation-preview"
./gradlew :lwjgl3:run --args="--animation-preview --wide"
./gradlew :lwjgl3:run --args="--debug"    # Trong trận nhấn F4
```

Android debug: Tạm dừng → Cài đặt → XEM HOẠT ẢNH (DEBUG). Android release không có nút này.
Trong preview có nút chạm để đổi An/Vy, chuỗi trước/sau, phát/dừng, từng frame, tốc độ,
lật hướng và bắt đầu lại. Phím: Tab đổi nhân vật; ↑/↓ đổi chuỗi; ←/→ từng frame (tự dừng);
Space phát/dừng; `+` đổi tốc độ 0.25/0.5/1/2×; F lật hướng; R từ đầu; Esc quay lại.
Preview tôn trọng clip một lần/giữ cuối; dùng TỪ ĐẦU để xem lại. Header hiển thị region/frame
**thực sự được vẽ**, cùng tên clip yêu cầu và trạng thái `atlas` hoặc lý do fallback.
Đường ngang là mặt đất; dấu vàng là điểm neo, khung xanh lá là collider, vàng là hitbox kiếm đang
active, chấm hồng là socket, cyan là vị trí/diện tích đạn logic của Vy. Fallback thiếu hình chỉ có
một region thật dù manifest yêu cầu nhiều khung.

Nghiệm thu bằng mắt: đứng tại đường mặt đất, xem từng frame trái/phải và lúc lặp; chân không
nhảy vị trí/trượt, tỷ lệ và palette ổn định, vũ khí không bị cắt. Sau đó thử trong trận ở 16:9/20:9,
nhảy → rơi → tiếp đất, combo 1/2/3, đổi người, pause/hội thoại, hurt/death. Chỉ bỏ fallback của clip
sau khi các bước này đạt. Kiểm thử logic không chứng minh chất lượng nét vẽ/chuyển động.

Kiểm tra trong phiên triển khai pipeline: biên dịch bằng Kotlin 2.2.10 + libGDX 1.14.2 đã có trong
cache, target JVM 11, toàn bộ core/test và hai launcher desktop/Android (API 37.0); JUnit đạt 18 test (gồm 359 assertion
gameplay + 111 tiến trình và kiểm tra mới về clock, pha, fallback, trim/rotate/flip). Gradle Wrapper
bị chặn vì cache chỉ đọc. Thử mở desktop preview bị `GLFW_PLATFORM_UNAVAILABLE` / `Unable to
initialize GLFW`; bộ kiểm tra importer Python đạt 3 test với ảnh thử tổng hợp chỉ ở thư mục tạm; chưa xem được cửa sổ, chưa có video, chưa build lại APK và chưa nghiệm thu
chuyển động thật. Atlas nhân vật tùy chọn chưa có vì chưa có bộ tư thế độc lập được cung cấp.
