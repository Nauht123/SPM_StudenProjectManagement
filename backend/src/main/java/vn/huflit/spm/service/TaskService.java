package vn.huflit.spm.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.TaskDto;
import vn.huflit.spm.dto.Dtos.TaskRequest;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.TaskRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Task nam trong milestone phu. Phan quyen:
 * - Them/sua/xoa task: chi nhom truong (hoac admin). Giang vien khong duoc them task.
 * - Thanh vien duoc giao: bat dau lam, bao hoan thanh (kem file/ghi chu tuy chon).
 * - Nhom truong: xac nhan (-> DONE) hoac yeu cau sua (-> DOING).
 */
@Service
public class TaskService {
    private final TaskRepository tasks;
    private final MilestoneRepository milestones;
    private final AccessService access;
    private final NotificationService notifications;
    private final FileStorage storage;
    private final Mapper mapper;

    public TaskService(TaskRepository tasks, MilestoneRepository milestones, AccessService access,
                       NotificationService notifications, FileStorage storage, Mapper mapper) {
        this.tasks = tasks;
        this.milestones = milestones;
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

    @Transactional
    public TaskDto create(Long milestoneId, TaskRequest req) {
        Milestone m = findMilestone(milestoneId);
        access.requireLeaderOrAdmin(m.getTeam());
        if (m.getParent() == null) {
            throw ApiException.conflict("Task phải nằm trong milestone phụ. Hãy tạo milestone phụ trước.");
        }
        Task t = new Task();
        t.setMilestone(m);
        apply(t, req, m);
        tasks.save(t);
        notifyAssigned(t);
        return mapper.task(t);
    }

    @Transactional
    public TaskDto update(Long taskId, TaskRequest req) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        User before = t.getAssignee();
        apply(t, req, t.getMilestone());
        tasks.save(t);
        if (t.getAssignee() != null && (before == null || !before.getId().equals(t.getAssignee().getId()))) {
            notifyAssigned(t);
        }
        return mapper.task(t);
    }

    @Transactional
    public void delete(Long taskId) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        tasks.delete(t);
    }

    /** TODO -> DOING: nguoi duoc giao (hoac nhom truong/admin) bat dau lam. */
    @Transactional
    public TaskDto start(Long taskId) {
        Task t = find(taskId);
        User u = access.current();
        Team team = t.getMilestone().getTeam();
        if (!isAssignee(t, u) && !access.isLeader(team, u) && u.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Chỉ người được giao hoặc nhóm trưởng mới bắt đầu task này");
        }
        if (t.getStatus() != TaskStatus.TODO) {
            throw ApiException.conflict("Task không ở trạng thái Cần làm");
        }
        t.setStatus(TaskStatus.DOING);
        return mapper.task(tasks.save(t));
    }

    /** DOING -> REVIEW: thanh vien duoc giao bao hoan thanh, ghi lai thoi diem nop cuoi cung. */
    @Transactional
    public TaskDto submit(Long taskId, MultipartFile file, String note) {
        Task t = find(taskId);
        User u = access.current();
        if (!isAssignee(t, u)) {
            throw ApiException.forbidden("Chỉ người được giao task mới báo hoàn thành");
        }
        if (t.getStatus() != TaskStatus.DOING) {
            throw ApiException.conflict("Chỉ báo hoàn thành khi task đang ở trạng thái Đang làm");
        }
        if (file != null && !file.isEmpty()) {
            FileStorage.StoredFile stored = storage.save("tasks/" + taskId, file);
            t.setResultFileName(stored.originalName());
            t.setResultStoredPath(stored.path());
        }
        t.setResultNote(note == null || note.isBlank() ? null : note.trim());
        t.setSubmittedAt(LocalDateTime.now());
        t.setReviewNote(null);
        t.setConfirmedAt(null);
        t.setStatus(TaskStatus.REVIEW);
        tasks.save(t);
        Team team = t.getMilestone().getTeam();
        if (!team.getLeader().getId().equals(u.getId())) {
            notifications.notify(team.getLeader(), u.getFullName() + " báo hoàn thành task \"" + t.getTitle()
                    + "\", đang chờ bạn xác nhận");
        }
        return mapper.task(t);
    }

    /** REVIEW -> DONE: nhom truong xac nhan dat. */
    @Transactional
    public TaskDto confirm(Long taskId) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        requireReview(t);
        t.setStatus(TaskStatus.DONE);
        t.setConfirmedAt(LocalDateTime.now());
        tasks.save(t);
        if (t.getAssignee() != null) {
            notifications.notify(t.getAssignee(), "Task \"" + t.getTitle() + "\" đã được nhóm trưởng xác nhận hoàn thành");
        }
        return mapper.task(t);
    }

    /** REVIEW -> DOING: nhom truong yeu cau sua (bat buoc ghi noi dung). */
    @Transactional
    public TaskDto reject(Long taskId, String note) {
        Task t = find(taskId);
        access.requireLeaderOrAdmin(t.getMilestone().getTeam());
        requireReview(t);
        t.setStatus(TaskStatus.DOING);
        t.setReviewNote(note.trim());
        t.setConfirmedAt(null);
        tasks.save(t);
        if (t.getAssignee() != null) {
            notifications.notify(t.getAssignee(), "Nhóm trưởng yêu cầu sửa task \"" + t.getTitle() + "\": " + note.trim());
        }
        return mapper.task(t);
    }

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

    // ---------------- helpers ----------------
    private void requireReview(Task t) {
        if (t.getStatus() != TaskStatus.REVIEW) {
            throw ApiException.conflict("Task không ở trạng thái Chờ xác nhận");
        }
    }

    private boolean isAssignee(Task t, User u) {
        return t.getAssignee() != null && t.getAssignee().getId().equals(u.getId());
    }

    private void apply(Task t, TaskRequest req, Milestone milestone) {
        if (req.dueDate() != null && req.dueDate().isAfter(milestone.getDueDate())) {
            throw ApiException.badRequest("Hạn task không được sau hạn milestone phụ (" + milestone.getDueDate() + ")");
        }
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        if (req.assigneeId() == null) {
            t.setAssignee(null);
        } else {
            User assignee = milestone.getTeam().getMembers().stream()
                    .filter(member -> member.getId().equals(req.assigneeId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Người được giao phải là thành viên của nhóm"));
            t.setAssignee(assignee);
        }
    }

    private void notifyAssigned(Task t) {
        User actor = access.current();
        if (t.getAssignee() != null && !t.getAssignee().getId().equals(actor.getId())) {
            notifications.notify(t.getAssignee(), "Bạn được giao task: \"" + t.getTitle() + "\"");
        }
    }

    private Milestone findMilestone(Long id) {
        return milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }

    private Task find(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy task"));
    }
}
