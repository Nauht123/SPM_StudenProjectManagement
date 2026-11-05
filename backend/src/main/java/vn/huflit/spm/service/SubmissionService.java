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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Nop bai theo phien ban, tai file, phan hoi cua giang vien. */
@Service
public class SubmissionService {
    private final SubmissionRepository submissions;
    private final FeedbackRepository feedbacks;
    private final SubmissionContributorRepository contributors;
    private final MilestoneRepository milestones;
    private final RegistrationRepository registrations;
    private final AccessService access;
    private final NotificationService notifications;
    private final Mapper mapper;
    private final FileStorage storage;

    public SubmissionService(SubmissionRepository submissions, FeedbackRepository feedbacks,
                             SubmissionContributorRepository contributors, MilestoneRepository milestones, RegistrationRepository registrations,
                             AccessService access, NotificationService notifications, Mapper mapper,
                             FileStorage storage) {
        this.submissions = submissions;
        this.feedbacks = feedbacks;
        this.contributors = contributors;
        this.milestones = milestones;
        this.registrations = registrations;
        this.access = access;
        this.notifications = notifications;
        this.mapper = mapper;
        this.storage = storage;
    }

    /** Bai nop tinh la tre neu thoi diem nop sau het ngay han (24:00 cua dueDate). */
    public static boolean isLate(LocalDate dueDate, LocalDateTime submittedAt) {
        return submittedAt.isAfter(dueDate.plusDays(1).atStartOfDay());
    }

    /**
     * contributorIds: nhung thanh vien thuc su dong gop noi dung bai nop (khac nguoi upload).
     * De trong = chi nguoi upload duoc ghi nhan la nguoi dong gop.
     */
    @Transactional
    public SubmissionDto submit(Long milestoneId, MultipartFile file, String note, List<Long> contributorIds) {
        Milestone m = findMilestone(milestoneId);
        access.requireMember(m.getTeam());
        if (m.getParent() != null) {
            throw ApiException.badRequest("Chỉ nộp bài cho milestone chính");
        }
        if (m.isArchived()) {
            throw ApiException.conflict("Milestone đã được lưu trữ, không thể nộp bài");
        }
        User uploader = access.current();
        Set<Long> contributorSet = new LinkedHashSet<>();
        contributorSet.add(uploader.getId());
        if (contributorIds != null) {
            contributorSet.addAll(contributorIds);
        }
        List<User> contributorUsers = new ArrayList<>();
        for (Long uid : contributorSet) {
            contributorUsers.add(m.getTeam().getMembers().stream()
                    .filter(member -> member.getId().equals(uid))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Người đóng góp phải là thành viên của nhóm")));
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn file để nộp");
        }
        FileStorage.StoredFile stored = storage.save("submissions/" + milestoneId, file);
        LocalDateTime now = LocalDateTime.now();
        Submission s = new Submission();
        s.setMilestone(m);
        s.setSubmittedBy(uploader);
        s.setVersion(submissions.maxVersion(milestoneId) + 1);
        s.setFileName(stored.originalName());
        s.setStoredPath(stored.path());
        s.setNote(note);
        s.setSubmittedAt(now);
        s.setLate(isLate(m.getDueDate(), now));
        submissions.save(s);
        for (User c : contributorUsers) {
            SubmissionContributor sc = new SubmissionContributor();
            sc.setSubmission(s);
            sc.setUser(c);
            contributors.save(sc);
        }

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
        return storage.load(s.getStoredPath());
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
    private Milestone findMilestone(Long id) {
        return milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }

    private Submission findSubmission(Long id) {
        return submissions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy bài nộp"));
    }
}
