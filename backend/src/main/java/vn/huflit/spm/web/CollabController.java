package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.ActivityService;
import vn.huflit.spm.service.TaskCommentService;

import java.time.LocalDate;
import java.util.List;

/** Binh luan task, nhat ky hoat dong, lich su thanh vien va lich tong hop cua nhom. */
@RestController
@RequestMapping("/api")
public class CollabController {
    private final TaskCommentService comments;
    private final ActivityService activity;

    public CollabController(TaskCommentService comments, ActivityService activity) {
        this.comments = comments;
        this.activity = activity;
    }

    @GetMapping("/tasks/{id}/comments")
    public List<CommentDto> comments(@PathVariable Long id) {
        return comments.list(id);
    }

    @PostMapping("/tasks/{id}/comments")
    public CommentDto addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return comments.add(id, req);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        comments.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teams/{id}/activity")
    public List<ActivityDto> activity(@PathVariable Long id) {
        return activity.feed(id);
    }

    @GetMapping("/teams/{id}/member-history")
    public List<MemberHistoryDto> memberHistory(@PathVariable Long id) {
        return activity.memberHistory(id);
    }

    @GetMapping("/teams/{id}/calendar")
    public List<CalendarEventDto> calendar(@PathVariable Long id,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return activity.calendar(id, from, to);
    }
}
