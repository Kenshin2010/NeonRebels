# Hướng dẫn đóng góp — NeonRebelS

## Ngôn ngữ phản hồi

Luôn phản hồi người dùng bằng tiếng Việt, bao gồm thông báo tiến độ,
câu hỏi làm rõ và câu trả lời cuối cùng.

Giữ nguyên lệnh, đường dẫn và định danh mã nguồn khi cần để bảo đảm
tính chính xác.

## Mục tiêu dự án

NeonRebelS là game hành động đi cảnh 2D offline dành cho Android.

- Tên hiển thị: NEON REBELS.
- Tên phụ: Biệt Đội Nổi Loạn.
- Màn hình ngang.
- Ngôn ngữ chính: Kotlin.
- Engine hiện dùng: libGDX.
- Ưu tiên điều khiển cảm ứng, gameplay ổn định và khả năng mở rộng.

Khi được yêu cầu triển khai, hãy sửa trực tiếp các file trong repository.
Không chỉ đưa kế hoạch hoặc các đoạn code rời rạc.

## Cấu trúc dự án hiện có

Dự án đã có ba mô-đun `app`, `core`, `lwjgl3`; namespace và applicationId
Android đều là `com.manroid.neonrebels`. Gameplay, giao diện và hai launcher
đã được triển khai; không coi dự án là bộ khung mới.

- Gameplay Kotlin và giao diện libGDX: `core/src/main/java/com/manroid/neonrebels/`
  (file `.kt` đặt trong thư mục `java`).
- Khởi chạy Android: `app/src/main/java/`; desktop: `lwjgl3/src/main/java/`.
- Tài nguyên Android: `app/src/main/res/`.
- Manifest: `app/src/main/AndroidManifest.xml`.
- Kiểm thử gameplay, tiến trình, bản địa hóa và sprite: `core/src/test/java/`.
- Kiểm thử Android mẫu: `app/src/test/java/`, `app/src/androidTest/java/`;
  hiện chỉ kiểm tra phép cộng/package, không kiểm chứng gameplay.
- Asset dùng chung: `app/src/main/assets/`; desktop thêm thư mục này vào resources.
- Ảnh thiết kế nguồn: `ui/`; công cụ dựng asset và kiểm thử độc lập: `scripts/`.
- Logic menu/tiến trình: `docs/UI_GAME_LOGIC.md`.
- Logic chiến đấu: `docs/UI_02_COMBAT_LOGIC.md`.
- Cấu hình build: Gradle Kotlin DSL.
- Danh mục phiên bản: `gradle/libs.versions.toml`.

Phải kiểm tra repository thực tế trước khi triển khai. Nếu các thành phần
đã tồn tại, mở rộng chúng thay vì tạo trùng hoặc ghi đè.
Khi tài liệu mô tả khác mã nguồn, đối chiếu lớp triển khai và kiểm thử trước;
không dùng mô tả cũ trong README hoặc CLAUDE.md để thay đổi hành vi ngoài nhiệm vụ.

## Nguyên tắc tích hợp libGDX

- Giữ mô-đun `app` làm ứng dụng Android và launcher.
- Giữ namespace và applicationId hiện có.
- Tiếp tục dùng Kotlin; không chuyển toàn bộ dự án sang Java.
- Không chạy công cụ sinh dự án để ghi đè repository hiện tại.
- Khai báo dependency qua `gradle/libs.versions.toml`.
- Giữ asset dùng chung tại `app/src/main/assets/`, không sao chép sang desktop.
- Android đã có `AndroidLauncher`; desktop đã có `DesktopLauncher`.
- `core` và `lwjgl3` dùng `org.jetbrains.kotlin.jvm`; `app` dùng Kotlin
  tích hợp của AGP 9, không thêm `org.jetbrains.kotlin.android`.
- Phần gameplay không được phụ thuộc trực tiếp vào Activity hoặc Android Context.
- Không sao chép gameplay giữa các mô-đun; `app` giữ vai trò launcher Android,
  `core` chứa mã dùng chung, `lwjgl3` chứa launcher desktop.
- Native libGDX Android cho `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
  được task `extractGdxNatives` giải nén vào `app/build/generated/gdxJniLibs`.
  Giữ kết nối qua `androidComponents.onVariants` và
  `addGeneratedSourceDirectory`; không đưa `Provider` vào
  `android.sourceSets.jniLibs.srcDir` (AGP 9 không hỗ trợ). Không cần NDK.

## Quy trình làm việc

Trước khi sửa:

1. Đọc hướng dẫn áp dụng cho thư mục đang làm việc.
2. Kiểm tra cấu trúc dự án, trạng thái Git và cấu hình build.
3. Xác định tính năng đã có và các thay đổi chưa commit của người dùng.
4. Nêu kế hoạch ngắn gọn rồi thực hiện.

Nếu `git status`/`git log` không đọc được repository, ghi rõ giới hạn này,
không tự tạo Git repository hay suy đoán trạng thái thay đổi chưa commit.

Trong quá trình làm:

- Giữ nguyên các thay đổi không liên quan.
- Không viết lại toàn bộ kiến trúc để sửa một vấn đề nhỏ.
- Tự quyết định chi tiết triển khai thông thường trong phạm vi nhiệm vụ.
- Không dừng sau mỗi bước chỉ để hỏi có tiếp tục không.
- Chỉ hỏi khi thiếu thông tin làm thay đổi đáng kể phạm vi hoặc hành động
  có tính phá hủy, không thể đảo ngược.
- Khi thiếu SDK, dependency hoặc công cụ, ghi rõ trở ngại và tiếp tục
  các phần độc lập có thể làm được.
- Không tuyên bố build hoặc chạy thành công nếu chưa kiểm chứng.

## Phạm vi bản chơi thử

Phạm vi gameplay hiện có là một màn hoàn chỉnh gồm:

- Hai nhân vật: An và Vy.
- Chuyển đổi nhân vật trong màn.
- Hai loại địch: robot tuần tra và drone.
- Boss: Thiết Giáp Gác Cổng.
- Di chuyển, nhảy, đánh, né và kỹ năng.
- Menu, HUD, tạm dừng, thắng, thua và chơi lại.
- Lưu thiết lập và tiến trình cơ bản.
- Hội thoại Linh/An/Vy và hướng dẫn theo hành động thật.
- Sprite atlas, ảnh nền, hoạt ảnh uốn lưới và hiệu ứng neon đã có;
  không thay trở lại toàn bộ bằng placeholder hình học.

Menu đã có luồng Menu → Bản đồ → Chọn đội → Trận → Kết quả và Xưởng nâng cấp.
Bản đồ có dữ liệu 5 khu × 3 màn nhưng **chỉ `streets_01` có gameplay**,
bao gồm trận Thiết Giáp Gác Cổng. Các màn còn lại là nội dung xem trước;
`unlocked` và `implemented` độc lập, chỉ cho chơi khi cả hai đúng.
Khang chỉ là thẻ xem trước, chưa có gameplay và không chọn/nâng cấp được.
Xưởng hiện có nâng cấp máu, sát thương, hồi kỹ năng cấp 0–5 theo từng nhân vật;
giữ hệ thống này khi bảo trì, không tự mở rộng kinh tế hoặc trang bị phức tạp.

Chưa triển khai nếu không được yêu cầu:

- Multiplayer và backend.
- Đăng nhập.
- Quảng cáo và thanh toán.
- Hệ thống trang bị hoặc kinh tế phức tạp.
- Toàn bộ chiến dịch nhiều khu vực.

## Bối cảnh và thiết kế màn đầu

Thành phố Nova bị robot chiếm đóng sau khi hệ thống AI bị can thiệp.
An cùng Vy vượt khu phố mất điện để kích hoạt trạm phát sóng
và tìm Linh, em gái An.

Màn đầu có:

1. Khu hướng dẫn di chuyển và nhảy.
2. Robot tuần tra để học chiến đấu.
3. Bục cao và vực.
4. Khu chiến đấu kết hợp robot và drone.
5. Điểm hồi phục trước boss.
6. Đấu trường boss.
7. Trạm phát sóng kết thúc màn.

Hình học và các mốc màn nằm trong `model/Level.kt`, vị trí địch trong `World.kt`;
thay đổi đường đi phải kiểm tra lại `route()` trong `ModelChecks`.
Trạm hồi phục dùng một lần mỗi lượt, hồi đầy máu và đặt hồi chiêu kỹ năng
về 0 cho người còn sống, **không hồi sinh người đã chết**.
Chỉ thắng khi boss đã bị tiêu diệt, còn nhân vật sống ở trạm và người chơi
nhấn KÍCH HOẠT (`Controls.interact`/phím E); đứng hoặc chạm trạm chưa đủ.
Đường chính phải đi được với bất kỳ nhân vật nào còn sống.

## Nhân vật và chuyển đổi

### An

- Đánh kiếm cận chiến.
- Combo ba đòn, mỗi đòn có startup → active → recovery; chỉ nối combo trên đất.
- Chỉ đệm một yêu cầu đòn tiếp trong active/recovery; trên không là một đòn đơn.
- Kỹ năng lướt chém.
- Mỗi lần vung kiếm chỉ gây sát thương một lần lên mỗi mục tiêu.

### Vy

- Bắn đạn năng lượng theo hướng nhìn.
- Giữ nút để bắn theo hồi chiêu; sau đổi người phải nhả và nhấn lại để bắn.
- Kỹ năng bắn xuyên.
- Mỗi viên đạn xuyên chỉ gây sát thương một lần lên mỗi mục tiêu.

### Chuyển đổi

- Chỉ một nhân vật xuất hiện tại một thời điểm.
- Giữ vị trí, hướng nhìn và vận tốc khi đổi.
- Máu và hồi chiêu kỹ năng được lưu riêng.
- Nhân vật dự bị không nhận sát thương.
- Hồi chiêu của nhân vật dự bị vẫn giảm khi gameplay đang chạy.
- Thời gian chờ đổi nhân vật mặc định: 1 giây, cấu hình được.
- Đổi tự nguyện chỉ khi người dự bị còn sống, không đang hành động/choáng
  và đã hết hồi đổi. Đổi hợp lệ ưu tiên trước lệnh đánh/né/kỹ năng cùng bước.
- Bất tử sau trúng đòn dùng chung cho đội; đổi không thêm hoặc xóa bất tử.
- Hủy hitbox cận chiến đang hoạt động khi đổi.
- Đạn đã bắn tiếp tục vòng đời riêng.
- Khi nhân vật hiện tại chết, tự chuyển sang đồng đội còn sống,
  bỏ qua thời gian chờ đổi.
- Thua khi cả hai hết máu.

## Di chuyển và va chạm

- Có trọng lực, nhảy, rơi, tiếp đất và né.
- Có coyote time và jump buffer với thông số cấu hình được.
- Không cho nhảy vô hạn.
- Không xuyên nền hoặc tường ở tốc độ thiết kế, kể cả khi lướt.
- Dùng tọa độ thế giới độc lập với pixel thiết bị.
- Camera theo người chơi và không vượt biên bản đồ.

Khi rơi khỏi bản đồ:

- Gây sát thương một lần cho mỗi lần rơi.
- Sát thương rơi là 15% máu tối đa, làm tròn lên, bỏ qua bất tử.
- Đưa nhân vật còn sống về vị trí an toàn hợp lệ.
- Xử lý chuyển nhân vật hoặc thua nếu sát thương rơi làm hết máu.
- Không vô tình hồi sinh nhân vật đã chết.

Ưu tiên AABB cho địa hình chữ nhật.
Chỉ thêm Box2D khi gameplay thực sự cần.

## Chiến đấu và kẻ địch

Quy tắc chung:

- Tách hitbox tấn công, hurtbox và hình ảnh.
- Có hồi chiêu, đẩy lùi và bất tử ngắn sau khi trúng đòn.
- Thực thể đã chết không tiếp tục tấn công hoặc nhận sát thương.
- Thu hồi đạn khi hết thời gian sống, va chạm theo quy tắc của loại đạn
  hoặc ra khỏi vùng hoạt động.
- Không xóa đạn chỉ vì nó tạm ra ngoài camera.
- Thêm/xóa thực thể an toàn khi đang duyệt collection.
- Giữ thứ tự xử lý trong `World.update`: input/hành động → AI → di chuyển →
  đạn/thu thập hit → giải quyết sát thương → chết/rơi/linh kiện → trigger →
  kích hoạt đạn mới. Sát thương lên địch xử lý theo `Enemy.id` tăng dần,
  sau đó mới đến sát thương lên người chơi.
- `Enemy.id` phải duy nhất và nằm trong 0–63 vì kiếm/đạn xuyên dùng mặt nạ
  `Long`; khi tăng số địch, kiểm tra cả giới hạn hàng đợi và mảng hiển thị.
- Đạn dùng pool cố định; sát thương chụp tại lúc bắn, không đổi khi đổi người.

Robot tuần tra:

- Tuần tra trong phạm vi.
- Phát hiện và tiếp cận người chơi.
- Báo hiệu trước khi đánh gần.
- Không tự đi xuống vực ngoài chủ đích thiết kế.

Drone:

- Bay trong phạm vi.
- Bắn theo nhịp khi người chơi ở gần.
- Có hồi chiêu; không sinh đạn mỗi frame.

Boss:

- Có thanh máu riêng.
- Hai đòn: đấm đất và lao tới.
- Có trạng thái báo đòn, tấn công và hồi phục.
- Vùng báo hiệu khớp với vùng gây sát thương.
- Ở mức máu ≤ 40%, chờ đòn hiện tại xong rồi chuyển pha một lần,
  giảm hồi phục nhưng giữ nguyên thời gian báo đòn.
- Chọn đấm/lao theo khoảng cách và chỗ trống; không quá hai đòn giống nhau
  liên tiếp khi có thể thực hiện đòn còn lại. Boss không bị đẩy lùi/choáng.
- Khi chết, hủy đòn đang hoạt động và mở lối kết thúc.
- Boss chết xóa đạn địch; nếu người cuối cùng cũng chết trong bước đó thì thua.

Mục tiêu tập `TrainingDummy` không chết do đòn đánh, không tấn công hoặc
rơi linh kiện; gỡ khi hoàn tất bước hướng dẫn đánh/bắn hoặc bỏ qua hướng dẫn.
Mục tiêu đã gỡ không được tiếp tục nhận đòn hay chặn đạn.

Dùng state machine đơn giản, tránh framework AI không cần thiết.

## Điều khiển

Android:

- Bên trái: trái/phải.
- Bên phải: nhảy, đánh, né, kỹ năng.
- Nút riêng cho đổi nhân vật và tạm dừng.
- Nút KÍCH HOẠT riêng chỉ hiện khi tương tác được với trạm;
  nút bỏ qua hướng dẫn chỉ nhận chạm khi đang hiện.
- Hỗ trợ multi-touch, theo dõi từng pointer.
- Cho phép di chuyển đồng thời nhảy hoặc đánh.
- Giải phóng trạng thái nhấn khi touchUp, touchCancelled,
  mất focus, chuyển màn và tạm dừng.
- Chuyển tọa độ chạm qua viewport HUD, không hardcode pixel.

Desktop hiện có:

- A/D hoặc mũi tên: di chuyển.
- Space: nhảy.
- J: An nhấn từng lần để đánh/nối combo; Vy giữ để bắn.
- K: né.
- L: kỹ năng.
- Q: đổi nhân vật.
- Escape: tạm dừng.
- E: kích hoạt trạm phát sóng.
- F3: bật/tắt debug overlay (hitbox, AI, FPS); cũng có trong cài đặt pause.

Tách InputController khỏi gameplay.
Phân biệt hành động nhấn một lần và hành động giữ.
Không lặp một lần nhấn qua nhiều bước fixed update.
`left`, `right`, `attackHeld` là trạng thái giữ; các hành động khác là cạnh nhấn.
`ButtonLayout` là nguồn tọa độ chung cho HUD và hit-test, có `TOUCH_SLOP`;
giữ các vùng chạm không chồng nhau. Nút gameplay nằm dưới `DECK_TOP`,
ngoại trừ pause và bỏ qua hướng dẫn; camera đặt mặt đường theo cùng mốc này.

## Kiến trúc và vòng đời

Tách trách nhiệm:

- Game/Screen.
- Gameplay và trạng thái thực thể.
- Va chạm.
- Input.
- Hiển thị và hoạt ảnh.
- HUD.
- Lưu dữ liệu.
- Cấu hình thông số.

Không bắt buộc tạo lớp chưa có trách nhiệm thực tế.
Không xây ECS hoặc framework tổng quát cho bản thử nghiệm.

Ranh giới mã hiện có (đường dẫn dưới `core/src/main/java/com/manroid/neonrebels/`):

| Thành phần | Trách nhiệm và quy tắc mở rộng |
| --- | --- |
| `model/` | Mô phỏng Kotlin/JVM thuần: `World`, `Player`, địch, `Level`, va chạm, `Controls`, `FixedStepper`, `CombatSession`, hội thoại/hướng dẫn. Không import libGDX hoặc Android. |
| `progress/` | Kotlin/JVM thuần: dữ liệu chiến dịch, `GameState`, công thức chỉ số, service đội/nâng cấp/thưởng, `SaveCodec`, `ProgressRepository`, giao diện `SaveStore`. Không import libGDX hoặc Android. |
| `input/` | `InputController` chuyển phím/chạm thành `Controls`; `ButtonLayout` và `PointerButtons` giữ độc lập libGDX để kiểm thử. |
| `view/` | HUD, `WorldRenderer`, `WorldSprites`, `RigRenderer`, `Effects`, viewport, font và bộ vẽ UI. Chỉ đọc trạng thái gameplay, không quyết định sát thương. |
| Các `*Screen`, `MenuBase` | Điều phối model/service, input và vẽ; `UiButton` nằm trong `MenuBase.kt`. Không sao chép công thức hoặc quy tắc tiến trình vào màn hình. |
| `MainGame` | Sở hữu font thường/nghiêng, batch, shape renderer, service tiến trình và điều hướng. Screen mượn tài nguyên chung, chỉ dispose tài nguyên riêng. |
| `SaveManager`, `PreferencesStore` | Cầu nối libGDX Preferences cho cài đặt và `SaveStore`; không đưa Preferences vào model/service thuần. |

`MainGame.open(next)` thay và dispose màn cũ. `openWorkshop(from)` giữ màn gọi
trong `parked`; `returnFromWorkshop` trả về đúng instance đó và dispose Xưởng.
Giữ đội nháp, màn đang chọn và kết quả khi đi Xưởng rồi quay lại; không nhận
thưởng lần nữa. Chuyển màn dùng chốt `navigate`/`leaving` để chống nhấn lặp.

Yêu cầu:

- Dùng fixed timestep cho gameplay.
- `GameConfig.STEP` hiện là 1/120 giây, tối đa 12 bước bù trong `FixedStepper`.
- Giới hạn frame delta và số bước cập nhật bù.
- Không mô phỏng bù toàn bộ thời gian ứng dụng nằm nền.
- Pause phải dừng vật lý, AI, đạn, sát thương và bộ đếm gameplay.
- UI pause vẫn nhận tương tác.
- Khi trở lại từ nền, chờ người chơi chủ động tiếp tục.
- Xử lý resize, pause, resume và dispose.
- Không tải asset hoặc tạo texture mỗi frame.
- Hạn chế cấp phát đối tượng trong vòng lặp.
- Tài nguyên có chủ sở hữu rõ ràng và chỉ dispose một lần.

Luồng trận do `CombatSession` quản lý:
`LOADING → INTRO → PLAYING ⇄ DIALOGUE`, `PAUSED`, `VICTORY`/`DEFEAT`, `EXITING`.
Chỉ `PLAYING` chạy mô phỏng; intro/hội thoại/pause/kết quả đóng băng cả hồi chiêu
và đồng hồ trận. `GameScreen` có một router input, không để cùng lần chạm đi
vào cả hội thoại/overlay và nút chiến đấu. Khi dừng, xóa input và accumulator;
khi tiếp tục, trở lại đúng trạng thái đã bị ngắt, kể cả hội thoại.

Hội thoại xếp hàng tới lúc an toàn (trên đất, không hành động/choáng) và chỉ
phát ở lượt của màn chưa hoàn thành. Nhấn tiếp khi chữ chưa hiện đủ chỉ hiện
hết câu; nhấn lần sau mới sang câu. Bỏ qua vẫn chạy hành động bắt buộc của đoạn.
Hướng dẫn hoàn thành theo bộ đếm hành động thật trong `World`, không theo số
lần chạm nút; lưu `done`/`skipped` từng bước, chiến dịch mới đặt lại.

## Hình ảnh và văn bản

- Phong cách tương lai, nền tối, điểm nhấn neon.
- Giữ pipeline art đã có: nền/chân dung HUD tại `assets/ui/`, atlas và nền
  thế giới tại `assets/sprites/`, font bitmap tại `assets/fonts/`.
- Phân biệt rõ nhân vật, địch, boss, đạn và địa hình.
- Có phản hồi trực quan khi đánh, trúng đòn, dùng kỹ năng và chết.
- `WorldSprites` sở hữu atlas/nền; `WorldRenderer` dispose nó.
  `GameHud` sở hữu chân dung; `GameScreen` sở hữu ảnh Linh; `MenuBase`
  quản lý các texture do từng màn menu tải. Không dispose tài nguyên mượn.
- `SpriteNames.kt` là hợp đồng tên vùng/số khung; `actors.properties`
  lưu tâm thân, chiều cao thế giới và hướng ảnh. Đổi art không đổi hitbox.
- `RigRenderer` uốn lưới ảnh theo `Rigs`/`Pose`; `Effects` dùng pool cố định.
  Hoạt ảnh và hiệu ứng chạy theo thời gian mô phỏng để dừng khi pause/hội thoại.
- Không tham chiếu asset chưa tồn tại.
- Không có âm thanh vẫn phải chơi được.
- Giữ tỷ lệ hình ảnh và hỗ trợ bố cục 16:9, 20:9.
- Nút điều khiển không bị che bởi vùng khuyết hoặc thanh hệ thống.

Văn bản:

- Giao diện Android dùng `res/values/strings.xml`.
- Giao diện libGDX dùng bundle bản địa hóa trong assets.
- Không phụ thuộc Android Resources từ phần gameplay dùng chung.
- Nếu hiển thị tiếng Việt, font phải hỗ trợ đầy đủ dấu.
- Nếu dùng tiếng Anh tạm thời, ghi rõ trong README.

Giao diện libGDX mặc định tiếng Việt tại `i18n/strings.properties`, bản Anh tại
`i18n/strings_en.properties`, chọn theo locale thiết bị. Thêm khóa ở **cả hai**
bundle UTF-8 và gọi `UiPainter.tr`/`MainGame.text`, không viết cứng chữ UI.
Hai font `neon-ui.fnt` và `neon-italic.fnt` sinh từ Noto Sans Bold/Bold Italic,
kèm giấy phép OFL. `FontSpec.PIXEL_SIZE` phải khớp font; kích thước bố cục được
quy đổi qua `FontSpec.UNIT`. Giữ xử lý metric trong `MainGame.loadFont` khi
sửa cách tải font, kiểm tra chữ tiếng Việt trên thiết bị.

Chỉ chạy lại công cụ khi thực sự cần dựng asset (file kết quả đã có):

- `python3 scripts/extract-ui-art.py`: cắt nền menu và HUD từ ảnh `ui/01`,
  `ui/02`; cần Pillow.
- `python3 scripts/build-world-art.py`: dựng atlas/nền từ `ui/02`, `ui/04`,
  `ui/05`; cần Pillow, NumPy, OpenCV (`opencv-python-headless`).
- `python3 scripts/generate-font.py [path/to/NotoSans-Bold.ttf] [path/to/NotoSans-BoldItalic.ttf]`:
  sinh cả font thường/nghiêng; cần Pillow và font nguồn. Bổ sung ký tự mới
  vào charset của script, giữ giấy phép và chạy kiểm tra bản địa hóa.

Ảnh `ui/` là nguồn thiết kế, asset đóng gói nằm trong `app/src/main/assets/`.
Không yêu cầu Python/OpenCV trên Android hoặc khi build thông thường.

## Lưu dữ liệu

Dùng libGDX Preferences `nova-light-core-v1`; giữ tên cũ để bảo toàn dữ liệu.
`SaveManager` giữ `showControls` và `debugOverlay` ở các khóa riêng;
`PreferencesStore` thực hiện `SaveStore` cho tiến trình chiến dịch.

- Có giá trị mặc định khi chưa có dữ liệu.
- Lưu khi tạo/reset chiến dịch, chọn màn, xác nhận đội vào trận, mua nâng cấp,
  nhận kết quả, hoàn thành/bỏ qua bước hướng dẫn hoặc thay đổi thiết lập.
- Không flush mỗi frame.
- Không lưu đạn, hitbox hoặc trạng thái nút đang nhấn.
- Chơi lại reset toàn bộ trạng thái màn.
- Không xóa thiết lập người dùng khi chơi lại.

Các bất biến tiến trình:

- Chỉ sửa tiến trình qua `ProgressRepository.update`: tạo bản sao, ghi trước,
  thay trạng thái trong bộ nhớ khi ghi thành công. Không sửa trực tiếp `state`
  từ giao diện; lỗi lưu phải giữ trạng thái cũ và cho thử lại.
- `SaveCodec` lưu snapshot có phiên bản và CRC32 tại `campaign`, bản hợp lệ
  trước tại `campaignBackup`, dữ liệu lỗi tại `campaignCorrupt`. Giữ migration
  khóa cũ; không âm thầm xóa save lỗi hoặc phiên bản chưa hiểu.
- Preferences không bảo đảm nguyên tử khi mất điện; checksum/bản dự phòng
  giúp phát hiện/phục hồi, không được mô tả là giao dịch bền vững tuyệt đối.
- Đội nháp `DraftTeam` chỉ lưu khi `TeamService.start` xác nhận đúng hai
  nhân vật khác nhau đã mở và có gameplay, đồng thời cấp `runId` mới.
  `RunSetup`/`Loadout` chụp chỉ số vào trận; nâng cấp áp dụng từ lượt mới.
- Công thức chỉ số dùng chung ở `StatCalculator`; dữ liệu màn/nhân vật,
  thưởng và bảng giá ở `GameData`. Không sao chép công thức vào HUD/menu.
- Linh kiện hạ địch chỉ vào `World.collectedParts`; thắng mới cộng quỹ tạm
  cùng thưởng màn và thưởng lần đầu. Thua/thoát/chơi lại từ pause không nhận.
- Chỉ `CampaignService.applyResult` chốt thưởng, hoàn thành và kỷ lục.
  Giữ chống lặp theo `runId`; lưu lỗi thử lại cùng ID, không tạo thưởng mới.
- Chơi lại tạo `World` và `runId` mới, giữ chiến dịch/nâng cấp/cài đặt.
  Chiến dịch mới reset tiến trình/linh kiện/nâng cấp/hướng dẫn sau xác nhận,
  giữ cài đặt và bộ đếm run ID để chặn kết quả cũ.
- TIẾP TỤC dẫn về Bản đồ, không khôi phục giữa trận; không lưu máu hiện tại,
  thực thể, đạn, hitbox, Screen, texture hoặc trạng thái nút.

## Lệnh phát triển và kiểm thử

Chạy các lệnh sau từ thư mục gốc (Gradle dùng Wrapper của dự án):

- `./scripts/test-model.sh`
  Chạy hồi quy gameplay **và tiến trình** bằng Bash + JDK ≥ 11 + `kotlinc`,
  hoặc Kotlin compiler có sẵn trong cache Gradle; không cần libGDX/Android SDK.
- `./gradlew :core:test`
  Chạy JUnit gameplay, tiến trình, bản địa hóa và sprite assets.
- `./gradlew :core:modelTest`
  Chạy trực tiếp `ModelChecks.main`, gồm cả `ProgressChecks`.
- `./gradlew :lwjgl3:classes :lwjgl3:jar`
  Biên dịch/đóng JAR desktop; JAR này không phải bản độc lập kèm mọi dependency.
- `./gradlew :lwjgl3:run`
  Chạy desktop 1280×720; `--args="--wide"` cho 1600×720 (20:9).
- `./gradlew :app:assembleDebug`
  Tạo APK trong `app/build/outputs/apk/debug/`.
- `./gradlew :app:testDebugUnitTest`
  Chạy kiểm thử JVM của launcher Android (hiện là test mẫu).
- `./gradlew :app:connectedDebugAndroidTest`
  Chạy kiểm thử trên thiết bị hoặc emulator đã kết nối.
- `./gradlew :app:lintDebug`
  Chạy Android Lint.
- `./gradlew :app:installDebug`
  Cài APK lên thiết bị.

Cấu hình trong file build hiện tại:

| Thành phần | Giá trị |
| --- | --- |
| Gradle Wrapper | 9.6.0, kèm SHA-256 trong `gradle-wrapper.properties` |
| Android Gradle Plugin | 9.5.1 |
| Kotlin JVM plugin (`core`, `lwjgl3`) | 2.2.10 |
| libGDX | 1.14.2 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 24 |
| Gradle daemon JDK | 25 (`gradle/gradle-daemon-jvm.properties`) |
| Java source/target và Kotlin JVM target | 11 |

Phải đối chiếu với file cấu hình thực tế và Gradle Wrapper.
Không mặc định cấu hình ghi trong tài liệu luôn còn chính xác.
Không thay đổi SDK, JDK, Gradle hoặc plugin chỉ theo sở thích.

Nếu phát hiện không tương thích:

1. Xác định lỗi và yêu cầu phiên bản.
2. Chọn thay đổi nhỏ nhất để khắc phục.
3. Giải thích và cập nhật tài liệu liên quan.

Ghim phiên bản dependency, không dùng phiên bản động như `+`.
`--offline` chỉ dùng khi dependency/công cụ đã có đủ trong cache.
Desktop cần OpenGL và phiên đồ họa; Android instrumentation cần thiết bị/emulator.
Lịch sử build/giới hạn kiểm chứng nằm trong README; không xem đó là kết quả
đã chạy ở phiên làm việc hiện tại.

## Quy ước viết mã

- Thụt lề bốn dấu cách.
- `PascalCase` cho lớp.
- `camelCase` cho hàm và thuộc tính.
- Chữ thường cho package.
- `snake_case` cho tài nguyên Android.
- Ưu tiên Kotlin theo phong cách hiện có.
- Không thêm Java nếu không có lý do tích hợp cụ thể.
- Đưa thông số gameplay vào cấu hình có tên rõ ràng.
- Không rải magic number cho máu, tốc độ, sát thương và hồi chiêu.
- Chỉ thêm bình luận khi cần giải thích lý do hoặc logic khó hiểu.

Chưa có cấu hình ktlint/Detekt.
Không thêm công cụ định dạng mới ngoài phạm vi nhiệm vụ.

## Hướng dẫn kiểm thử

Dùng:

- JUnit 4 cho JVM.
- AndroidX JUnit và Espresso cho giao diện Android phù hợp.
- Kiểm tra trực tiếp trên thiết bị/emulator cho gameplay libGDX;
  không giả định Espresso truy cập được từng đối tượng trong game.

Tên lớp kết thúc bằng `Test`.
Tên hàm mô tả hành vi.

Bổ sung kiểm thử hồi quy cho thay đổi logic quan trọng:

- Tính sát thương.
- Hồi chiêu.
- Chuyển nhân vật.
- Chết, thắng, thua.
- Reset màn.
- Máy trạng thái trận, hội thoại/hướng dẫn, quyền ưu tiên input.
- Mở khóa/chọn đội, công thức và mua nâng cấp, thưởng chống lặp theo run ID.
- Lưu lỗi, migration, checksum và phục hồi bản dự phòng.

`GameplayTest` gọi `ModelChecks.runAll()`, `ProgressTest` gọi
`ProgressChecks.runAll()`; thêm assertion vào nhóm phù hợp trong hai bộ này.
`ModelChecks.main` gọi cả hai bộ. `LocalizationTest` kiểm tra khóa hai ngôn ngữ,
khóa dùng trong mã và glyph của font thường; khi đổi font nghiêng cần kiểm tra
thêm nó. `SpriteAssetsTest` kiểm tra vùng/số khung atlas, metadata và kích thước
power-of-two của texture lặp (GLES2).

`scripts/test-model.sh` chỉ biên dịch `model/`, `progress/`,
`input/ButtonLayout.kt`, `input/PointerButtons.kt` và hai bộ checks.
Không làm các lớp này phụ thuộc libGDX; script không thay thế `:core:test`
cho kiểm tra bản địa hóa hoặc asset.

Không viết test chỉ để kiểm tra hằng số hoặc sao chép logic triển khai.
Chưa quy định ngưỡng độ bao phủ.

Checklist gameplay:

1. Di chuyển và nhảy/đánh đồng thời.
2. Thả tay ngoài nút không gây kẹt input.
3. Không nhảy vô hạn hoặc xuyên nền.
4. Lướt không xuyên tường.
5. Một đòn không gây sát thương lặp mỗi frame.
6. Nhân vật chết chuyển đúng sang đồng đội.
7. Cả hai chết dẫn đến thua.
8. Pause và chuyển ứng dụng không gây sát thương ngầm.
9. Boss chết mở lối kết thúc.
10. Chơi lại không giữ thực thể hoặc trạng thái cũ.
11. HUD không bị cắt ở 16:9 và 20:9.
12. Boss chết chưa thắng; cần nút KÍCH HOẠT tại trạm; người cuối chết thì thua.
13. Hồi phục không hồi sinh; hướng dẫn không yêu cầu đổi sang người đã chết.
14. Hội thoại/intro dừng mô phỏng; bỏ qua vẫn thực hiện hành động bắt buộc.
15. Đi Xưởng rồi quay lại giữ đội nháp/màn/kết quả; nhấn nhanh không mở hai lượt.
16. Lưu lỗi không trừ linh kiện/tăng cấp; nhận lại kết quả không cộng thưởng lặp.
17. Chiến dịch mới có xác nhận, HỦY giữ nguyên dữ liệu, cài đặt không bị reset.

Chạy kiểm tra phù hợp với thay đổi.
Không coi build thành công là bằng chứng gameplay đã được kiểm tra.

## Commit và pull request

Kiểm tra lịch sử Git nếu có thể để tuân theo quy ước hiện có.

Nếu chưa có quy ước:

- Tiêu đề commit ngắn, nêu rõ hành động.
- Mỗi commit tập trung vào một thay đổi.
- Không commit file không liên quan.

Pull request cần:

- Mô tả vấn đề và hành vi sau thay đổi.
- Liên kết issue nếu có.
- Nêu kết quả kiểm tra thực tế.
- Thêm ảnh hoặc video khi sửa giao diện/gameplay nếu có thể.
- Nêu rõ phần chưa kiểm chứng.

Không tự commit, push hoặc phát hành nếu nhiệm vụ chưa yêu cầu.

## Bảo mật cấu hình

- Lưu đường dẫn SDK riêng trong `local.properties`, được Git bỏ qua.
- Không commit token, API key, mật khẩu hoặc khóa ký.
- Không commit sản phẩm build.
- Không đưa thông tin xác thực vào log.
- Không thêm quyền Android không cần thiết.
- Không tích hợp quảng cáo, analytics hoặc dịch vụ bên ngoài
  nếu chưa được yêu cầu.

## Bàn giao và cập nhật tài liệu

Sau mỗi nhiệm vụ, báo cáo ngắn gọn:

1. Đã thay đổi gì.
2. Các file hoặc thành phần chính.
3. Các kiểm tra đã chạy và kết quả.
4. Hạn chế hoặc phần chưa kiểm chứng.

Cập nhật README khi thay đổi:

- Yêu cầu JDK/SDK.
- Cách build và chạy.
- Cấu trúc mô-đun hoặc asset.
- Điều khiển.
- Tính năng và giới hạn của bản thử nghiệm.

Giữ AGENTS.md là hướng dẫn làm việc.
Ghi tiến độ triển khai và danh sách việc còn lại trong README
hoặc tài liệu tiến độ riêng, không biến AGENTS.md thành nhật ký.
