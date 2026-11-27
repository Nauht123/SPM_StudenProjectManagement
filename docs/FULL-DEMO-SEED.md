# Seed data mở rộng — SPM

Dữ liệu hoàn toàn giả lập, dùng cho demo và kiểm thử local. Không dùng tài khoản demo trên production.

## Kích hoạt (giữ dữ liệu cũ)

1. Mở đúng thư mục `spm` chứa `docker-compose.yml`. Nếu đã chạy trên máy, giữ nguyên thư mục dự án, `.env` và tên Compose project cũ; chép các file thay đổi vào dự án đó để tiếp tục dùng đúng volume.
2. Kiểm tra `docker compose ps` và `docker volume ls`. Nên sao lưu DB trước (ví dụ trong PowerShell chạy `docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction spm > /tmp/spm-before-seed.sql'` rồi `docker compose cp db:/tmp/spm-before-seed.sql ./spm-before-seed.sql`). Không dùng `docker compose down -v`.
3. Trong `.env` hiện tại, thêm `SEED_FULL_DEMO_DATA=true` (nếu đã có thì sửa giá trị, không ghi đè các biến khác). Nếu chưa có `.env`, có thể chép `.env.example` và đặt biến này thành true.
4. `docker compose up -d --build backend`
5. `docker compose logs --tail=150 backend` — tìm `FULL DEMO: created ...` hoặc `namespace already exists; skipping` nếu đã chạy trước. `docker compose up -d` để bảo đảm frontend đang chạy.
6. Mở http://localhost:3000 và đăng nhập bằng tài khoản bên dưới. Sau khi seed thành công, đổi `SEED_FULL_DEMO_DATA=false` trong `.env`, chạy `docker compose up -d backend`. Dữ liệu không bị xóa.

## Tài khoản

Mật khẩu chung cho tài khoản mới: `SpmDemo2026!` (chỉ demo).

- `spmdemo.gv1`: Alpha, Beta, Gamma, Epsilon.
- `spmdemo.gv2`: Delta (chờ duyệt), Zeta.
- `spmdemo.gv3`: Omega, Sigma.
- `spmdemo.sv01` … `spmdemo.sv30`: sinh viên; 24 người thuộc nhóm, 6 người chưa thuộc nhóm. Trưởng nhóm lần lượt: sv01, sv04, sv07, sv10, sv13, sv16, sv19, sv22 (tất cả có tiền tố `spmdemo.`).
- Admin cũ giữ nguyên, không tạo admin mới. Seeder cơ bản vẫn hoạt động như trước nếu DB trống và `SEED_DEMO_DATA=true`.

## Kịch bản

| Nhóm | Trạng thái |
|---|---|
| Alpha | 10/15 task DONE; có task chờ xác nhận |
| Beta | 3/15 DONE; milestone quá hạn |
| Gamma | 15/15 DONE |
| Delta | Đăng ký chờ duyệt, chưa có milestone/task |
| Epsilon | 7/15 DONE; bài nộp milestone đầu trễ |
| Zeta | 6/15 DONE; yêu cầu thêm thành viên |
| Omega | 8/15 DONE; task chờ xác nhận |
| Sigma | 5/15 DONE; task bị yêu cầu sửa |

Số lượng dự kiến tăng thêm: 33 tài khoản, 12 đề tài, 8 nhóm, 8 đăng ký (7 duyệt, 1 chờ), 21 milestone chính, 21 milestone phụ, 105 task, 21 bài nộp (kèm file TXT thực), 21 feedback, 63 contributor records, 42 task comments, 36 peer ratings, 3 yêu cầu thay đổi nhóm, 82 thông báo, lịch sử thành viên/task/phân công/nộp task. Ngày tháng tương đối ngày chạy lần đầu. Không tạo dữ liệu GitHub giả.

## Kiểm tra SQL (đọc, không sửa)

```sql
SELECT role, COUNT(*) FROM users WHERE username LIKE 'spmdemo.%' GROUP BY role;
SELECT COUNT(*) FROM teams WHERE name LIKE '[DEMO] %';
SELECT r.status, COUNT(*) FROM registrations r JOIN teams t ON t.id=r.team_id WHERE t.name LIKE '[DEMO] %' GROUP BY r.status;
SELECT COUNT(*) FROM milestones m JOIN teams t ON t.id=m.team_id WHERE t.name LIKE '[DEMO] %';
SELECT k.status, COUNT(*) FROM tasks k JOIN milestones m ON m.id=k.milestone_id JOIN teams t ON t.id=m.team_id WHERE t.name LIKE '[DEMO] %' GROUP BY k.status;
SELECT COUNT(*) FROM submissions s JOIN milestones m ON m.id=s.milestone_id JOIN teams t ON t.id=m.team_id WHERE t.name LIKE '[DEMO] %';
```

Namespace `spmdemo.*` được dành riêng. Nếu đã tồn tại bất kỳ tài khoản thuộc namespace, seeder bỏ qua toàn bộ, không tự sửa/xóa dữ liệu; không tự phục hồi dữ liệu demo bị người dùng xóa sau đó. Các bản ghi DB được tạo trong một transaction; file mới được dọn khi transaction rollback thông thường. Không chạy đồng thời nhiều instance seed vào cùng DB.

## File thay đổi

- `backend/src/main/java/vn/huflit/spm/config/FullDemoSeeder.java` (mới)
- `backend/src/main/java/vn/huflit/spm/config/DataSeeder.java` (thứ tự chạy)
- `backend/src/main/resources/application.properties`
- `docker-compose.yml`
- `.env.example`
- `README.md` và tài liệu này

Kiểm tra tác giả bản seed: hai lớp Java được biên dịch bằng `javac --release 17` với dependency từ JAR gốc. Chưa thực thi Spring Boot/MySQL/Docker trong môi trường chỉnh sửa; phải chạy lại Maven test và kiểm thử giao diện sau khi tích hợp. Kết quả Surefire cũ (nếu có trong ZIP) không phải kết quả kiểm thử seed mới.
