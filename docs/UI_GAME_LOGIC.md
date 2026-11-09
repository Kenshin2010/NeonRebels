# NEON REBELS — Logic 4 màn hình: Menu, Bản đồ, Chọn đội, Xưởng

Tài liệu mô tả hành vi **đã triển khai thật** trong mã nguồn. Logic nằm ở package thuần Kotlin
`core/src/main/java/com/manroid/neonrebels/progress/` (không phụ thuộc libGDX, có kiểm thử);
màn hình libGDX chỉ gọi service và vẽ.

| Trách nhiệm | Lớp |
| --- | --- |
| Cấu hình (nhân vật, chiến dịch, bảng nâng cấp, phần thưởng) | `progress/Definitions.kt` (`GameData`) |
| Công thức thông số dùng chung gameplay/Chọn đội/Xưởng | `progress/Stats.kt` (`StatCalculator`) |
| Dữ liệu tiến trình | `progress/GameState.kt` |
| Mã hóa, kiểm tra, migration dữ liệu lưu | `progress/SaveCodec.kt` |
| Kho lưu duy nhất, bản dự phòng, phục hồi | `progress/ProgressRepository.kt`, `PreferencesStore.kt` |
| Mở khóa, chọn màn, tiếp tục, nhận thưởng | `progress/CampaignService.kt` |
| Đội nháp, xác nhận đội, khởi tạo lượt | `progress/TeamService.kt` (`DraftTeam`, `RunSetup`) |
| Giao dịch nâng cấp | `progress/UpgradeService.kt` |
| Điều hướng, sở hữu service | `MainGame.kt` (`open`, `openWorkshop`, `returnFromWorkshop`, `play`) |
| Khung UI: nút thật, modal, chống nhấn lặp | `MenuBase.kt` (`UiButton`, `navigate`) |
| Màn hình | `MenuScreen.kt`, `CampaignScreen.kt`, `TeamScreen.kt`, `WorkshopScreen.kt`, `GameScreen.kt` (bảng kết quả) |

## 1. Luồng màn hình

```
Menu → Bản đồ → Chọn đội → Gameplay → Kết quả (trong GameScreen)
Menu / Bản đồ / Chọn đội / Kết quả → Xưởng → quay lại đúng màn đã gọi
Menu → Cài đặt (modal) → Menu
```

- **Xưởng** mở bằng `MainGame.openWorkshop(from)`: màn gọi được *giữ lại* (không dispose) và
  Back trong Xưởng trả về đúng instance đó. Vì vậy Chọn đội giữ nguyên đội nháp và màn đang chọn,
  Bản đồ giữ khu/màn, Kết quả giữ nguyên bảng kết quả (không chốt thưởng lại).
- `MainGame.open(next)` thay màn và dispose màn cũ (và màn đang được giữ, nếu có). Màn chỉ dispose
  texture của chính nó; font, batch, ShapeRenderer là tài nguyên chung do `MainGame` sở hữu.
- **Chống nhấn lặp:** mọi chuyển màn đi qua `MenuBase.navigate { }`. Sau lần đầu, màn bỏ qua mọi
  input cho tới khi được hiện lại, nên nhấn nhanh hai lần không mở hai màn và không tạo hai lượt.
  `GameScreen` có cờ tương tự cho các nút của bảng kết quả.
- **Input:** `show()` gắn input của màn mới; `hide()` gỡ; nhấn được tính khi nhả tay trong nút
  (kéo ra ngoài thì hủy). Mất focus/pause xóa trạng thái nhấn; quay lại từ nền không tự thực hiện
  giao dịch hay vào trận.
- **Modal:** khi mở, chỉ nút của modal nhận input; Back đóng modal trước, sau đó mới quay màn.
  Hộp thoại "dữ liệu lỗi" không đóng bằng Back (bắt buộc chọn).
- Back tại Menu mở hộp thoại "Thoát game?"; xác nhận gọi `Gdx.app.exit()` (không dùng `System.exit`).

## 2. Menu chính

Nút: TIẾP TỤC, CHƠI MỚI, BẢN ĐỒ CHIẾN DỊCH, XƯỞNG NÂNG CẤP, CÀI ĐẶT; logo và key art lấy từ ảnh
thiết kế (nền thở chậm), phiên bản ứng dụng ở góc phải dưới (Android lấy `versionName`, desktop
lấy system property `neonrebels.version`, mặc định `desktop`).

| Trạng thái | Hành vi |
| --- | --- |
| Đang tải | Hiện "Đang tải dữ liệu…", mọi nút bắt đầu bị vô hiệu. Dữ liệu được đọc ở frame thứ hai. |
| Chưa có chiến dịch | CHƠI MỚI là nút chính (vàng). Tiếp tục/Bản đồ/Xưởng vô hiệu (chạm vào hiện lý do). Cài đặt dùng được. |
| Có chiến dịch | TIẾP TỤC là nút chính; Bản đồ, Xưởng bật; CHƠI MỚI hỏi xác nhận. |
| Dữ liệu lỗi | Thử bản dự phòng trước. Nếu được: báo "Đã khôi phục…". Nếu không: hộp thoại THỬ LẠI / CHIẾN DỊCH MỚI; dữ liệu lỗi được giữ ở khóa `campaignCorrupt`, không bị xóa âm thầm. |

- **CHƠI MỚI** (đã có chiến dịch): hộp thoại "Bắt đầu chiến dịch mới? Tiến trình, linh kiện và
  nâng cấp hiện tại sẽ bị đặt lại." — HỦY không đổi dữ liệu; BẮT ĐẦU MỚI mới reset.
- **Chiến dịch mới:** `campaignStarted = true`, 0 linh kiện, An và Vy mở, Khang khóa, đội `[an, vy]`
  (An xuất trận trước), mọi nâng cấp cấp 0, chưa hoàn thành màn nào, chọn `streets_01`. Cài đặt
  không bị đụng tới. Bộ đếm `runId` vẫn tăng tiếp để kết quả cũ không thể được nhận. Lưu thành công
  mới mở Bản đồ; lưu lỗi thì báo và ở lại.
- **TIẾP TỤC:** chọn `lastPlayedLevelId` nếu còn chơi được → màn chưa hoàn thành đầu tiên chơi được
  → màn chơi được gần nhất; lưu lựa chọn rồi mở Bản đồ. Không tự vào trận, không có "tiếp tục giữa màn".

## 3. Bản đồ chiến dịch

5 khu × 3 màn, màn thứ ba là boss:

| Khu (id) | Màn |
| --- | --- |
| Phố mất điện (`neon_streets`) | `streets_01`, `streets_02`, `streets_03` |
| Xưởng thép (`steel_factory`) | `factory_01..03` |
| Đường hầm ngập nước (`flooded_tunnels`) | `tunnels_01..03` |
| Vườn kính trên cao (`sky_garden`) | `garden_01..03` |
| Tháp Lõi Sáng (`core_tower`) | `tower_01..03` |

**Chỉ `streets_01` có gameplay** (chính là màn hiện có, gồm cả trận Thiết Giáp Gác Cổng). Các màn
khác `implemented = false` và hiển thị "Sắp có".

Trạng thái được tách riêng (`LevelStatus`):
- `unlocked` (do tiến trình), `implemented` (có nội dung), `completed`; `playable = unlocked && implemented`.
- Một màn có thể vừa mở khóa vừa "Sắp có" (ví dụ `streets_02` sau khi thắng `streets_01`).

Quy tắc mở khóa (suy ra từ cấu hình + `completedLevels`, không lưu cờ mở khóa riêng):
- Màn đầu khu 1 mở khi có chiến dịch.
- Màn tiếp theo trong khu cần hoàn thành màn trước.
- Khu tiếp theo cần hoàn thành màn boss của khu trước.
- Màn đã hoàn thành chơi lại được.

UI: tuyến đường 5 khu (nút tròn + biển tên, có trạng thái khóa/sắp có/chơi được/hoàn thành), panel
khu đang chọn (tên, tiến độ khu, mô tả, mục tiêu, trạng thái boss hoặc điều kiện mở khu), 3 ô màn,
dòng trạng thái màn (lý do khóa cụ thể / "Nội dung đang được phát triển" / đã hoàn thành), phần
thưởng dự kiến, kỷ lục, nút CHƠI, XƯỞNG, Quay lại.

- Chọn khu chỉ cập nhật panel và tự chọn màn: màn đang nhớ trong khu → màn chưa hoàn thành đầu tiên → màn đầu khu. Khu khóa vẫn xem được mô tả và điều kiện.
- Chọn màn lưu `selectedLevelId` (màn khóa vẫn chọn để xem được).
- CHƠI chỉ bật khi màn chơi được; khi nhấn, `CampaignService.canPlay` được kiểm tra lại rồi mới mở Chọn đội với `levelId`.
- Khi mọi màn đang có nội dung đã hoàn thành: hiện "Đã hoàn thành nội dung hiện có"; không mở màn trống.

## 4. Chọn đội

- Hiện tên màn sắp chơi, 3 thẻ (An, Vy, Khang), 2 ô đội hình có thứ tự (1 · xuất trận, 2 · dự bị),
  nút đổi vị trí, "Đội: n/2", panel nhân vật đang xem, nút XƯỞNG, VÀO TRẬN, Quay lại.
- **Chạm thẻ chỉ để xem.** Thêm/bỏ bằng nút riêng THÊM VÀO ĐỘI / BỎ KHỎI ĐỘI trong panel.
- Đội phải có **đúng hai nhân vật khác nhau**, đều đã mở và có gameplay (`TeamService.selectable`).
- Khi đã đủ hai người, thêm người khác báo "Hãy bỏ một nhân vật trước khi chọn người khác" (không tự thay).
- Bỏ nhân vật ở ô 1 thì nhân vật ô 2 lên ô 1.
- **Khang:** thẻ xem trước, không chọn được; điều kiện mở dự kiến "giải cứu ở màn 2-2" — chỉ mở khi
  màn đó và gameplay Khang thật sự có. Xem thẻ không mở khóa gì.
- **Đội nháp:** khi vào màn, đội đã lưu được chép sang `DraftTeam`. Mọi chỉnh sửa chỉ ở bộ nhớ;
  Back bỏ nháp (đội đã lưu giữ nguyên); đi Xưởng rồi quay lại vẫn giữ nháp.
- Thông số trong panel (máu tối đa, sát thương, hồi chiêu, cấp) lấy từ `StatCalculator` — cùng
  công thức với gameplay và Xưởng; không hiện máu còn lại của lượt trước.

**VÀO TRẬN** (`TeamService.start`): chặn nhấn lặp → kiểm tra lại màn chơi được → kiểm tra đội hợp
lệ → trong **một lần lưu**: đội đã xác nhận, `lastPlayedLevelId`, `selectedLevelId`, cấp `runId` mới →
tạo `RunSetup` (ảnh chụp thông số tại thời điểm vào trận) → `World` mới đầy máu, hồi chiêu 0, input
rỗng. Nếu tạo màn chơi lỗi: ở lại Chọn đội, báo "Không thể tải màn chơi", cho thử lại; không đánh dấu
hoàn thành. Đường chính của màn đi được bằng bất kỳ nhân vật nào.

## 5. Xưởng nâng cấp

- Hiện tổng linh kiện, tab An / Vy / Khang (khóa, không mua được), preview (An trên bệ trong ảnh
  nền; Vy/Khang dùng chân dung), 3 mục Máu tối đa / Sát thương / Hồi kỹ năng với cấp và thanh cấp,
  giá trị hiện tại → cấp tiếp, chi phí, "Cần thêm X linh kiện", nút NÂNG CẤP.
- Mỗi nhân vật có cấp riêng, mỗi loại cấp **0–5**, linh kiện dùng chung, mua từng cấp, không hoàn tiền.
- Nâng cấp áp dụng từ lượt chơi mới; không sửa thông số của lượt đang chạy (lượt dùng `RunSetup` đã chụp).

Chỉ số gốc (cấp 0, trong `GameConfig`): An 1200 máu / 100 sát thương / 8 s hồi kỹ năng;
Vy 1000 / 60 / 6 s. Với L = cấp:

- Máu = round(gốc × (1 + 0.10 × L))
- Sát thương = round(gốc × (1 + 0.08 × L))
- Hồi kỹ năng = gốc × (1 − 0.05 × L) — giữ độ chính xác đầy đủ; UI hiện tối đa hai chữ số thập phân.

Chi phí mua cấp 1→5 (`GameData.upgrades`): Máu 100, 200, 350, 550, 800; Sát thương và Hồi kỹ năng
150, 300, 500, 750, 1100. Ví dụ: An Máu 0→1: 1200 → 1320, giá 100; An Sát thương 0→1: 100 → 108,
giá 150; Vy Hồi kỹ năng 0→1: 6s → 5.7s, giá 150.

Trong gameplay, "sát thương" là sát thương gốc của đòn thường: kiếm An (+18% mỗi bước combo),
lướt chém = 1.9×, đạn Vy = 1×, đạn xuyên = 2.3× (`GameConfig`). Máu/sát thương kẻ địch được nhân
theo cùng tỉ lệ để độ khó tương đương bản trước.

Trạng thái nút: đủ tiền và chưa tối đa → NÂNG CẤP; thiếu tiền → vô hiệu + "Cần thêm X linh kiện";
cấp 5 → ĐÃ TỐI ĐA, không hiện giá; đang xử lý → khóa ("ĐANG XỬ LÝ"); nhân vật khóa → ĐÃ KHÓA.

**Giao dịch** (`UpgradeService.purchase`): từ chối nếu đang có giao dịch khác (BUSY) → đọc trạng thái
mới nhất → kiểm tra nhân vật, cấp, giá, số dư → trừ tiền và tăng cấp trong **một lần lưu** trên bản
sao → chỉ khi lưu thành công mới thay trạng thái và UI hiện "Nâng cấp thành công"; lưu lỗi thì giữ
nguyên và cho thử lại. Một lần nhấn mua đúng một cấp; giá được tính lại từ cấp mới. Không có hộp
thoại xác nhận cho từng lần mua.

## 6. Linh kiện và nhận thưởng

- Kẻ địch bị hạ rơi linh kiện vào **quỹ tạm của lượt** (`World.collectedParts`): robot 10, drone 15,
  boss 40 (`GameConfig.DROP_*`). Chưa có vật phẩm nhặt riêng; hạ địch được tính là nhặt.
- **Thắng:** linh kiện tạm + thưởng màn (thường 100, boss 250) + 100 nếu là lần thắng đầu màn đó.
  Chơi lại vẫn nhận thưởng màn và linh kiện nhặt được, không nhận lại thưởng lần đầu.
- **Thua hoặc rời màn từ bảng tạm dừng:** không cộng gì; lượt được chốt là thua. Bảng kết quả và bảng
  tạm dừng có ghi rõ quy tắc này.
- Hệ thống thưởng theo kẻ địch của bản trước (cộng cả khi thua) đã được thay hoàn toàn; chỉ còn một
  đường nhận thưởng là `CampaignService.applyResult`.

**Chống nhận lặp:** mỗi lượt có `runId` tăng dần (cấp khi VÀO TRẬN). `applyResult` ghi kết quả,
linh kiện, hoàn thành màn, kỷ lục và dấu `lastSettledRunId` trong **cùng một lần lưu**. Gọi lại với
cùng `runId` trả về kết quả cũ (không cộng thêm); `runId` cũ hơn hoặc chưa cấp bị từ chối; một lượt
đã chốt thua không thể thành thắng. Chỉ lưu một dấu (không có danh sách runId tăng vô hạn). Kỷ lục
chỉ cập nhật khi thời gian tốt hơn. Nếu lưu lỗi, bảng kết quả hiện LƯU LẠI để thử lại.

## 7. Lưu dữ liệu và phục hồi

- Một nguồn trạng thái: `ProgressRepository.state`. Mọi thay đổi qua `update { }`: làm trên bản sao,
  ghi xuống, chỉ thay trạng thái khi ghi thành công; không cho ghi lồng nhau.
- Ảnh chụp dạng văn bản `khóa=giá trị` có `saveVersion` và `crc` (CRC32) ở cuối, lưu trong libGDX
  Preferences `nova-light-core-v1`, khóa `campaign`; bản hợp lệ trước đó ở `campaignBackup`; dữ
  liệu không đọc được ở `campaignCorrupt`.
- Cài đặt (`showControls`) dùng khóa riêng trong cùng file, không bị chiến dịch mới đụng tới.
- Kiểm tra khi đọc: checksum, phiên bản; khóa lạ bị bỏ qua (thêm trường mới không làm reset); ID không
  còn trong cấu hình bị bỏ; số dư không âm; cấp kẹp 0–5; đội không hợp lệ trở về `[an, vy]`.
- Migration: `SaveCodec.migrate` nâng từng phiên bản (hiện là 1). Dữ liệu từ bản trước khi có ảnh
  chụp (khóa `level1Complete`, `bestSeconds`, `parts`, `level<slot><STAT>` cấp 1–5) được chuyển sang
  phiên bản 1 (cấp giảm 1 cho thang 0–5) rồi xóa khóa cũ.
- Thời điểm lưu: tạo/reset chiến dịch, chọn màn, xác nhận đội vào trận, nâng cấp, chấp nhận kết quả,
  đổi cài đặt. Không flush mỗi frame.
- **Giới hạn:** Preferences ghi lại cả file khi flush, không phải giao dịch bền vững khi mất điện.
  Checksum + bản dự phòng giúp phát hiện và phục hồi file hỏng, nhưng không bảo đảm nguyên tử tuyệt
  đối. Không lưu: Screen/texture, máu hiện tại, đạn, kẻ địch, hitbox, trạng thái chạm, Activity.

## 7b. Màn chiến đấu

- HUD (`view/GameHud.kt`) chỉ đọc `World`; chân dung An/Vy/boss là ảnh nhỏ do `GameHud` sở hữu và dispose cùng `GameScreen`.
- Hội thoại Linh (`GameScreen`): lời mở đầu khi vào màn và lời cảnh báo khi boss thức dậy, chỉ khi màn chưa hoàn thành (suy ra từ `completedLevels`, không lưu cờ riêng). Trong hội thoại `world.paused = true`, input chiến đấu bị xóa, nút tạm dừng ẩn; mất focus vẫn giữ nguyên hội thoại. TIẾP/chạm sang câu sau, BỎ QUA/Back kết thúc.

## 8. Chưa triển khai

- Gameplay cho 14/15 màn (chỉ có `streets_01`); các màn khác hiện "Sắp có", không vào được.
- Nhân vật Khang và nhiệm vụ giải cứu ở 2-2.
- Vật phẩm linh kiện riêng để nhặt (đang tính theo kẻ địch bị hạ).
- Âm thanh (thiết kế có nút loa; chưa đưa vào). Cài đặt hiện chỉ có bật/tắt nút cảm ứng.
- Hoạt ảnh nhiều khung cho nhân vật/robot/boss: art cắt từ thiết kế chỉ có một tư thế mỗi nhân vật (`scripts/build-world-art.py`); tư thế gục trong ảnh 03 quá nhỏ để tách nền nên dùng hiệu ứng ngã.
- Tiếp tục giữa màn sau khi đóng ứng dụng (cố ý không hỗ trợ).
- Ghi file nguyên tử kiểu `write temp + rename` thay cho Preferences.

## 9. Kiểm thử

- `core/src/test/.../progress/ProgressChecks.kt` (JUnit `ProgressTest`): khởi tạo chiến dịch, hủy/xác
  nhận reset, mở khóa màn/khu, phân biệt khóa và chưa triển khai, tiếp tục, đội đủ hai/không trùng/
  chọn được, đổi thứ tự, công thức và chi phí, mua đúng số dư, thiếu tiền, cấp tối đa, giao dịch trùng,
  lưu lỗi, thưởng lần đầu/chơi lại, callback lặp, lượt thua không thành thắng, kỷ lục, dữ liệu sửa/cắt
  cụt, phiên bản mới hơn, khóa lạ, phục hồi từ dự phòng, dữ liệu hỏng hoàn toàn, migration bản cũ.
- `ModelChecks` (JUnit `GameplayTest`): gameplay với thang chỉ số mới, `Loadout` theo thứ tự đội,
  linh kiện rơi một lần mỗi kẻ địch.
- Chạy: `./scripts/test-model.sh` hoặc `./gradlew :core:test`.
