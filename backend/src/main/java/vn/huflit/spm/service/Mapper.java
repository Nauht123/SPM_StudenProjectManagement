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

    public Mapper(RegistrationRepository registrations, TaskRepository tasks, SubmissionRepository submissions) {
        this.registrations = registrations;
        this.tasks = tasks;
        this.submissions = submissions;
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

    public MilestoneDto milestone(Milestone m) {
        long total = tasks.countByMilestoneId(m.getId());
        long done = tasks.countByMilestoneIdAndStatus(m.getId(), TaskStatus.DONE);
        long subs = submissions.countByMilestoneId(m.getId());
        boolean overdue = m.getDueDate().isBefore(LocalDate.now()) && subs == 0;
        return new MilestoneDto(m.getId(), m.getTeam().getId(), m.getTitle(), m.getDescription(), m.getDueDate(),
                total, done, subs, overdue);
    }

    public TaskDto task(Task t) {
        return new TaskDto(t.getId(), t.getMilestone().getId(), t.getTitle(), t.getDescription(),
                user(t.getAssignee()), t.getStatus(), t.getDueDate());
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
