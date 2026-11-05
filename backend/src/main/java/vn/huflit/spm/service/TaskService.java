package vn.huflit.spm.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Task nam trong milestone phu. Phan quyen:
 * - Them/sua/giao/huy task, duyet de xuat: chi nhom truong (hoac admin). Giang vien khong duoc them task.
 * - Thanh vien: de xuat task moi; neu duoc giao thi bat dau lam, bao hoan thanh (kem file/ghi chu tuy chon).
 * - Nhom truong: xac nhan (-> DONE) hoac yeu cau sua (-> DOING).
 * Moi thay doi quan trong deu ghi vao TaskHistory; moi lan phan cong vao TaskAssignmentHistory;
 * moi lan bao hoan thanh vao TaskSubmissionHistory.
 */
@Service
public class TaskService {
    /** Tien to ly do huy khi nhom truong tu choi mot de xuat (dung de phan biet voi task bi huy sau khi da duyet). */
    public static final String REJECTED_SUGGESTION_PREFIX = "Từ chối đề xuất: ";

    private final TaskRepository tasks;
    private final MilestoneRepository milestones;
    private final TaskHistoryRepository history;
    private final TaskAssignmentHistoryRepository assignments;
    private final TaskSubmissionHistoryRepository attempts;
    private final AccessService access;
    private final NotificationService notifications;
    private final FileStorage storage;
    private final Mapper mapper;

    public TaskService(TaskRepository tasks, MilestoneRepository milestones, TaskHistoryRepository history,
                       TaskAssignmentHistoryRepository assignments, TaskSubmissionHistoryRepository attempts,
                       AccessService access, NotificationService notifications, FileStorage storage, Mapper mapper) {
        this.tasks = tasks;
        this.milestones = milestones;
        this.history = history;
        this.assignments = assignments;
        this.attempts = attempts;
        this.access = access;
        this.notifications = notifications;
        this.storage = storage;
        this.mapper = mapper;
    }

    public List<TaskDto> list(Long milestoneId) {
        Milestone m = findMilestone(milestoneId);
        access.requireView(m.getTeam());
        return tasks.findByMilestoneIdOrderByIdAsc(milestoneId).stream().map(mapper::task).toList();
    }

    public TaskTimelineDto timeline(Long taskId) {
        Task t = find(taskId);
        access.requireView(t.getMilestone().getTeam());
        return new TaskTimelineDto(
                history.findByTaskIdOrderByCreatedAtAscIdAsc(taskId).stream().map(mapper::taskHistory).toList(),
                assignments.findByTaskIdOrderByAssignedAtAscIdAsc(taskId).stream().map(mapper::taskAssignment).toList(),
                attempts.findByTaskIdOrderByAttemptNoAsc(taskId).stream().map(mapper::taskSubmission).toList());
    }

    // ---------------- tao / de xuat / duyet ----------------
    @Transactional
    public TaskDto create(Long milestoneId, TaskRequest req) {
        Milestone m = findMilestone(milestoneId);
        access.requireLeaderOrAdmin(m.getTeam());
        requireSubMilestone(m);
        requireActive(m);
        User actor = access.current();
        Task t = new Task();
        t.setMilestone(m);
        t.setCreatedBy(actor);
        t.setCreatedAt(LocalDateTime.now());
        checkDueDate(req.dueDate(), m);
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        if (req.difficulty() != null) t.setDifficulty(req.difficulty());
        if (req.weight() != null) t.setWeight(req.weight());
        if (req.workloadHours() != null) t.setWorkloadHours(req.workloadHours());
        tasks.save(t);
        log(t, actor, TaskAction.CREATED, null, TaskStatus.TODO, "Tạo task " + describeWeights(t));
        changeAssignee(t, resolveAssignee(m, req.assigneeId()), actor);
        return mapper.task(t);
    }

    /** Thanh vien de xuat task moi: trang thai SUGGESTED, cho nhom truong duyet. */
    @Transactional
    public TaskDto suggest(Long milestoneId, TaskSuggestionRequest req) {
        Milestone m = findMilestone(milestoneId);
        access.requireMember(m.getTeam());
        requireSubMilestone(m);
        requireActive(m);
        User actor = access.current();
        checkDueDate(req.dueDate(), m);
        Task t = new Task();
        t.setMilestone(m);
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        if (req.difficulty() != null) t.setDifficulty(req.difficulty());
        if (req.workloadHours() != null) t.setWorkloadHours(req.workloadHours());
        t.setStatus(TaskStatus.SUGGESTED);
        t.setSuggestedBy(actor);
        t.setCreatedBy(actor);
        t.setCreatedAt(LocalDateTime.now());
        tasks.save(t);
        log(t, actor, TaskAction.SUGGESTED, null, TaskStatus.SUGGESTED, "Đề xuất task mới");
        Team team = m.getTeam();
        if (!team.getLeader().getId().equals(actor.getId())) {
            notifications.notify(team.getLeader(), actor.getFullName() + " đề xuất task mới \"" + t.getTitle()
                    + "\" trong milestone \"" + m.getTitle() + "\", đang chờ bạn duyệt");
        }
        return mapper.task(t);
    }

    /** SUGGESTED -> TODO: nhom truong chot trong so, do kho, nguoi thuc hien. */
    @Transactional
    public TaskDto approveSuggestion(Long taskId, ApproveSuggestionRequest req) {
        Task t = find(taskId);
        Milestone m = t.getMilestone();
        access.requireLeaderOrAdmin(m.getTeam());
        requireActive(m);
        requireStatus(t, TaskStatus.SUGGESTED, "Task không phải là đề xuất đang chờ duyệt");
        User actor = access.current();
        if (req.dueDate() != null) {
            checkDueDate(req.dueDate(), m);
            t.setDueDate(req.dueDate());
        }
        if (req.difficulty() != null) t.setDifficulty(req.difficulty());
        if (req.weight() != null) t.setWeight(req.weight());
        if (req.workloadHours() != null) t.setWorkloadHours(req.workloadHours());
        t.setStatus(TaskStatus.TODO);
        tasks.save(t);
        log(t, actor, TaskAction.SUGGESTION_APPROVED, TaskStatus.SUGGESTED, TaskStatus.TODO,
                "Duyệt đề xuất " + describeWeights(t));
        changeAssignee(t, resolveAssignee(m, req.assigneeId()), actor);
        if (t.getSuggestedBy() != null && !t.getSuggestedBy().getId().equals(actor.getId())) {
            notifications.notify(t.getSuggestedBy(), "Đề xuất task \"" + t.getTitle() + "\" của bạn đã được duyệt");
        }
        return mapper.task(t);
    }

    /** SUGGESTED -> CANCELLED: nhom truong tu choi de xuat, bat buoc ghi ly do. */
    @Transactional
    public TaskDto rejectSuggestion(Long taskId, String note) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        requireActive(t.getMilestone());
        requireStatus(t, TaskStatus.SUGGESTED, "Task không phải là đề xuất đang chờ duyệt");
        User actor = access.current();
        t.setStatus(TaskStatus.CANCELLED);
        t.setCancelledAt(LocalDateTime.now());
        t.setCancelReason(REJECTED_SUGGESTION_PREFIX + note.trim());
        tasks.save(t);
        log(t, actor, TaskAction.SUGGESTION_REJECTED, TaskStatus.SUGGESTED, TaskStatus.CANCELLED, note.trim());
        if (t.getSuggestedBy() != null && !t.getSuggestedBy().getId().equals(actor.getId())) {
            notifications.notify(t.getSuggestedBy(), "Đề xuất task \"" + t.getTitle() + "\" bị từ chối: " + note.trim());
        }
        return mapper.task(t);
    }

    // ---------------- sua / huy / xoa ----------------
    @Transactional
    public TaskDto update(Long taskId, TaskRequest req) {
        Task t = find(taskId);
        Milestone m = t.getMilestone();
        access.requireLeaderOrAdmin(m.getTeam());
        requireActive(m);
        if (t.getStatus() == TaskStatus.DONE || t.getStatus() == TaskStatus.CANCELLED) {
            throw ApiException.conflict("Task đã hoàn thành hoặc đã hủy, không thể chỉnh sửa");
        }
        if (t.getStatus() == TaskStatus.SUGGESTED) {
            throw ApiException.conflict("Task đang là đề xuất, hãy duyệt hoặc từ chối đề xuất");
        }
        checkDueDate(req.dueDate(), m);
        User actor = access.current();
        User newAssignee = resolveAssignee(m, req.assigneeId());
        boolean assigneeChanged = !sameUser(t.getAssignee(), newAssignee);
        if (assigneeChanged && t.getStatus() != TaskStatus.TODO && t.getStatus() != TaskStatus.DOING) {
            throw ApiException.conflict("Chỉ đổi người thực hiện khi task ở trạng thái Cần làm hoặc Đang làm");
        }
        List<String> changes = new ArrayList<>();
        diff(changes, "Tên", t.getTitle(), req.title().trim());
        diff(changes, "Mô tả", t.getDescription(), req.description());
        diff(changes, "Hạn", t.getDueDate(), req.dueDate());
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        if (req.difficulty() != null) {
            diff(changes, "Độ khó", t.getDifficulty(), req.difficulty());
            t.setDifficulty(req.difficulty());
        }
        if (req.weight() != null) {
            diff(changes, "Trọng số", t.getWeight(), req.weight());
            t.setWeight(req.weight());
        }
        if (req.workloadHours() != null) {
            diff(changes, "Khối lượng (giờ)", t.getWorkloadHours(), req.workloadHours());
            t.setWorkloadHours(req.workloadHours());
        }
        tasks.save(t);
        if (!changes.isEmpty()) {
            log(t, actor, TaskAction.UPDATED, t.getStatus(), t.getStatus(), String.join("; ", changes));
        }
        if (assigneeChanged) {
            changeAssignee(t, newAssignee, actor);
        }
        return mapper.task(t);
    }

    /** Huy task (khong xoa du lieu): giu lich su, khong tinh vao tien do va diem dong gop. */
    @Transactional
    public TaskDto cancel(Long taskId, String reason) {
        Task t = find(taskId);
        User actor = access.current();
        boolean suggesterWithdraw = t.getStatus() == TaskStatus.SUGGESTED && t.getSuggestedBy() != null
                && t.getSuggestedBy().getId().equals(actor.getId());
        if (!suggesterWithdraw) {
            access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        }
        requireActive(t.getMilestone());
        if (t.getStatus() == TaskStatus.DONE) {
            throw ApiException.conflict("Task đã hoàn thành, không thể hủy");
        }
        if (t.getStatus() == TaskStatus.CANCELLED) {
            throw ApiException.conflict("Task đã bị hủy trước đó");
        }
        TaskStatus from = t.getStatus();
        User oldAssignee = t.getAssignee();
        closeOpenAssignment(t);
        t.setStatus(TaskStatus.CANCELLED);
        t.setCancelledAt(LocalDateTime.now());
        t.setCancelReason(reason.trim());
        tasks.save(t);
        // Task dang cho xac nhan: lan nop gan nhat khong con duoc xet nua
        attempts.findFirstByTaskIdAndOutcomeOrderByAttemptNoDesc(t.getId(), SubmissionOutcome.PENDING)
                .ifPresent(a -> {
                    a.setReviewNote("Task bị hủy: " + reason.trim());
                    attempts.save(a);
                });
        log(t, actor, TaskAction.CANCELLED, from, TaskStatus.CANCELLED, reason.trim());
        if (oldAssignee != null && !oldAssignee.getId().equals(actor.getId())) {
            notifications.notify(oldAssignee, "Task \"" + t.getTitle() + "\" đã bị hủy: " + reason.trim());
        }
        return mapper.task(t);
    }

    /** Chi xoa han task chua phat sinh du lieu (chua giao, chua lam). Con lai phai dung huy task. */
    @Transactional
    public void delete(Long taskId) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        requireActive(t.getMilestone());
        boolean untouched = (t.getStatus() == TaskStatus.TODO || t.getStatus() == TaskStatus.SUGGESTED)
                && t.getAssignee() == null
                && assignments.findByTaskIdOrderByAssignedAtAscIdAsc(taskId).isEmpty()
                && attempts.findByTaskIdOrderByAttemptNoAsc(taskId).isEmpty();
        if (!untouched) {
            throw ApiException.conflict("Task đã được giao hoặc đã có hoạt động, hãy dùng \"Hủy task\" để giữ lịch sử");
        }
        history.deleteByTaskId(taskId);
        tasks.delete(t);
    }

    // ---------------- luong trang thai ----------------
    /** TODO -> DOING: nguoi duoc giao (hoac nhom truong/admin) bat dau lam. */
    @Transactional
    public TaskDto start(Long taskId) {
        Task t = find(taskId);
        User u = access.current();
        Team team = t.getMilestone().getTeam();
        if (!isAssignee(t, u) && !access.isLeader(team, u) && u.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Chỉ người được giao hoặc nhóm trưởng mới bắt đầu task này");
        }
        requireActive(t.getMilestone());
        if (t.getStatus() != TaskStatus.TODO) {
            throw ApiException.conflict("Task không ở trạng thái Cần làm");
        }
        if (t.getAssignee() == null) {
            throw ApiException.conflict("Task chưa được giao cho ai");
        }
        t.setStatus(TaskStatus.DOING);
        tasks.save(t);
        log(t, u, TaskAction.STARTED, TaskStatus.TODO, TaskStatus.DOING, null);
        return mapper.task(t);
    }

    /** DOING -> REVIEW: thanh vien duoc giao bao hoan thanh. Moi lan bao deu duoc luu thanh mot ban ghi lich su. */
    @Transactional
    public TaskDto submit(Long taskId, MultipartFile file, String note) {
        Task t = find(taskId);
        User u = access.current();
        if (!isAssignee(t, u)) {
            throw ApiException.forbidden("Chỉ người được giao task mới báo hoàn thành");
        }
        requireActive(t.getMilestone());
        if (t.getStatus() != TaskStatus.DOING) {
            throw ApiException.conflict("Chỉ báo hoàn thành khi task đang ở trạng thái Đang làm");
        }
        LocalDateTime now = LocalDateTime.now();
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        TaskSubmissionHistory attempt = new TaskSubmissionHistory();
        attempt.setTask(t);
        attempt.setAttemptNo(attempts.maxAttempt(taskId) + 1);
        attempt.setSubmittedBy(u);
        attempt.setNote(cleanNote);
        attempt.setSubmittedAt(now);
        if (file != null && !file.isEmpty()) {
            FileStorage.StoredFile stored = storage.save("tasks/" + taskId, file);
            attempt.setFileName(stored.originalName());
            attempt.setStoredPath(stored.path());
            t.setResultFileName(stored.originalName());
            t.setResultStoredPath(stored.path());
        } else {
            // Lan nop nay khong dinh kem file: khong de task hien file cua lan truoc nhu ket qua moi
            t.setResultFileName(null);
            t.setResultStoredPath(null);
        }
        attempts.save(attempt);
        t.setResultNote(cleanNote);
        t.setSubmittedAt(now);
        t.setReviewNote(null);
        t.setConfirmedAt(null);
        t.setStatus(TaskStatus.REVIEW);
        tasks.save(t);
        log(t, u, TaskAction.SUBMITTED, TaskStatus.DOING, TaskStatus.REVIEW, "Báo hoàn thành lần " + attempt.getAttemptNo());
        Team team = t.getMilestone().getTeam();
        if (!team.getLeader().getId().equals(u.getId())) {
            notifications.notify(team.getLeader(), u.getFullName() + " báo hoàn thành task \"" + t.getTitle()
                    + "\" (lần " + attempt.getAttemptNo() + "), đang chờ bạn xác nhận");
        }
        return mapper.task(t);
    }

    /** REVIEW -> DONE: nhom truong xac nhan dat, co the cham diem chat luong 1..5. */
    @Transactional
    public TaskDto confirm(Long taskId, Integer qualityRating) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        requireActive(t.getMilestone());
        requireReview(t);
        User actor = access.current();
        LocalDateTime now = LocalDateTime.now();
        t.setStatus(TaskStatus.DONE);
        t.setConfirmedAt(now);
        t.setQualityRating(qualityRating);
        tasks.save(t);
        closeLatestAttempt(t, SubmissionOutcome.APPROVED, actor, now, null);
        log(t, actor, TaskAction.CONFIRMED, TaskStatus.REVIEW, TaskStatus.DONE,
                qualityRating == null ? "Xác nhận hoàn thành" : "Xác nhận hoàn thành, chất lượng " + qualityRating + "/5");
        if (t.getAssignee() != null) {
            notifications.notify(t.getAssignee(), "Task \"" + t.getTitle() + "\" đã được nhóm trưởng xác nhận hoàn thành");
        }
        return mapper.task(t);
    }

    /** REVIEW -> DOING: nhom truong yeu cau sua (bat buoc ghi noi dung); tang so lan bi yeu cau sua. */
    @Transactional
    public TaskDto reject(Long taskId, String note) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        requireActive(t.getMilestone());
        requireReview(t);
        User actor = access.current();
        t.setStatus(TaskStatus.DOING);
        t.setReviewNote(note.trim());
        t.setConfirmedAt(null);
        t.setRevisionCount(t.getRevisionCount() + 1);
        tasks.save(t);
        closeLatestAttempt(t, SubmissionOutcome.REVISION_REQUESTED, actor, LocalDateTime.now(), note.trim());
        log(t, actor, TaskAction.REVISION_REQUESTED, TaskStatus.REVIEW, TaskStatus.DOING, note.trim());
        if (t.getAssignee() != null) {
            notifications.notify(t.getAssignee(), "Nhóm trưởng yêu cầu sửa task \"" + t.getTitle() + "\": " + note.trim());
        }
        return mapper.task(t);
    }

    // ---------------- tai file ----------------
    public Task getForResult(Long taskId) {
        Task t = find(taskId);
        access.requireView(t.getMilestone().getTeam());
        if (t.getResultStoredPath() == null) {
            throw ApiException.notFound("Task này không có file kết quả");
        }
        return t;
    }

    public Resource loadResult(Task t) {
        return storage.load(t.getResultStoredPath());
    }

    /** File cua tung lan bao hoan thanh trong lich su. */
    public TaskSubmissionHistory getAttemptForDownload(Long attemptId) {
        TaskSubmissionHistory a = attempts.findById(attemptId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lần báo hoàn thành"));
        access.requireView(a.getTask().getMilestone().getTeam());
        if (a.getStoredPath() == null) {
            throw ApiException.notFound("Lần báo hoàn thành này không có file");
        }
        return a;
    }

    public Resource loadAttempt(TaskSubmissionHistory a) {
        return storage.load(a.getStoredPath());
    }

    // ---------------- helpers ----------------
    private void requireReview(Task t) {
        requireStatus(t, TaskStatus.REVIEW, "Task không ở trạng thái Chờ xác nhận");
    }

    private void requireStatus(Task t, TaskStatus status, String message) {
        if (t.getStatus() != status) {
            throw ApiException.conflict(message);
        }
    }

    private void requireSubMilestone(Milestone m) {
        if (m.getParent() == null) {
            throw ApiException.conflict("Task phải nằm trong milestone phụ. Hãy tạo milestone phụ trước.");
        }
    }

    /** Milestone (hoac milestone chinh cua no) da luu tru thi chi doc. */
    private void requireActive(Milestone m) {
        if (m.isArchived() || (m.getParent() != null && m.getParent().isArchived())) {
            throw ApiException.conflict("Milestone đã được lưu trữ, chỉ có thể xem");
        }
    }

    private boolean isAssignee(Task t, User u) {
        return t.getAssignee() != null && t.getAssignee().getId().equals(u.getId());
    }

    private static boolean sameUser(User a, User b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    private void checkDueDate(LocalDate due, Milestone milestone) {
        if (due != null && due.isAfter(milestone.getDueDate())) {
            throw ApiException.badRequest("Hạn task không được sau hạn milestone phụ (" + milestone.getDueDate() + ")");
        }
    }

    private User resolveAssignee(Milestone milestone, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return milestone.getTeam().getMembers().stream()
                .filter(member -> member.getId().equals(assigneeId))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Người được giao phải là thành viên của nhóm"));
    }

    /** Doi nguoi thuc hien: dong ban ghi phan cong cu, mo ban ghi moi (nguoi giao + thoi diem), ghi log va bao cho cac ben. */
    private void changeAssignee(Task t, User newAssignee, User actor) {
        User old = t.getAssignee();
        if (sameUser(old, newAssignee)) {
            return;
        }
        closeOpenAssignment(t);
        t.setAssignee(newAssignee);
        if (newAssignee == null) {
            t.setAssignedBy(null);
            t.setAssignedAt(null);
            if (t.getStatus() == TaskStatus.DOING) {
                t.setStatus(TaskStatus.TODO);
            }
            tasks.save(t);
            log(t, actor, TaskAction.UNASSIGNED, null, null, "Bỏ giao task của " + old.getFullName());
            if (!old.getId().equals(actor.getId())) {
                notifications.notify(old, "Bạn không còn được giao task \"" + t.getTitle() + "\"");
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        t.setAssignedBy(actor);
        t.setAssignedAt(now);
        tasks.save(t);
        TaskAssignmentHistory a = new TaskAssignmentHistory();
        a.setTask(t);
        a.setAssignee(newAssignee);
        a.setAssignedBy(actor);
        a.setAssignedAt(now);
        assignments.save(a);
        log(t, actor, TaskAction.ASSIGNED, null, null,
                "Giao cho " + newAssignee.getFullName() + (old != null ? " (trước đó: " + old.getFullName() + ")" : ""));
        if (!newAssignee.getId().equals(actor.getId())) {
            notifications.notify(newAssignee, "Bạn được giao task: \"" + t.getTitle() + "\"");
        }
        if (old != null && !old.getId().equals(actor.getId())) {
            notifications.notify(old, "Task \"" + t.getTitle() + "\" đã được giao cho " + newAssignee.getFullName());
        }
    }

    private void closeOpenAssignment(Task t) {
        assignments.findFirstByTaskIdAndEndedAtIsNullOrderByIdDesc(t.getId()).ifPresent(a -> {
            a.setEndedAt(LocalDateTime.now());
            assignments.save(a);
        });
    }

    private void closeLatestAttempt(Task t, SubmissionOutcome outcome, User reviewer, LocalDateTime at, String note) {
        attempts.findFirstByTaskIdAndOutcomeOrderByAttemptNoDesc(t.getId(), SubmissionOutcome.PENDING).ifPresent(a -> {
            a.setOutcome(outcome);
            a.setReviewedBy(reviewer);
            a.setReviewedAt(at);
            a.setReviewNote(note);
            attempts.save(a);
        });
    }

    private void log(Task t, User actor, TaskAction action, TaskStatus from, TaskStatus to, String detail) {
        TaskHistory h = new TaskHistory();
        h.setTask(t);
        h.setActor(actor);
        h.setAction(action);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setDetail(detail == null || detail.length() <= 1500 ? detail : detail.substring(0, 1500));
        h.setCreatedAt(LocalDateTime.now());
        history.save(h);
    }

    private static String describeWeights(Task t) {
        return "(độ khó " + t.getDifficulty() + ", trọng số " + t.getWeight() + ", khối lượng " + t.getWorkloadHours() + "h)";
    }

    private static void diff(List<String> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(field + ": " + (before == null ? "—" : before) + " → " + (after == null ? "—" : after));
        }
    }

    private Milestone findMilestone(Long id) {
        return milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }

    private Task find(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy task"));
    }
}
