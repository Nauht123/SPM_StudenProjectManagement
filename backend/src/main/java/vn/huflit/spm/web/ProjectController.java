package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.huflit.spm.domain.Submission;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.MilestoneService;
import vn.huflit.spm.service.NotificationService;
import vn.huflit.spm.service.SubmissionService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProjectController {
    private final MilestoneService milestones;
    private final SubmissionService submissions;
    private final NotificationService notifications;

    public ProjectController(MilestoneService milestones, SubmissionService submissions,
                             NotificationService notifications) {
        this.milestones = milestones;
        this.submissions = submissions;
        this.notifications = notifications;
    }

    // ----- milestone -----
    @GetMapping("/teams/{teamId}/milestones")
    public List<MilestoneDto> listMilestones(@PathVariable Long teamId) {
        return milestones.list(teamId);
    }

    @PostMapping("/teams/{teamId}/milestones")
    public MilestoneDto createMilestone(@PathVariable Long teamId, @Valid @RequestBody MilestoneRequest req) {
        return milestones.create(teamId, req);
    }

    @GetMapping("/milestones/{id}")
    public MilestoneDto getMilestone(@PathVariable Long id) {
        return milestones.get(id);
    }

    @PutMapping("/milestones/{id}")
    public MilestoneDto updateMilestone(@PathVariable Long id, @Valid @RequestBody MilestoneRequest req) {
        return milestones.update(id, req);
    }

    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<Void> deleteMilestone(@PathVariable Long id) {
        milestones.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ----- task -----
    @GetMapping("/milestones/{id}/tasks")
    public List<TaskDto> listTasks(@PathVariable Long id) {
        return milestones.listTasks(id);
    }

    @PostMapping("/milestones/{id}/tasks")
    public TaskDto createTask(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return milestones.createTask(id, req);
    }

    @PutMapping("/tasks/{id}")
    public TaskDto updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return milestones.updateTask(id, req);
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return milestones.changeStatus(id, req);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        milestones.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    // ----- submission -----
    @GetMapping("/milestones/{id}/submissions")
    public List<SubmissionDto> listSubmissions(@PathVariable Long id) {
        return submissions.list(id);
    }

    @PostMapping(value = "/milestones/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public SubmissionDto submit(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                @RequestParam(value = "note", required = false) String note) {
        return submissions.submit(id, file, note);
    }

    @GetMapping("/submissions/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Submission s = submissions.getForDownload(id);
        Resource resource = submissions.load(s);
        ContentDisposition cd = ContentDisposition.attachment().filename(s.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ----- feedback -----
    @GetMapping("/submissions/{id}/feedback")
    public List<FeedbackDto> listFeedback(@PathVariable Long id) {
        return submissions.listFeedback(id);
    }

    @PostMapping("/submissions/{id}/feedback")
    @PreAuthorize("hasRole('LECTURER')")
    public FeedbackDto addFeedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest req) {
        return submissions.addFeedback(id, req);
    }

    // ----- notification -----
    @GetMapping("/notifications")
    public List<NotificationDto> notifications() {
        return notifications.mine();
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", notifications.unreadCount());
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        notifications.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> readAll() {
        notifications.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
