# NEON REBELS — UI 02: Logic chiến đấu

Tài liệu mô tả hành vi **đã triển khai**. Mô phỏng nằm trong `core/.../model/` (Kotlin thuần, không
phụ thuộc libGDX, có kiểm thử); màn hình chỉ gửi lệnh và đọc trạng thái.

| Trách nhiệm | Lớp |
| --- | --- |
| Trạng thái trận (GameSession) | `model/CombatSession.kt` |
| Một bước mô phỏng, hàng đợi sát thương, trigger | `model/World.kt` |
| Nhân vật, hành động, đổi người (PlayerController + CharacterStateMachine) | `model/Player.kt` |
| Robot / drone / boss (EnemyAI, BossController) | `model/PatrolRobot.kt`, `Drone.kt`, `Boss.kt`, `Enemy.kt` (kèm `TrainingDummy`) |
| Hội thoại, hướng dẫn | `model/Dialogue.kt`, `model/Tutorial.kt` |
| Fixed timestep | `model/FixedStepper.kt` |
| Va chạm địa hình | `model/CollisionSystem.kt`, `Level.blocked()` |
| Thông số | `model/GameConfig.kt` |
| Input | `input/InputController.kt`, `PointerButtons.kt`, `ButtonLayout.kt` |
| Màn hình, overlay, router input | `GameScreen.kt` |
| HUD, thế giới, debug overlay | `view/GameHud.kt`, `view/WorldRenderer.kt` |

## 1. Trạng thái trận

`LOADING → INTRO → PLAYING ⇄ DIALOGUE`, `PAUSED` (nhớ `pausedFrom`), `VICTORY | DEFEAT` (kết thúc,
loại trừ nhau, chỉ đặt một lần), `EXITING`.

- Chỉ `PLAYING` chạy mô phỏng (`World.paused` phản chiếu trạng thái). INTRO, DIALOGUE, PAUSED đóng
  băng địch, đạn, vật lý, sát thương, hồi chiêu và đồng hồ trận. Chữ chạy, intro, thông báo dùng
  thời gian UI riêng.
- Ngoài PLAYING, input chiến đấu bị xóa và nút chiến đấu ẩn; một router duy nhất trong `GameScreen`
  gửi chạm tới đúng lớp (điều khiển / hội thoại / overlay), nên một lần chạm không đi xuyên.
- Ra nền: tự pause, xóa input và accumulator. Quay lại không tự chạy; TIẾP TỤC trả về đúng trạng thái
  trước pause (hội thoại vẫn mở). Không có checkpoint giữa màn: tiến trình bị hủy thì bắt đầu lại.

## 2. Vòng lặp

Fixed step **1/120 s** (giữ nguyên của dự án để lướt và đạn nhanh không xuyên vật; đề xuất 1/60 không
đổi hành vi, chỉ thô hơn), kẹp delta, tối đa 12 bước bù. Nếu một bước làm dừng chơi (hội thoại,
kết quả), các bước còn lại của frame bị bỏ. Thứ tự trong `World.update`:

1. Tiêu thụ input → 2. bộ đếm, hành động, ý định (đổi người trước; nếu đổi thì bỏ lệnh đánh/né/kỹ
năng cùng bước; không đổi thì Né → Kỹ năng → Đánh) → 3. AI địch → 4. di chuyển + địa hình →
5. đạn (quét từng đoạn ≤ 0.15) → 6. thu thập hit → 7. giải quyết sát thương (người→địch theo
`Enemy.id` tăng dần, rồi địch→người theo thứ tự hàng đợi) → 8. rơi, chết, đổi bắt buộc, rơi linh
kiện → 9. trigger, boss, điều kiện kết thúc → 10. đạn sinh trong bước mới được kích hoạt.

Boss chết và người cuối cùng gục trong cùng bước ⇒ **DEFEAT** (chưa kích hoạt trạm, không thưởng).

## 3. Input

| Nút (Android) | Desktop | Kiểu |
| --- | --- | --- |
| Trái / Phải (dưới trái) | A/D, mũi tên | Giữ; giữ cả hai ⇒ đứng yên |
| Nhảy | Space | Cạnh nhấn |
| Đánh (An) / Bắn (Vy) | J | An: mỗi lần nhấn một yêu cầu combo; Vy: giữ để bắn |
| Né, Kỹ năng (hàng trên) | K, L | Cạnh nhấn |
| Đổi (trái cụm chiến đấu) | Q | Cạnh nhấn |
| Kích hoạt (chỉ hiện ở trạm) | E | Cạnh nhấn, nút riêng, không đè Nhảy/Đánh |
| Tạm dừng (góc phải trên) | Esc / Back | — |
| Bỏ qua hướng dẫn (cạnh khung hướng dẫn) | — | — |
| Bật/tắt debug overlay | F3 hoặc Cài đặt trong pause | — |

Mỗi pointer sở hữu tối đa một nút; kéo ra ngoài thì nhả; nhả một pointer không ảnh hưởng pointer
khác. Vùng chạm rộng hơn hình 10 đơn vị và không chồng nhau (có kiểm thử). Vị trí nút giống nhau
cho An/Vy. Sau khi đổi người, phải nhả và nhấn lại nút bắn mới bắn.

## 4. Thông số (đơn vị thế giới, giây) — `GameConfig`

Di chuyển: chạy 6.5, nhảy 12, trọng lực −28, rơi tối đa 18, coyote 0.10, jump buffer 0.12. Các
số đề xuất (5 / 10 / −25) không dùng vì bố cục bục của màn 1-1 được canh theo cung nhảy hiện tại
(kiểm thử `route()`).

| An | Startup | Active | Recovery | Sát thương |
| --- | --- | --- | --- | --- |
| Đòn 1 | 0.08 | 0.10 | 0.18 | 1.0 × atk |
| Đòn 2 | 0.08 | 0.10 | 0.20 | 1.1 × atk |
| Đòn 3 | 0.12 | 0.12 | 0.28 | 1.5 × atk, đẩy lùi mạnh |
| Lướt chém | — | 0.22 | — | 2.0 × atk, 12 u/s, không bất tử |

Vy: bắn 0.25 s/phát, 1.0 × atk, 14 u/s, sống 1.5 s. Đạn xuyên 2.5 × atk, 18 u/s, 1.5 s, khóa bắn
thường 0.2 s. Né: 10 u/s, 0.20 s, hồi 1.0 s, bất tử 0.12 s đầu. Trúng đòn: bất tử 0.6 s, choáng 0.18 s.
Rơi: 15% máu tối đa (làm tròn lên). Đổi người: hồi 1.0 s.

Robot: máu 180, sát thương 80, 2.0 u/s, thấy 6.0, tầm 1.2, windup 0.40 / active 0.12 / recovery 0.80.
Drone: máu 120, đạn 60 ở 5 u/s, AIM 0.50, nghỉ 1.20. Boss: máu 3000; đấm đất báo 0.70 / active 0.15 /
180 / hồi 1.20; lao báo 0.80 / tối đa 0.65 s / 9 u/s / 150 / hồi 1.40; pha 2 ở ≤ 40%: hồi × 0.75.

## 5. Quy tắc chiến đấu

- **Combo An:** chỉ nối trên đất; trên không là một đòn đơn. Cửa sổ đệm = active + recovery, giữ tối
  đa một lệnh. Hướng khóa lúc bắt đầu đòn; mỗi đòn trúng mỗi mục tiêu một lần (mặt nạ bit). Bị trúng,
  chết, đổi người, né đều xóa combo. Né hủy được recovery, không hủy startup/active.
- **Lướt chém:** chỉ trên đất, khóa hướng, dừng ở tường; bị tường chặn ngay từ đầu ⇒ từ chối, không
  tốn hồi chiêu; bị trúng đòn ⇒ dừng, hồi chiêu vẫn tính.
- **Vy:** bắn khi chạy và trên không; không bắn khi né/choáng/chết/đổi trạng thái màn. Sát thương đạn
  là ảnh chụp lúc bắn, không đổi khi đổi người.
- **Né:** chỉ trên đất, theo hướng đang giữ (hoặc hướng nhìn), dừng ở tường, không cứu khỏi rơi; thiếu
  khoảng trống ⇒ từ chối, không tốn hồi chiêu.
- **Đổi:** chỉ khi cả hai còn sống, không đang hành động/choáng, hết hồi chiêu. Giữ vị trí, vận tốc,
  hướng; bất tử là bộ đếm chung của đội (đổi không thêm, không xóa). Hồi kỹ năng của người dự bị vẫn
  chạy khi PLAYING. Người đang đánh gục ⇒ tự đổi, bỏ qua hồi chiêu.
- **Rơi:** một lần sát thương, bỏ qua bất tử; đưa người còn sống về điểm an toàn, vận tốc 0, bảo vệ 0.6 s.
  Điểm an toàn chỉ ghi trên nền rộng trước đấu trường, không sát kẻ địch.
- **Trạm hồi phục** (x 107–112, một lần mỗi lượt): hồi đầy người còn sống, không hồi sinh, hồi chiêu
  kỹ năng về 0, cập nhật điểm an toàn.

## 6. Kẻ địch

- **Robot:** PATROL → CHASE (trong tầm, cùng độ cao, không tường chắn) → WINDUP (dừng, khóa hướng) →
  ATTACK (hitbox phía trước, chỉ trúng nếu người chơi đang ở trong lúc active) → RECOVERY. Trúng đòn
  ⇒ HURT, hủy đòn đang chờ. Không bước xuống vực, không ra khỏi vùng tuần tra.
- **Drone:** PATROL → AIM (khóa điểm ngắm lúc bắt đầu, hiện tia đỏ) → FIRE → REPOSITION. Độ cao trong
  tầm bắn ngang khi nhảy (có kiểm thử). Không bị đẩy lùi. Đạn bị tường chặn.
- **Mục tiêu tập** (x 8, biển "MỤC TIÊU TẬP"): robot màu cyan đứng yên, nhận đòn, không chết, không tấn công, không rơi linh kiện. Chỉ có mặt khi hướng dẫn còn cần bước Đánh/Bắn; xong bước đó hoặc bỏ qua hướng dẫn thì bị gỡ khỏi màn (không vẽ, không nhận đòn, không chặn đạn), kể cả ở các lượt chơi lại.

## 7. Boss Thiết Giáp Gác Cổng

`SLEEP → INTRO (1.2 s, không tấn công) → IDLE → TELEGRAPH → ATTACK → RECOVERY → IDLE`, cộng
`PHASE_TRANSITION` và `DEAD`.

- Kích hoạt khi người chơi qua x 115 (đã ra khỏi collider cửa x 113): khóa cửa, camera khóa vào đấu
  trường, hiện thanh máu boss, hội thoại cảnh báo (lần đầu chơi màn).
- **Đấm đất:** vùng khóa lúc bắt đầu báo đòn = đúng vùng gây sát thương (ra 2 đơn vị mỗi bên, cao 1.1,
  nhảy qua được). **Lao:** quay theo người chơi khi báo, khóa hướng lúc hết báo, chạy thẳng tới hết
  giờ hoặc chạm tường, một hit mỗi lần lao; vùng cảnh báo = toàn bộ đường lao có thể đi.
- Chọn đòn: gần (< 4) ⇒ đấm; xa và đủ chỗ (≥ 2) ⇒ lao; không quá hai lần liên tiếp cùng đòn khi
  đòn kia làm được; không lao khi không có chỗ.
- Pha 2: máu ≤ 40% ⇒ đánh dấu, chờ đòn hiện tại xong mới chuyển (không cắt báo đòn), một lần; hồi × 0.75,
  báo đòn giữ nguyên; trong lúc chuyển không có hitbox, vẫn nhận sát thương.
- Giáp: không bị đẩy lùi/choáng. Chết: hủy báo đòn và đòn đang chờ, xóa đạn địch, mở cổng, ẩn thanh
  máu, mục tiêu đổi thành "Kích hoạt trạm phát sóng". **Chưa thắng.**

## 8. Trạm phát sóng và kết quả

Trạm (x 153–158) chỉ hoạt động sau khi boss chết. Đứng gần ⇒ hiện nút **KÍCH HOẠT** riêng; nhấn khi
còn người sống ⇒ VICTORY đúng một lần. Kết thúc: dừng mô phỏng, xóa input, tạo
`RunResult(runId, levelId, won, time, collectedParts)` và giao cho `CampaignService.applyResult`
(xem `docs/UI_GAME_LOGIC.md`): thắng nhận linh kiện nhặt + thưởng màn (+ lần đầu); thua/thoát/chơi
lại bỏ linh kiện tạm; kết quả lặp không cộng thêm; lưu lỗi ⇒ nút LƯU LẠI thử lại cùng `runId`.

## 9. Hội thoại và hướng dẫn

- Hội thoại có `dialogueId` (`streets_01.intro`, `.boss`, `.boss_down`), văn bản trong bundle
  (`dlg.*`), người nói Linh/An/Vy. Chỉ chạy khi màn chưa từng hoàn thành; mỗi đoạn một lần mỗi lượt.
  Mở vào thời điểm an toàn (trên đất, không hành động, không choáng); nếu chưa an toàn thì xếp hàng.
- Chữ hiện dần 45 ký tự/s. TIẾP (hoặc chạm vùng trống) lần đầu hiện đủ câu, lần sau mới sang câu
  tiếp. BỎ QUA kết thúc đoạn nhưng vẫn chạy hành động bắt buộc (đoạn mở đầu luôn bật hướng dẫn).
- Hướng dẫn tách khỏi hội thoại: Di chuyển (đi 3 đơn vị) → Nhảy → Đánh/Bắn (trúng mục tiêu tập hoặc
  địch) → Né → Kỹ năng → Đổi, mỗi bước hoàn tất bằng hành động thật trong `World`. Gameplay vẫn chạy;
  nút tương ứng được viền sáng. Bước Kỹ năng chờ hồi chiêu xong; bước Đổi tự bỏ qua nếu chỉ còn một
  người. Có nút bỏ qua. Kết quả lưu trong tiến trình (`tutorial.<bước>=done|skipped`), chơi lại không
  học lại, Chơi mới đặt lại.

## 10. Pause

Tiếp tục / Chơi lại (xác nhận) / Cài đặt (nút cảm ứng, debug overlay) / Về menu (xác nhận). Chơi lại
và Về menu chốt lượt hiện tại là thua (không thưởng); Chơi lại tạo `runId` mới, đội giữ nguyên, máu
đầy, hồi chiêu 0, `World` mới (không còn đạn, địch, trigger cũ). Về menu không xóa tiến trình chiến dịch.

## 10b. Hoạt ảnh và hiệu ứng (chỉ hiển thị)

`view/WorldRenderer.kt` + `view/Effects.kt`. Không ảnh hưởng hitbox hay sát thương; hiệu ứng được kích
hoạt bằng cách so trạng thái model với frame trước và chạy theo thời gian trận (đứng yên khi pause/hội thoại).

- **Uốn lưới theo khung xương** (`view/RigRenderer.kt`): mỗi ảnh nhân vật/quái được vẽ thành lưới 12×10 ô và
  uốn theo một khung xương đo trên ảnh (`Rigs`): hai chân đánh kéo ngược chiều nhau quanh hông theo
  quãng đường đã đi (không trượt chân), chân đưa lên trước thì nhấc lên; khi nhảy co chân; tay + vũ khí
  xoay quanh vai (vùng xoay là dải dọc theo tay/vũ khí nên không kéo theo chân); khăn/tóc gợn sóng.
  Tư thế (`Pose`) được tính từ trạng thái gameplay: An nâng kiếm khi startup, chém vòng khi active (mỗi
  đòn một hướng), thu kiếm khi recovery; Vy giật súng; robot giơ súng/lưỡi khi báo đòn và chém xuống;
  boss giơ nắm đấm khi báo đấm đất và nện xuống, dậm chân khi lao. Tia sét, chớp đầu nòng và mắt quái
  bám theo vị trí đã uốn.
- **Nhân vật:** thở khi đứng; nhún, nghiêng và bụi chân khi chạy; giãn khi bật nhảy, nén khi tiếp đất
  (kèm bụi); An lùi tay khi startup, chém tới khi active (đòn 3 bật người), hồi tư thế khi recovery;
  bóng mờ khi né/lướt; giật lùi súng của Vy; nghiêng và nháy đỏ khi trúng đòn; nháy trắng khi đổi người.
- **Kiếm An:** tia sét chạy dọc lưỡi kiếm (vị trí đo trên hình, theo đúng biến đổi của sprite), lúc đứng
  lách tách nhẹ, mạnh dần khi ra đòn, có nhánh sét ở mũi kiếm; vệt chém hình lưỡi liềm khác nhau cho
  từng đòn; vệt tốc độ khi lướt chém.
- **Vy:** chớp lửa đầu nòng, đạn có vệt sáng, đạn xuyên có vòng xoắn.
- **Trúng đòn:** tia lửa theo màu nhân vật, số sát thương bay lên (vàng và lớn khi ≥ 150), rung màn hình
  nhẹ khi đòn mạnh; nhân vật trúng đòn ra tia lửa đỏ và số đỏ.
- **Robot:** đi nhún nghiêng; báo đòn ngả người, rung, mắt đỏ sáng dần; chém tới khi đánh; đổ, nổ, khói khi chết.
- **Drone:** nghiêng theo hướng bay, quầng đỏ khi ngắm, xoay rơi và nổ khi chết.
- **Boss:** đứng dậy khi intro; gồng người khi báo đòn (mắt rực, quầng đỏ); đấm đất có sóng xung kích
  trên mặt đường, bụi hai bên và rung mạnh; lao có bóng mờ và bụi; xả hơi khi hồi sức; chuyển pha rung,
  tia lửa đỏ; khi gục nổ dây chuyền rồi mờ dần.
- Mọi hạt, vòng sóng và số dùng pool cố định, không cấp phát mỗi frame.

## 11. Giới hạn hiện tại

- Chưa có âm thanh và rung (pause không có gì để dừng); cài đặt chỉ có nút cảm ứng và debug.
- Linh kiện chỉ rơi khi hạ địch, chưa có vật phẩm nhặt riêng.
- Nhân vật dùng một tư thế vẽ từ ảnh thiết kế; chân tay cử động bằng uốn lưới nên biên độ có giới hạn (chân không bắt chéo hẳn, tay không gập khuỷu rõ). Hoạt ảnh từng khung đầy đủ cần họa sĩ vẽ thêm khung cùng tên vùng atlas.
- Collision layer/mask được thể hiện bằng phe đạn và các vòng duyệt riêng, không có hệ thống mask chung.
- Chưa tự động hóa kiểm tra cảm ứng thật; các mục nghiệm thu trên thiết bị ghi trong README.
