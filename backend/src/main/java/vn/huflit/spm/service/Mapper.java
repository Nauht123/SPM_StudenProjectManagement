package vn.huflit.spm.service;

import org.springframework.stereotype.Component;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Chuyen entity sang DTO (gom cac phep dem can thiet cho hien thi). */
@Component
public class Mapper {
    private final RegistrationRepository registrations;
    private final TaskRepository tasks;
    private final SubmissionRepository submissions;
    private final MilestoneRepository milestones;
    private final SubmissionContributorRepository contributors;

    public Mapper(RegistrationRepository registrations, TaskRepository tasks, SubmissionRepository submissions,
                  MilestoneRepository milestones, SubmissionContributorRepository contributors) {
        this.registrations = registrations;
        this.tasks = tasks;
        this.submissions = submissions;
        this.milestones = milestones;
        this.contributors = contributors;
    }

    /** Phan tram co xet trong so: tong trong so task xong / tong trong so task dang hieu luc. */
    public static int weightedPercent(long doneWeight, long totalWeight) {
        return totalWeight <= 0 ? 0 : (int) Math.round(doneWeight * 100.0 / totalWeight);
    }

    public UserDto user(User u) {
        if (u == null) {
            return null;
        }
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getStudentCode(), u.getRole());
    }

    public TopicDto topic(Topic t) {
        long approved = registrations.countByTopicIdAndStatus(t.getId(), RegStatus.APPROVED);
        return new TopicDto(t.getId(), t.getTitle(), t.getDescription(), t.getMaxGroups(), approved,
                t.getSemester(), t.getStatus(), user(t.getLecturer()));
    }

    public RegistrationDto registration(Registration r) {
        return new RegistrationDto(r.getId(), r.getTeam().getId(), r.getTeam().getName(), topic(r.getTopic()),
                r.getStatus(), r.getNote(), r.getCreatedAt());
    }

    public TeamDto team(Team t) {
        List<UserDto> members = t.getMembers().stream()
                .sorted(Comparator.comparing(User::getId))
                .map(this::user)
                .toList();
        RegistrationDto reg = registrations.findFirstByTeamIdOrderByIdDesc(t.getId())
                .map(this::registration)
                .orElse(null);
        return new TeamDto(t.getId(), t.getName(), user(t.getLeader()), members, reg);
    }

    /** Milestone chinh: so lieu tong hop tu cac milestone phu (khong tinh milestone phu da luu tru); milestone phu: so lieu cua chinh no. */
    public MilestoneDto milestone(Milestone m) {
        boolean main = m.getParent() == null;
        long subMilestones = 0;
        long total;
        long done;
        long review;
        long totalWeight;
        long doneWeight;
        List<TaskStatus> doneOnly = List.of(TaskStatus.DONE);
        List<TaskStatus> reviewOnly = List.of(TaskStatus.REVIEW);
        if (main) {
            subMilestones = milestones.countByParentIdAndArchivedFalse(m.getId());
            total = tasks.countUnderMain(m.getId(), TaskStatus.ACTIVE);
            done = tasks.countUnderMain(m.getId(), doneOnly);
            review = tasks.countUnderMain(m.getId(), reviewOnly);
            totalWeight = tasks.weightUnderMain(m.getId(), TaskStatus.ACTIVE);
            doneWeight = tasks.weightUnderMain(m.getId(), doneOnly);
        } else {
            total = tasks.countInMilestone(m.getId(), TaskStatus.ACTIVE);
            done = tasks.countInMilestone(m.getId(), doneOnly);
            review = tasks.countInMilestone(m.getId(), reviewOnly);
            totalWeight = tasks.weightInMilestone(m.getId(), TaskStatus.ACTIVE);
            doneWeight = tasks.weightInMilestone(m.getId(), doneOnly);
        }
        long subs = submissions.countByMilestoneId(m.getId());
        boolean past = m.getDueDate().isBefore(LocalDate.now());
        boolean overdue = !m.isArchived() && (main ? past && subs == 0 : past && total > 0 && done < total);
        return new MilestoneDto(m.getId(), m.getTeam().getId(), main ? null : m.getParent().getId(), m.getTitle(),
                m.getDescription(), m.getDueDate(), subMilestones, total, done, review, subs, overdue,
                weightedPercent(doneWeight, totalWeight), m.isArchived(), m.getArchivedAt());
    }

    public TaskDto task(Task t) {
        return new TaskDto(t.getId(), t.getMilestone().getId(), t.getTitle(), t.getDescription(),
                user(t.getAssignee()), t.getStatus(), t.getDueDate(), t.getResultNote(), t.getResultFileName(),
                t.getSubmittedAt(), t.getReviewNote(), t.getConfirmedAt(),
                t.getDifficulty(), t.getWeight(), t.getWorkloadHours(), t.getRevisionCount(), t.getQualityRating(),
                user(t.getAssignedBy()), t.getAssignedAt(), user(t.getSuggestedBy()), user(t.getCreatedBy()),
                t.getCreatedAt(), t.getCancelledAt(), t.getCancelReason());
    }

    public TaskHistoryDto taskHistory(TaskHistory h) {
        return new TaskHistoryDto(h.getId(), user(h.getActor()), h.getAction(), h.getFromStatus(), h.getToStatus(),
                h.getDetail(), h.getCreatedAt());
    }

    public TaskAssignmentDto taskAssignment(TaskAssignmentHistory a) {
        return new TaskAssignmentDto(a.getId(), user(a.getAssignee()), user(a.getAssignedBy()), a.getAssignedAt(),
                a.getEndedAt());
    }

    public TaskSubmissionHistoryDto taskSubmission(TaskSubmissionHistory h) {
        return new TaskSubmissionHistoryDto(h.getId(), h.getAttemptNo(), user(h.getSubmittedBy()), h.getNote(),
                h.getFileName(), h.getSubmittedAt(), h.getOutcome(), user(h.getReviewedBy()), h.getReviewedAt(),
                h.getReviewNote());
    }

    public SubmissionDto submission(Submission s) {
        List<UserDto> contribs = contributors.findBySubmissionId(s.getId()).stream()
                .map(c -> user(c.getUser()))
                .sorted(Comparator.comparing(UserDto::id))
                .toList();
        return new SubmissionDto(s.getId(), s.getMilestone().getId(), s.getVersion(), s.getFileName(), s.getNote(),
                user(s.getSubmittedBy()), s.getSubmittedAt(), s.isLate(), contribs);
    }

    public FeedbackDto feedback(Feedback f) {
        return new FeedbackDto(f.getId(), f.getSubmission().getId(), user(f.getLecturer()), f.getComment(),
                f.getCreatedAt());
    }

    public NotificationDto notification(Notification n) {
        return new NotificationDto(n.getId(), n.getMessage(), n.isSeen(), n.getCreatedAt());
    }
}
