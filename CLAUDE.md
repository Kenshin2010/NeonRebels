# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`AGENTS.md` là hướng dẫn làm việc chính (phạm vi bản thử, quy tắc gameplay, điều khiển, lưu dữ liệu, checklist nghiệm thu) — đọc nó trước khi sửa. **Luôn phản hồi người dùng bằng tiếng Việt.** `README.md` ghi tiến độ, phiên bản và kết quả kiểm chứng thực tế; cập nhật README (không phải AGENTS.md) khi thay đổi build, điều khiển, tính năng hoặc giới hạn.

## Quy tắc tóm tắt từ AGENTS.md

Phần dưới tóm tắt các quy tắc ảnh hưởng trực tiếp tới việc viết mã; khi có xung đột, `AGENTS.md` là chuẩn.

**Quy trình**
- Kiểm tra mã thực tế trước khi sửa; mở rộng thành phần đã có thay vì tạo trùng hoặc ghi đè. Không chạy công cụ sinh dự án libGDX đè lên repo.
- Giữ nguyên thay đổi không liên quan; không viết lại kiến trúc để sửa việc nhỏ. Nêu kế hoạch ngắn rồi làm, không dừng giữa chừng để hỏi tiếp tục; chỉ hỏi khi thiếu thông tin làm đổi phạm vi đáng kể hoặc hành động phá hủy.
- Thiếu SDK/dependency/công cụ: ghi rõ trở ngại, tiếp tục phần độc lập. Không commit, push hay phát hành nếu chưa được yêu cầu.
- Bàn giao: đã đổi gì, file chính, kiểm tra đã chạy và kết quả, phần chưa kiểm chứng.

**Phạm vi bản thử**: một màn hoàn chỉnh (An, Vy, robot tuần tra, drone, boss Thiết Giáp Gác Cổng, menu/HUD/pause/thắng/thua/chơi lại, lưu thiết lập). Không thêm multiplayer, backend, đăng nhập, quảng cáo, thanh toán, analytics, kinh tế/trang bị phức tạp hay nhiều khu vực nếu chưa được yêu cầu. Không thêm quyền Android không cần thiết.

**Kịch bản**: thành phố Nova bị robot chiếm đóng sau khi hệ thống AI bị can thiệp. An và Vy vượt khu phố mất điện để kích hoạt trạm phát sóng và tìm Linh, em gái An. Tên hiển thị: NEON REBELS (tên phụ Biệt Đội Nổi Loạn) — dùng trong `app_name`, tiêu đề cửa sổ desktop, menu và README. Menu hiện cả tên phụ "BIỆT ĐỘI NỔI LOẠN". Khóa Preferences `nova-light-core-v1` giữ tên cũ có chủ đích để không mất dữ liệu đã lưu.
- **An**: kiếm cận chiến, combo 3 đòn, kỹ năng lướt chém.
- **Vy**: bắn đạn năng lượng theo hướng nhìn, kỹ năng bắn xuyên.
- **Boss Thiết Giáp Gác Cổng**: luân phiên đấm đất và lao tới, có báo đòn → tấn công → hồi phục.

**Trình tự màn đầu** (tọa độ x theo đơn vị thế giới, bản đồ rộng 160; dữ liệu trong `Level.kt`/`World.kt`):
1. Khu hướng dẫn di chuyển và nhảy: x 0–24 (HUD hiện gợi ý phím khi x < 24).
2. Robot tuần tra để học chiến đấu: robot tại x 29 và 39.
3. Bục cao và vực: vực tại x 48–54 và 70–77, chuỗi bục 44–77.
4. Khu chiến đấu robot + drone: drone tại 58 và 91, robot tại 80 và 101.
5. Điểm hồi phục trước boss: x 107–112, hồi đầy và hồi sinh cả hai **một lần mỗi lượt**.
6. Đấu trường boss: x 115–143; vào x > 115 thì boss thức dậy và cửa x 113 đóng lại; cổng x 143 chỉ mở khi boss chết.
7. Trạm phát sóng kết thúc màn: x 154–157.

Đường chính phải đi được với bất kỳ nhân vật nào còn sống; kiểm thử `route()` trong `ModelChecks` xác minh các bước nhảy qua bục. Khi đổi bố cục màn, cập nhật cả tọa độ ở đây, README và `route()`.

**Bất biến gameplay phải giữ** (đã có kiểm thử trong `ModelChecks`, thêm kiểm thử khi đụng tới):
- Thắng chỉ khi boss chết **và** chạm trạm phát sóng; thua khi cả hai nhân vật hết máu.
- Đổi nhân vật giữ vị trí/hướng/vận tốc; máu và hồi chiêu riêng; nhân vật dự bị không nhận sát thương nhưng hồi chiêu vẫn giảm; hủy hitbox cận chiến khi đổi, đạn đã bắn vẫn sống; chết thì tự đổi, bỏ qua thời gian chờ.
- Mỗi lần vung kiếm / mỗi viên đạn xuyên chỉ gây sát thương một lần lên mỗi mục tiêu (mặt nạ bit theo `Enemy.id`).
- Rơi vực: trừ máu một lần, đưa về vị trí an toàn, không hồi sinh nhân vật đã chết.
- Không xuyên nền/tường kể cả khi lướt; không nhảy vô hạn; coyote time và jump buffer cấu hình được.
- Pause dừng vật lý, AI, đạn, sát thương và bộ đếm; trở lại từ nền phải chờ người chơi bấm tiếp tục; không mô phỏng bù thời gian nằm nền.
- Thực thể chết không tấn công/nhận sát thương; drone bắn theo hồi chiêu; robot không tự đi xuống vực; vùng báo đòn của boss khớp vùng sát thương, dưới 40% máu chỉ giảm thời gian nghỉ.
- Không xóa đạn chỉ vì ra ngoài camera; không cấp phát hay tạo texture mỗi frame.

**Input**: tách khỏi gameplay; theo dõi từng pointer; giải phóng trạng thái nhấn khi touchUp/touchCancelled, mất focus, đổi màn và pause; chuyển tọa độ chạm qua viewport HUD. Một lần nhấn không được lặp qua nhiều bước fixed update. Phím desktop: A/D hoặc mũi tên, Space nhảy, J đánh, K né, L kỹ năng, Q đổi, Escape tạm dừng.

**Quy ước mã**
- Kotlin, thụt lề 4 dấu cách; `PascalCase` lớp, `camelCase` hàm/thuộc tính, package chữ thường, `snake_case` cho tài nguyên Android.
- Thông số máu/tốc độ/sát thương/hồi chiêu đặt trong `GameConfig`, không rải magic number. Chỉ bình luận khi cần giải thích lý do.
- State machine đơn giản; không ECS, không framework AI, chỉ dùng Box2D khi thật cần.
- Dependency khai báo qua `gradle/libs.versions.toml`, ghim phiên bản (không dùng `+`). Không thêm ktlint/Detekt hay công cụ định dạng mới.
- Văn bản UI Android ở `res/values/strings.xml`; UI libGDX dùng bundle bản địa hóa trong assets; gameplay không phụ thuộc Android Resources/Context. Hiển thị tiếng Việt cần font đủ dấu; nếu tạm dùng tiếng Anh thì ghi rõ trong README.
- Preferences: có giá trị mặc định, chỉ flush khi đổi thiết lập hoặc xong màn; không lưu đạn/hitbox/nút đang nhấn; chơi lại không xóa thiết lập.
- Kiểm thử: JUnit 4, tên lớp kết thúc bằng `Test`, tên hàm mô tả hành vi; không viết test chỉ kiểm tra hằng số. Build thành công không phải bằng chứng gameplay đúng — gameplay libGDX cần thử trên thiết bị/emulator theo checklist trong `AGENTS.md`.

## Trạng thái cần biết

- Mô tả "chỉ có bộ khung, chưa có Activity" trong `AGENTS.md` đã lỗi thời: gameplay, màn hình và hai launcher đều đã có.

- Toàn bộ mã nguồn đã chuyển sang **Kotlin** (file `.kt` vẫn đặt trong `src/*/java/`). Không thêm file Java mới. `core`/`lwjgl3` dùng plugin `org.jetbrains.kotlin.jvm` 2.2.10; `app` dùng Kotlin tích hợp sẵn của AGP 9 — **không** áp dụng `org.jetbrains.kotlin.android` cho `app`. Phiên bản Kotlin được ghim khớp với KGP mà AGP 9.5.1 phụ thuộc.
- Kiểm thử gameplay nằm ở `core/src/test/`, không phải `app/src/test/` (ở đó chỉ có test mẫu).
- Thư mục không phải Git repository.
- Từ 09/11/2026 Gradle build được (cần mạng lần đầu; `--offline` thiếu `aapt2`): `:app:assembleDebug`, `:core:test`, `:lwjgl3:jar` đều thành công. Chưa chạy game trên thiết bị/desktop — không tuyên bố gameplay chạy đúng nếu chưa thực sự chạy. AGP 9 cấm `Provider` trong `android.sourceSets`; thư mục sinh ra phải nối qua `androidComponents.onVariants { variant.sources.* .addGeneratedSourceDirectory(...) }`.

## Lệnh

```bash
./scripts/test-model.sh          # Bộ hồi quy gameplay không cần Gradle/libGDX: JDK + kotlinc, hoặc kotlin-compiler-embeddable trong cache Gradle
./gradlew :core:test             # JUnit 4: GameplayTest gọi ModelChecks.runAll()
./gradlew :core:modelTest        # Chạy ModelChecks trực tiếp qua JavaExec
./gradlew :lwjgl3:run            # Desktop 1280x720; thêm --args="--wide" cho 20:9
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/
./gradlew :app:lintDebug
./gradlew :app:installDebug
```

Phiên bản thực tế: Gradle 9.6.0, AGP 9.5.1, compile/target SDK 37, min SDK 24, Java/Kotlin target 11, daemon JDK 25, Kotlin 2.2.10, libGDX 1.14.2 (ghim trong `gradle/libs.versions.toml`). Không đổi các phiên bản này chỉ theo sở thích.

**Chạy một kiểm thử đơn lẻ:** không có test riêng lẻ — toàn bộ ~160 assertion gameplay nằm trong `ModelChecks` (Kotlin `object`) (các nhóm `movement()`, `jumping()`, `swapping()`, `boss()`…, gọi tuần tự trong `runAll()`), dừng ở assertion đầu tiên thất bại với thông điệp mô tả. Thêm kiểm thử hồi quy mới bằng hàm `check(...)`/`near(...)` trong nhóm phù hợp. `scripts/test-model.sh` chỉ biên dịch `model/*`, `progress/*` cùng `input/ButtonLayout.kt`, `input/PointerButtons.kt`, `ModelChecks.kt` và `ProgressChecks.kt` — nếu các lớp đó bắt đầu import libGDX hay lớp khác, script sẽ hỏng. Logic 4 màn menu (chiến dịch, đội, nâng cấp, thưởng, lưu) mô tả ở `docs/UI_GAME_LOGIC.md`; logic chiến đấu (CombatSession, combo, boss, hội thoại, hướng dẫn) ở `docs/UI_02_COMBAT_LOGIC.md`.

## Kiến trúc

Mã gameplay ở `core/src/main/java/com/manroid/neonrebels/`:

- **`model/` — mô phỏng Kotlin thuần, không phụ thuộc libGDX.** Đây là ranh giới quan trọng nhất: giữ nó không import `com.badlogic.*` để kiểm thử chạy được chỉ với trình biên dịch Kotlin.
  - `World` sở hữu toàn bộ trạng thái một lượt chơi (`Level`, `Player`, `Boss`, mảng `enemies`, pool 64 `Projectile` tái sử dụng, `result`, `paused`, `time`). `World.update(Controls, dt)` là một bước cố định. **Chơi lại = tạo `World` mới** (qua `game.play()`), không reset tại chỗ.
  - `Player` giữ cả An và Vy (máu/hồi chiêu riêng); chỉ một nhân vật hiện diện. `Enemy` là lớp cơ sở cho `PatrolRobot`, `Drone`; `Boss` là state machine riêng.
  - `CollisionSystem.move` làm AABB tách trục, chia nhỏ bước di chuyển để chống xuyên tường khi lướt. Cổng đấu trường trong `Level` biến mất khi `level.bossDefeated`.
  - `FixedStepper` chạy `World.update` ở 120 Hz, kẹp delta và số bước bù (`GameConfig.STEP`, `MAX_STEPS`); trả 0 bước và xóa input khi pause hoặc đã có kết quả.
  - `GameConfig` chứa mọi thông số gameplay — thêm hằng số ở đây thay vì magic number.
  - `Controls` là ý định trung lập nền tảng: `left/right` là trạng thái giữ; `jump/attack/dodge/skill/swap` là cờ "edge" tồn tại cho tới khi đúng một bước fixed update tiêu thụ (tránh lặp một lần nhấn qua nhiều bước).
- **`input/`** — `InputController` (InputProcessor) ánh xạ bàn phím và cảm ứng vào `Controls`; `PointerButtons` theo dõi từng pointer cho multi-touch; `ButtonLayout` là nguồn chung cho cả vẽ nút (HUD) và hit-test chạm, toạ độ qua viewport HUD.
- **`view/`** — `WorldRenderer` vẽ hình học neon bằng `ShapeRenderer` (chỗ sẽ thay bằng sprite sheet), `GameHud`, `SafeViewport` (FitViewport 16:9 trừ safe inset), `Palette`. View chỉ đọc `World`, không quyết định sát thương.
- **Màn hình & vòng đời** — `MainGame` sở hữu và dispose đúng một lần font/batch/shape renderer cùng `SaveManager` (libGDX Preferences); các Screen chỉ mượn. `GameScreen.render`: `input.sample()` → `clock.advance(world, controls, delta)` → khi có kết quả thì pause overlay và lưu khi thắng. `pause()`/mất focus vào trạng thái overlay, xóa input, reset accumulator; `resume()` không tự chạy tiếp — người chơi phải bấm RESUME.

**Văn bản & font**: không viết cứng chữ giao diện. Chữ nằm trong `app/src/main/assets/i18n/strings.properties` (tiếng Việt, mặc định) và `strings_en.properties`; gọi qua `UiPainter.tr("khóa")` (`MainGame.text` là `I18NBundle`). Thêm khóa vào **cả hai** file. Font là bitmap `fonts/neon-ui.fnt` sinh bởi `scripts/generate-font.py` từ Noto Sans Bold (OFL); ký tự mới phải được thêm vào script rồi sinh lại. `UiPainter` nhân tỉ lệ chữ với `FontSpec.UNIT` vì bố cục được canh cho font 15px. `LocalizationTest` bắt lỗi thiếu khóa/thiếu glyph. Assets dùng chung: `lwjgl3` thêm `app/src/main/assets` vào resources.

Launcher: `app/.../AndroidLauncher.kt` (immersive, chuyển `onWindowFocusChanged` thành `game.pause()/resume()` trên render thread) và `lwjgl3/.../DesktopLauncher.kt` (`object` với `@JvmStatic main`). Native libGDX cho Android được giải nén bởi task `extractGdxNatives` trong `app/build.gradle.kts` vào `build/generated/gdxJniLibs` (không dùng NDK). Hình nền menu cắt từ ảnh thiết kế (`scripts/extract-ui-art.py`); sprite và nền thế giới cắt từ `ui/02`, `ui/04`, `ui/05` bằng `scripts/build-world-art.py` (cần opencv/numpy, chỉ khi dựng lại), tên vùng khớp `view/SpriteNames.kt`.
