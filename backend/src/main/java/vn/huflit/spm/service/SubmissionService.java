package vn.huflit.spm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Nop bai theo phien ban, tai file, phan hoi cua giang vien. */
@Service
public class SubmissionService {
    private final SubmissionRepository submissions;
    private final FeedbackRepository feedbacks;
    private final MilestoneRepository milestones;
    private final RegistrationRepository registrations;
    private final AccessService access;
    private final NotificationService notifications;
    private final Mapper mapper;
    private final Path uploadRoot;

    public SubmissionService(SubmissionRepository submissions, FeedbackRepository feedbacks,
                             MilestoneRepository milestones, RegistrationRepository registrations,
                             AccessService access, NotificationService notifications, Mapper mapper,
                             @Value("${app.upload-dir}") String uploadDir) {
        this.submissions = submissions;
        this.feedbacks = feedbacks;
        this.milestones = milestones;
        this.registrations = registrations;
        this.access = access;
        this.notifications = notifications;
        this.mapper = mapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Bai nop tinh la tre neu thoi diem nop sau het ngay han (24:00 cua dueDate). */
    public static boolean isLate(LocalDate dueDate, LocalDateTime submittedAt) {
        return submittedAt.isAfter(dueDate.plusDays(1).atStartOfDay());
    }

    @Transactional
    public SubmissionDto submit(Long milestoneId, MultipartFile file, String note) {
        Milestone m = findMilestone(milestoneId);
        access.requireMember(m.getTeam());
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn file để nộp");
        }
        String original = sanitize(file.getOriginalFilename());
        Path dir = uploadRoot.resolve(String.valueOf(milestoneId));
        Path target = dir.resolve(UUID.randomUUID() + "_" + original).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw ApiException.badRequest("Tên file không hợp lệ");
        }
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được file");
        }
        LocalDateTime now = LocalDateTime.now();
        Submission s = new Submission();
        s.setMilestone(m);
        s.setSubmittedBy(access.current());
        s.setVersion(submissions.maxVersion(milestoneId) + 1);
        s.setFileName(original);
        s.setStoredPath(uploadRoot.relativize(target).toString());
        s.setNote(note);
        s.setSubmittedAt(now);
        s.setLate(isLate(m.getDueDate(), now));
        submissions.save(s);

        registrations.findFirstByTeamIdAndStatus(m.getTeam().getId(), RegStatus.APPROVED).ifPresent(r ->
                notifications.notify(r.getTopic().getLecturer(), "Nhóm \"" + m.getTeam().getName()
                        + "\" nộp bài v" + s.getVersion() + " cho milestone \"" + m.getTitle() + "\""
                        + (s.isLate() ? " (TRỄ HẠN)" : "")));
        return mapper.submission(s);
    }

    public List<SubmissionDto> list(Long milestoneId) {
        Milestone m = findMilestone(milestoneId);
        access.requireView(m.getTeam());
        return submissions.findByMilestoneIdOrderByVersionDesc(milestoneId).stream().map(mapper::submission).toList();
    }

    public Submission getForDownload(Long id) {
        Submission s = findSubmission(id);
        access.requireView(s.getMilestone().getTeam());
        return s;
    }

    public Resource load(Submission s) {
        Path p = uploadRoot.resolve(s.getStoredPath()).normalize();
        if (!p.startsWith(uploadRoot) || !Files.exists(p)) {
            throw ApiException.notFound("Không tìm thấy file trên máy chủ");
        }
        return new FileSystemResource(p);
    }

    // ---------------- Feedback ----------------
    @Transactional
    public FeedbackDto addFeedback(Long submissionId, FeedbackRequest req) {
        Submission s = findSubmission(submissionId);
        Team team = s.getMilestone().getTeam();
        access.requireSupervisor(team);
        Feedback f = new Feedback();
        f.setSubmission(s);
        f.setLecturer(access.current());
        f.setComment(req.comment().trim());
        feedbacks.save(f);
        notifications.notifyTeam(team, "Giảng viên đã phản hồi bài nộp v" + s.getVersion()
                + " của milestone \"" + s.getMilestone().getTitle() + "\"", null);
        return mapper.feedback(f);
    }

    public List<FeedbackDto> listFeedback(Long submissionId) {
        Submission s = findSubmission(submissionId);
        access.requireView(s.getMilestone().getTeam());
        return feedbacks.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream().map(mapper::feedback).toList();
    }

    // ---------------- helpers ----------------
    private String sanitize(String name) {
        String base = name == null ? "file" : Paths.get(name).getFileName().toString();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return cleaned.isBlank() ? "file" : cleaned;
    }

    private Milestone findMilestone(Long id) {
        return milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }

    private Submission findSubmission(Long id) {
        return submissions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy bài nộp"));
    }
}
