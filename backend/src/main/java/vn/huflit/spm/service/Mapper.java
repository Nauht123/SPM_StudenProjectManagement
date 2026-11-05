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

    public Mapper(RegistrationRepository registrations, TaskRepository tasks, SubmissionRepository submissions,
                  MilestoneRepository milestones) {
        this.registrations = registrations;
        this.tasks = tasks;
        this.submissions = submissions;
        this.milestones = milestones;
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

    /** Milestone chinh: so lieu tong hop tu cac milestone phu; milestone phu: so lieu cua chinh no. */
    public MilestoneDto milestone(Milestone m) {
        boolean main = m.getParent() == null;
        long subMilestones = 0;
        long total;
        long done;
        long review;
        if (main) {
            subMilestones = milestones.countByParentId(m.getId());
            total = tasks.countByMilestoneParentId(m.getId());
            done = tasks.countByMilestoneParentIdAndStatus(m.getId(), TaskStatus.DONE);
            review = tasks.countByMilestoneParentIdAndStatus(m.getId(), TaskStatus.REVIEW);
        } else {
            total = tasks.countByMilestoneId(m.getId());
            done = tasks.countByMilestoneIdAndStatus(m.getId(), TaskStatus.DONE);
            review = tasks.countByMilestoneIdAndStatus(m.getId(), TaskStatus.REVIEW);
        }
        long subs = submissions.countByMilestoneId(m.getId());
        boolean past = m.getDueDate().isBefore(LocalDate.now());
        boolean overdue = main ? past && subs == 0 : past && total > 0 && done < total;
        return new MilestoneDto(m.getId(), m.getTeam().getId(), main ? null : m.getParent().getId(), m.getTitle(),
                m.getDescription(), m.getDueDate(), subMilestones, total, done, review, subs, overdue);
    }

    public TaskDto task(Task t) {
        return new TaskDto(t.getId(), t.getMilestone().getId(), t.getTitle(), t.getDescription(),
                user(t.getAssignee()), t.getStatus(), t.getDueDate(), t.getResultNote(), t.getResultFileName(),
                t.getSubmittedAt(), t.getReviewNote(), t.getConfirmedAt());
    }

    public SubmissionDto submission(Submission s) {
        return new SubmissionDto(s.getId(), s.getMilestone().getId(), s.getVersion(), s.getFileName(), s.getNote(),
                user(s.getSubmittedBy()), s.getSubmittedAt(), s.isLate());
    }

    public FeedbackDto feedback(Feedback f) {
        return new FeedbackDto(f.getId(), f.getSubmission().getId(), user(f.getLecturer()), f.getComment(),
                f.getCreatedAt());
    }

    public NotificationDto notification(Notification n) {
        return new NotificationDto(n.getId(), n.getMessage(), n.isSeen(), n.getCreatedAt());
    }
}
