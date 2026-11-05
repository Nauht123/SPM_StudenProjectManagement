# Student Project Management System – Quản lý đồ án sinh viên

Đề tài 4 – môn Công nghệ phần mềm nâng cao (CNPMNC).
Ứng dụng web hỗ trợ giảng viên và sinh viên quản lý đồ án từ đăng ký đề tài đến nộp sản phẩm.

**Công nghệ:** ReactJS (Vite) · Spring Boot 3 (Java 17, Spring Security + JWT, Spring Data JPA) · MySQL 8 · Docker Compose · GitHub Actions.

## Chức năng

| Vai trò | Chức năng |
| --- | --- |
| Sinh viên – thành viên | Đăng ký tài khoản; xem milestone, bảng công việc; **thực hiện task được giao**: bắt đầu làm, báo hoàn thành (kèm file/ghi chú tùy chọn); nộp bài cho milestone chính; xem phản hồi; nhận thông báo |
| Sinh viên – nhóm trưởng | Mọi quyền của thành viên và: tạo nhóm, thêm/xóa thành viên theo MSSV (tối đa 5); đăng ký/hủy đăng ký đề tài; **tạo milestone phụ**; **thêm/sửa/xóa/giao task** (chỉ nhóm trưởng và admin); **xác nhận** task hoàn thành hoặc **yêu cầu sửa** |
| Giảng viên | Tạo/sửa/đóng/xóa đề tài; duyệt/từ chối đăng ký; **tạo và quản lý milestone chính** của nhóm mình hướng dẫn; xem và tải bài nộp; phản hồi theo từng bài nộp; dashboard tiến độ các nhóm. **Không được thêm hoặc sửa task** |
| Quản trị viên | Tạo tài khoản; xem tổng quan; có quyền ghi đè trong nhóm (milestone chính/phụ, task) |

### Phân cấp milestone và luồng task

```
Giảng viên ──► Milestone chính (hạn nộp, nộp bài, phản hồi)
Nhóm trưởng ──► Milestone phụ (hạn không sau hạn milestone chính)
Nhóm trưởng ──► Task (nằm trong milestone phụ, giao cho thành viên)
Thành viên ──► làm task
```

Bảng công việc có 4 cột: **Cần làm → Đang làm → Chờ xác nhận → Hoàn thành**.

1. Thành viên được giao bấm *Bắt đầu làm* (Cần làm → Đang làm).
2. Thành viên bấm *Báo hoàn thành*, có thể đính kèm file hoặc ghi chú kết quả; hệ thống ghi lại ngày giờ nộp gần nhất (Đang làm → Chờ xác nhận) và thông báo cho nhóm trưởng.
3. Nhóm trưởng bấm *Xác nhận* (→ Hoàn thành) hoặc *Yêu cầu sửa* kèm nội dung (→ Đang làm, thành viên thấy lý do và nộp lại).

Quy tắc nghiệp vụ chính: mỗi sinh viên chỉ thuộc một nhóm; mỗi nhóm chỉ có một đăng ký đang chờ/đã duyệt; đề tài không nhận quá số nhóm tối đa; nhóm được duyệt thì khóa danh sách thành viên và mới tạo được milestone; task chỉ nằm trong milestone phụ; hạn milestone phụ/task không được sau hạn của cấp trên; bài nộp chỉ gắn với milestone chính; bài nộp sau 24:00 của ngày hạn bị đánh dấu trễ; mỗi lần nộp tạo một phiên bản mới.

## Chạy nhanh bằng Docker

```bash
cp .env.example .env        # (tùy chọn) đổi mật khẩu DB và JWT_SECRET
docker compose up --build
```

- Giao diện: http://localhost:3000
- API: http://localhost:8080/api

## Tài khoản demo (dữ liệu mẫu, tạo khi CSDL trống)

| Vai trò | Tên đăng nhập | Mật khẩu |
| --- | --- | --- |
| Quản trị | `admin` | `admin123` |
| Giảng viên | `gv.khoa`, `gv.lan` | `gv123456` |
| Sinh viên | `sv01` … `sv06` (MSSV `SV001` … `SV006`) | `sv123456` |

Khi triển khai thật: đặt `SEED_DEMO_DATA=false` và đổi `JWT_SECRET`, `DB_PASSWORD`.

## Chạy khi phát triển (không dùng Docker)

Yêu cầu: JDK 17+, Maven 3.9+, Node 20+, MySQL 8 đang chạy (mặc định user `root` / mật khẩu `root`, DB `spm` tự tạo).

```bash
# Backend
cd backend
mvn spring-boot:run          # http://localhost:8080
mvn test                     # chạy unit test

# Frontend (cửa sổ khác)
cd frontend
npm install
npm run dev                  # http://localhost:5173 (proxy /api -> :8080)
```

Biến môi trường backend: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `UPLOAD_DIR`, `CORS_ORIGINS`, `SEED_DEMO_DATA`.

## Kiến trúc

Client–Server, backend phân lớp: `web` (REST controller) → `service` (nghiệp vụ, phân quyền tập trung ở `AccessService`) → `repo` (Spring Data JPA) → MySQL. Entity không trả trực tiếp ra API, dùng DTO (`dto/Dtos.java`) và `Mapper`. Xác thực stateless bằng JWT, phân quyền theo vai trò (`@PreAuthorize`) và theo quan hệ với nhóm (thành viên / nhóm trưởng / giảng viên hướng dẫn).

```
backend/src/main/java/vn/huflit/spm
├── config/    SecurityConfig, DataSeeder
├── security/  JwtService, JwtAuthFilter
├── domain/    Entity + enum (User, Topic, Team, Registration, Milestone, Task, Submission, Feedback, Notification)
├── repo/      Spring Data repositories
├── dto/       Request/Response DTO
├── service/   Auth, Topic, Team, Registration, Milestone, Task, Submission(+Feedback), Dashboard, Notification, AccessService, FileStorage
└── web/       Controller + GlobalExceptionHandler
frontend/src   App.jsx (route + layout), pages/*, api.js, auth.jsx
```

## API chính

| Nhóm | Endpoint |
| --- | --- |
| Auth | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me` |
| Admin | `GET/POST /api/admin/users` |
| Đề tài | `GET /api/topics`, `GET /api/topics/mine`, `POST/PUT/DELETE /api/topics[/{id}]` |
| Đăng ký | `POST /api/registrations`, `DELETE /api/registrations/{id}`, `GET /api/registrations/mine`, `GET /api/registrations/lecturer`, `POST /api/registrations/{id}/decision` |
| Nhóm | `GET /api/teams/me`, `GET /api/teams`, `GET /api/teams/{id}`, `POST /api/teams`, `POST /api/teams/{id}/members`, `DELETE /api/teams/{id}/members/{userId}`, `GET /api/teams/{id}/progress`, `GET /api/dashboard/teams` |
| Milestone | `GET/POST /api/teams/{id}/milestones` (body có `parentId` = null: milestone chính, khác null: milestone phụ), `GET/PUT/DELETE /api/milestones/{id}` |
| Task | `GET/POST /api/milestones/{id}/tasks`, `PUT/DELETE /api/tasks/{id}`, `POST /api/tasks/{id}/start`, `POST /api/tasks/{id}/submit` (multipart, file/note tùy chọn), `POST /api/tasks/{id}/confirm`, `POST /api/tasks/{id}/reject`, `GET /api/tasks/{id}/result` |
| Bài nộp / phản hồi | `GET/POST /api/milestones/{id}/submissions` (multipart), `GET /api/submissions/{id}/download`, `GET/POST /api/submissions/{id}/feedback` |
| Thông báo | `GET /api/notifications`, `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` |

## Kiểm thử

Unit test (JUnit 5 + Mockito) trong `backend/src/test`: quy tắc nộp trễ, quy tắc đăng ký đề tài (đủ nhóm, đề tài đóng), luồng task (báo hoàn thành, xác nhận, yêu cầu sửa, ràng buộc hạn) và phân quyền (giảng viên không được quản lý task). CI (`.github/workflows/ci.yml`) chạy `mvn verify` và `npm run build` cho mỗi push/PR.

## Hạn chế đã biết (technical debt)

- Quan hệ `@ManyToOne`/`@ManyToMany` đang dùng EAGER để đơn giản hóa; cần chuyển sang LAZY + fetch join khi dữ liệu lớn.
- Thêm thành viên bằng MSSV (không có bước mời/chấp nhận); thông báo chỉ trong ứng dụng (không email) và được cập nhật bằng polling 30 giây.
- File nộp lưu trên đĩa của server (volume Docker), chưa dùng object storage; chưa quét virus/giới hạn loại file.
- Schema do Hibernate `ddl-auto=update` tạo, chưa dùng công cụ migration (Flyway/Liquibase).
- Chưa có integration test và test giao diện tự động.
