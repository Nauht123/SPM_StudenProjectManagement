package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.CommentDto;
import vn.huflit.spm.dto.Dtos.CommentRequest;
import vn.huflit.spm.repo.TaskCommentRepository;
import vn.huflit.spm.repo.TaskRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binh luan trong task. Moi nguoi xem duoc nhom (thanh vien, giang vien huong dan, admin) deu doc va viet duoc;
 * chi tac gia hoac admin xoa duoc. Thong bao cho nguoi dang lam task va nhom truong.
 */
@Service
public class TaskCommentService {
    private final TaskCommentRepository comments;
    private final TaskRepository tasks;
    private final NotificationService notifications;
    private final AccessService access;
    private final Mapper mapper;

    public TaskCommentService(TaskCommentRepository comments, TaskRepository tasks,
                              NotificationService notifications, AccessService access, Mapper mapper) {
        this.comments = comments;
        this.tasks = tasks;
        this.notifications = notifications;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CommentDto> list(Long taskId) {
        Task t = task(taskId);
        access.requireView(t.getMilestone().getTeam());
        User me = access.current();
        return comments.findByTaskIdOrderByCreatedAtAscIdAsc(taskId).stream().map(c -> dto(c, me)).toList();
    }

    @Transactional
    public CommentDto add(Long taskId, CommentRequest req) {
        Task t = task(taskId);
        Team team = t.getMilestone().getTeam();
        access.requireView(team);
        User me = access.current();
        String content = req.content() == null ? "" : req.content().strip();
        if (content.isEmpty()) {
            throw ApiException.badRequest("Nội dung bình luận không được để trống");
        }
        TaskComment c = new TaskComment();
        c.setTask(t);
        c.setAuthor(me);
        c.setContent(content);
        comments.save(c);

        Map<Long, User> recipients = new LinkedHashMap<>();
        if (t.getAssignee() != null) {
            recipients.put(t.getAssignee().getId(), t.getAssignee());
        }
        recipients.put(team.getLeader().getId(), team.getLeader());
        recipients.remove(me.getId());
        String msg = me.getFullName() + " đã bình luận ở task \"" + t.getTitle() + "\"";
        recipients.values().forEach(u -> notifications.notify(u, msg));
        return dto(c, me);
    }

    @Transactional
    public void delete(Long commentId) {
        TaskComment c = comments.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bình luận"));
        User me = access.current();
        if (!c.getAuthor().getId().equals(me.getId()) && me.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Chỉ tác giả hoặc quản trị viên mới xóa được bình luận");
        }
        comments.delete(c);
    }

    private CommentDto dto(TaskComment c, User me) {
        boolean canDelete = c.getAuthor().getId().equals(me.getId()) || me.getRole() == Role.ADMIN;
        return new CommentDto(c.getId(), c.getTask().getId(), mapper.user(c.getAuthor()), c.getContent(),
                c.getCreatedAt(), canDelete);
    }

    private Task task(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy task"));
    }
}
