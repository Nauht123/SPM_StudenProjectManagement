# Student Project Management System – Quản lý đồ án sinh viên

Đề tài 4 – môn Công nghệ phần mềm nâng cao (CNPMNC).
Ứng dụng web hỗ trợ giảng viên và sinh viên quản lý đồ án từ đăng ký đề tài đến nộp sản phẩm.

**Công nghệ:** ReactJS (Vite) · Spring Boot 3 (Java 17, Spring Security + JWT, Spring Data JPA) · MySQL 8 · Docker Compose · GitHub Actions.

## Chức năng

| Vai trò | Chức năng |
| --- | --- |
| Sinh viên – thành viên | Đăng ký tài khoản; xem milestone, bảng công việc; **thực hiện task được giao**: bắt đầu làm, báo hoàn thành (kèm file/ghi chú tùy chọn); **đề xuất task mới** cho nhóm trưởng duyệt; nộp bài cho milestone chính và chọn những bạn thực sự đóng góp; xem lịch sử task, đóng góp cá nhân và phản hồi; nhận thông báo |
| Sinh viên – nhóm trưởng | Mọi quyền của thành viên và: tạo nhóm, thêm/xóa thành viên theo MSSV (tối đa 5); đăng ký/hủy đăng ký đề tài; **tạo milestone phụ**; **thêm/sửa/giao/hủy task** kèm độ khó, trọng số, khối lượng (chỉ nhóm trưởng và admin); **duyệt hoặc từ chối đề xuất task**; **xác nhận** task hoàn thành (có thể chấm chất lượng 1–5) hoặc **yêu cầu sửa**; xem đóng góp của cả nhóm; lưu trữ (archive) milestone phụ |
| Giảng viên | Tạo/sửa/đóng/xóa đề tài; duyệt/từ chối đăng ký; **tạo và quản lý milestone chính** của nhóm mình hướng dẫn; xem và tải bài nộp (kèm người upload và người đóng góp); phản hồi theo từng bài nộp; dashboard tiến độ các nhóm; **xem đóng góp cá nhân và lịch sử task** của từng thành viên; lưu trữ milestone chính. **Không được thêm hoặc sửa task** |
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

### Task nâng cao (giai đoạn 1)

- **Trọng số Task:** mỗi task có *độ khó* (1–5), *trọng số* (1–10) và *khối lượng ước tính* (giờ). Tiến độ nhóm / milestone được tính **theo trọng số** (tổng trọng số task đã xong ÷ tổng trọng số task còn hiệu lực), không còn chỉ đếm số task. Task bị hủy, đề xuất chưa duyệt và milestone đã lưu trữ không tính vào tiến độ.
- **Đề xuất task:** thành viên gửi đề xuất (trạng thái *SUGGESTED*), nhóm trưởng nhận thông báo, duyệt (chốt người giao, trọng số, độ khó, hạn → *Cần làm*) hoặc từ chối kèm lý do. Người đề xuất có thể rút lại.
- **Lịch sử Task:** mọi thay đổi (tạo, sửa từng trường, giao/đổi người, bắt đầu, báo hoàn thành, xác nhận, yêu cầu sửa, hủy) ghi vào `task_history`; mỗi lần phân công lưu người giao – người được giao – thời điểm (`task_assignment_history`); **mỗi lần báo hoàn thành là một bản ghi** (`task_submission_history`, kèm file/ghi chú, kết quả duyệt và số lần bị yêu cầu sửa), không chỉ lần cuối.
- **Hủy thay vì xóa:** task đã giao hoặc đã có hoạt động chỉ được *hủy* (trạng thái *CANCELLED*, giữ lịch sử, báo cho người thực hiện). Chỉ task chưa giao, chưa làm mới xóa hẳn được. Milestone đã có bài nộp / milestone phụ / task được *lưu trữ (archive)*, chỉ xem, có thể khôi phục.
- **Người upload ≠ người đóng góp:** khi nộp bài milestone chính, chọn những thành viên thực sự tham gia (`submission_contributors`); người upload luôn được ghi nhận. Điểm đóng góp không dựa vào người upload.
- **Đóng góp cá nhân** (`GET /api/teams/{id}/contributions`): số task giao/xong/đúng hạn/trễ hạn/chưa xong, số lần bị yêu cầu sửa, tiến độ cá nhân theo trọng số, số bài nộp upload/đóng góp và **điểm đóng góp**. Giảng viên hướng dẫn, admin và nhóm trưởng xem cả nhóm; thành viên chỉ xem của mình.

Điểm một task đã được xác nhận = `trọng số × (0.7 + 0.1×độ khó) × (0.5 + 0.1×điểm chất lượng, chưa chấm = 1.0) × (1.0 đúng hạn / 0.9 trễ) × max(0.7, 1 − 0.05×số lần bị yêu cầu sửa)`. Điểm cá nhân là tổng điểm các task đã xong; tỷ lệ đóng góp = điểm cá nhân ÷ tổng điểm nhóm. Task chưa xong, bị hủy hoặc mới chỉ là đề xuất không có điểm.

Quy tắc nghiệp vụ chính: mỗi sinh viên chỉ thuộc một nhóm; mỗi nhóm chỉ có một đăng ký đang chờ/đã duyệt; đề tài không nhận quá số nhóm tối đa; nhóm được duyệt thì khóa danh sách thành viên và mới tạo được milestone; task chỉ nằm trong milestone phụ; hạn milestone phụ/task không được sau hạn của cấp trên; bài nộp chỉ gắn với milestone chính; bài nộp sau 24:00 của ngày hạn bị đánh dấu trễ; mỗi lần nộp tạo một phiên bản mới.

### Dashboard đóng góp (giai đoạn 2)

- **Dashboard giảng viên** (`/dashboard`): so sánh các nhóm — tiến độ theo trọng số, task hoàn thành đúng hạn/trễ hạn, task quá hạn chưa xong, số lần sửa lại, mục **Cần chú ý** (thành viên chưa được giao task, đóng góp quá thấp hoặc tập trung vào một người, bị yêu cầu sửa từ 3 lần, task quá hạn).
- **Đóng góp cá nhân** (`/contributions`, giảng viên/admin): từng nhóm một bảng so sánh thành viên (điểm, tỷ lệ, đúng hạn, trễ hạn, chưa xong, sửa lại, tiến độ cá nhân). Trang nhóm `/teams/{id}/contributions` hiển thị riêng một nhóm; trang chi tiết nhóm chỉ giữ phần tóm tắt và liên kết sang đây để không trùng thống kê.
- **Chi tiết thành viên** (`/teams/{id}/members/{userId}`): điểm và tỷ lệ đóng góp, biểu đồ đúng hạn/trễ hạn/quá hạn chưa xong, danh sách task kèm trọng số, độ khó, chất lượng, điểm; **theo dõi sửa lại** (mỗi lần bị yêu cầu sửa: task, lần nộp, người duyệt, nội dung); **lịch sử nộp cá nhân** (báo hoàn thành task và bài nộp milestone mà thành viên upload hoặc đóng góp, kèm kết quả duyệt và đúng/trễ hạn).
- **Xuất báo cáo:** Excel (.xlsx) cho cả nhóm hoặc từng cá nhân, và nút *In / Lưu PDF* (in trang báo cáo ra PDF bằng trình duyệt). Chỉ giảng viên hướng dẫn, nhóm trưởng và admin được xuất; thành viên chỉ xem phần của mình.

### Đánh giá chéo ẩn danh và điều chỉnh điểm (giai đoạn 3)

- **Đánh giá chéo theo từng milestone chính** (`/milestones/{id}`, mục *Đánh giá chéo*): mỗi thành viên (kể cả nhóm trưởng) chấm **tất cả bạn cùng nhóm** theo hai tiêu chí thang 1–5: *mức độ đóng góp* và *độ nặng của công việc được giao*, kèm nhận xét tùy chọn. Có thể sửa lại cho đến khi milestone bị lưu trữ.
- **Ẩn danh:** không API nào trả về người đánh giá. Người đánh giá chỉ đọc lại bài chấm của chính mình; giảng viên/admin xem **kết quả tổng hợp** (trung bình từng tiêu chí, nhận xét sắp theo chữ cái để không lộ thứ tự nộp) và chỉ biết *ai chưa đánh giá* (không biết đánh giá nội dung). Thành viên chỉ thấy kết quả dành cho mình và chỉ khi có ít nhất 2 lượt đánh giá. Trung bình chung của một milestone = (trung bình đóng góp + trung bình độ nặng) ÷ 2.
- **Điều chỉnh điểm của giảng viên** (`/teams/{id}/evaluation`): giảng viên hướng dẫn cộng/trừ điểm đóng góp của thành viên, **bắt buộc có lý do** (tối thiểu 5 ký tự), có thể gắn với một milestone chính. Mỗi điều chỉnh được lưu lại, thông báo cho thành viên, có thể **thu hồi** nhưng không bị xóa hay sửa.
- **Điểm cuối cùng** = điểm tự động (theo task) + các điều chỉnh còn hiệu lực, không âm. Đánh giá chéo hiển thị cạnh điểm để giảng viên tham khảo, không tự động cộng vào điểm. Giảng viên/admin xem cả nhóm; thành viên (kể cả nhóm trưởng) chỉ xem điểm và lý do điều chỉnh của chính mình.

### Tích hợp GitHub (giai đoạn 3B)

- Nhóm trưởng (hoặc admin) liên kết repo của nhóm bằng URL và **token cá nhân** (có thể bỏ trống nếu repo công khai). Token được mã hóa AES-GCM trước khi lưu (khóa lấy từ biến môi trường `GITHUB_TOKEN_SECRET`, mặc định dùng `JWT_SECRET`) và không bao giờ trả về qua API.
- Mọi thành viên và giảng viên hướng dẫn xem được thống kê; bấm "Đồng bộ ngay" để lấy commit và pull request mới (đồng bộ tăng dần, cách nhau tối thiểu 30 giây để tránh giới hạn của GitHub).
- Mỗi thành viên khai báo username GitHub của mình (nhóm trưởng khai báo hộ được). Commit/PR được gán cho thành viên khi đọc, nên đổi tài khoản thì thống kê cập nhật theo; commit của tài khoản chưa khai báo hiện ở mục "chưa gán".
- Thống kê: commit, PR đã tạo, PR đã merge, hoạt động gần nhất theo từng thành viên; biểu đồ commit 8 tuần; danh sách commit và PR gần đây. Trang: `/teams/{id}/github`.
- Hướng phát triển: webhook để cập nhật thời gian thực thay cho đồng bộ thủ công, gắn commit với task qua mã `#id` trong commit message.

### Bình luận, nhật ký hoạt động, lịch (giai đoạn 3C)

- **Bình luận task:** thành viên, giảng viên hướng dẫn và admin cùng trao đổi dưới mỗi task (mở "Lịch sử & bình luận"). Người đang làm task và nhóm trưởng được thông báo; chỉ tác giả hoặc admin xóa được bình luận.
- **Nhật ký hoạt động** (`/teams/{id}/activity`): gộp từ lịch sử task, bình luận, milestone, thay đổi thành viên và pull request GitHub, mới nhất trước. Đây là dữ liệu đọc gộp từ các bảng lịch sử sẵn có nên không thể lệch với dữ liệu gốc.
- **Lịch sử thay đổi thành viên:** ghi lại tạo nhóm, thêm, xóa thành viên (ai làm, lúc nào); chỉ ghi thêm, không sửa/xóa. Chỉ có dữ liệu từ khi có tính năng này.
- **Lịch** (`/teams/{id}/calendar`): lịch tháng gồm hạn milestone (không tính milestone đã lưu trữ) và hạn task còn hiệu lực; task trễ hạn tô đỏ; sinh viên có thể lọc "chỉ task của tôi".

### Giao diện (giai đoạn 3D)

- Chủ đề màu xanh dịu (kiểu TaskFlow): thanh trên và menu dạng thẻ bo góc, thẻ thống kê màu pastel, biểu đồ dùng bảng màu xanh.
- Trang nhóm dạng bảng phân cấp (kiểu monday.com): mỗi milestone chính là một nhóm có màu riêng, thu gọn/mở rộng được; bên trong là bảng cột Mục / Trạng thái (ô màu) / Tiến độ (thanh %) / Hạn / Task, milestone phụ thụt vào bên dưới. Thành viên hiển thị dạng thẻ có avatar.

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
├── domain/    Entity + enum (User, Topic, Team, Registration, Milestone, Task, Submission, Feedback, Notification,
│              TaskHistory, TaskAssignmentHistory, TaskSubmissionHistory, SubmissionContributor,
│              PeerEvaluation, ContributionAdjustment)
├── repo/      Spring Data repositories
├── dto/       Request/Response DTO
├── service/   Auth, Topic, Team, Registration, Milestone, Task, Submission(+Feedback), Contribution, PeerEvaluation, Evaluation, Dashboard, Notification, AccessService, FileStorage
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
| Cộng tác | `GET/POST /api/tasks/{id}/comments`, `DELETE /api/comments/{id}`, `GET /api/teams/{id}/activity`, `GET /api/teams/{id}/member-history`, `GET /api/teams/{id}/calendar?from=&to=` |
| GitHub | `GET/PUT/DELETE /api/teams/{id}/github` (xem / liên kết repo / gỡ), `POST /api/teams/{id}/github/sync`, `PUT /api/teams/{id}/github/accounts` |
| Milestone | `GET/POST /api/teams/{id}/milestones` (body có `parentId` = null: milestone chính, khác null: milestone phụ), `GET/PUT/DELETE /api/milestones/{id}` (xóa chỉ khi chưa có dữ liệu), `POST /api/milestones/{id}/archive`, `POST /api/milestones/{id}/restore` |
| Task | `GET/POST /api/milestones/{id}/tasks` (body có `difficulty`, `weight`, `workloadHours`), `POST /api/milestones/{id}/tasks/suggest`, `PUT/DELETE /api/tasks/{id}`, `POST /api/tasks/{id}/approve-suggestion`, `/reject-suggestion`, `/cancel`, `/start`, `POST /api/tasks/{id}/submit` (multipart, file/note tùy chọn), `POST /api/tasks/{id}/confirm` (body tùy chọn `qualityRating`), `POST /api/tasks/{id}/reject`, `GET /api/tasks/{id}/result`, `GET /api/tasks/{id}/history`, `GET /api/task-submissions/{id}/download` |
| Đóng góp | `GET /api/teams/{id}/contributions`, `GET /api/dashboard/contributions` (tổng hợp theo nhóm + cảnh báo, giảng viên/admin), `GET /api/teams/{id}/members/{userId}/contribution` (chi tiết, sửa lại, lịch sử nộp), `GET /api/teams/{id}/contributions/export` và `GET /api/teams/{id}/members/{userId}/report` (Excel) |
| Đánh giá chéo / điều chỉnh điểm | `GET /api/milestones/{id}/peer-evaluations/mine`, `PUT /api/milestones/{id}/peer-evaluations` (sinh viên), `GET /api/milestones/{id}/peer-evaluations/results`, `GET /api/teams/{id}/evaluation-summary`, `POST /api/teams/{id}/adjustments` (giảng viên hướng dẫn), `DELETE /api/adjustments/{id}` (thu hồi) |
| Bài nộp / phản hồi | `GET/POST /api/milestones/{id}/submissions` (multipart, có thể kèm nhiều `contributorIds`), `GET /api/submissions/{id}/download`, `GET/POST /api/submissions/{id}/feedback` |
| Thông báo | `GET /api/notifications`, `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` |

## Kiểm thử

Unit test (JUnit 5 + Mockito) trong `backend/src/test`: quy tắc nộp trễ, quy tắc đăng ký đề tài (đủ nhóm, đề tài đóng), luồng task (báo hoàn thành, xác nhận, yêu cầu sửa, ràng buộc hạn), lịch sử task / phân công / từng lần nộp, đề xuất và duyệt task, hủy task, lưu trữ milestone, người đóng góp bài nộp, công thức điểm đóng góp, dashboard đóng góp (cảnh báo, phân quyền xem/xuất báo cáo, theo dõi sửa lại, lịch sử nộp cá nhân), file Excel xuất ra và phân quyền (giảng viên không được quản lý task). CI (`.github/workflows/ci.yml`) chạy `mvn verify` và `npm run build` cho mỗi push/PR.

## Hạn chế đã biết (technical debt)

- GitHub: đồng bộ thủ công (chưa webhook), tối đa 500 commit mỗi lần và 300 PR gần nhất; chưa thống kê dòng code thêm/xóa. Chưa kiểm thử được với GitHub thật trong môi trường phát triển của mình, bạn nên thử với một repo thật trước khi demo.

- Quan hệ `@ManyToOne`/`@ManyToMany` đang dùng EAGER để đơn giản hóa; cần chuyển sang LAZY + fetch join khi dữ liệu lớn.
- Thêm thành viên bằng MSSV (không có bước mời/chấp nhận); thông báo chỉ trong ứng dụng (không email) và được cập nhật bằng polling 30 giây.
- File nộp lưu trên đĩa của server (volume Docker), chưa dùng object storage; chưa quét virus/giới hạn loại file.
- Schema do Hibernate `ddl-auto=update` tạo, chưa dùng công cụ migration (Flyway/Liquibase). Các cột mới của `tasks`/`milestones` có giá trị mặc định nên CSDL cũ tự nâng cấp được; task cũ nhận độ khó 3, trọng số 3, khối lượng 4h.
- Điểm đóng góp tính tại thời điểm xem (không lưu bảng `ContributionScore`); chưa có chấm điểm rubric và lịch sử thay đổi thành viên nhóm (giai đoạn sau).
- Đánh giá chéo ẩn danh chỉ ẩn ở tầng ứng dụng/API: nhóm rất nhỏ (2–3 người) vẫn có thể đoán ra người chấm từ nội dung nhận xét; quản trị CSDL vẫn đọc được bảng `peer_evaluations`. Có thể mở rộng bằng cách chỉ lưu bản băm hoặc đóng băng kết quả khi chốt điểm.
- Chưa có integration test và test giao diện tự động.
