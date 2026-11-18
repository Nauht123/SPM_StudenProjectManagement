package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Nhat ky hoat dong cua nhom, lich su thay doi thanh vien va lich tong hop.
 * Nhat ky la mot "read model" gop tu cac bang lich su san co (task, binh luan, milestone, thanh vien, GitHub)
 * nen khong can bang rieng va khong the lech so voi du lieu goc.
 */
@Service
public class ActivityService {
    static final int MAX = 60;

    private final TaskHistoryRepository taskHistory;
    private final TaskCommentRepository comments;
    private final MilestoneRepository milestones;
    private final TeamMemberHistoryRepository memberHistory;
    private final GithubPullRequestRepository pulls;
    private final TaskRepository tasks;
    private final SubmissionRepository submissions;
    private final FeedbackRepository feedbacks;
    private final RegistrationRepository registrations;
    private final TeamChangeRequestRepository changeRequests;
    private final TeamService teamService;
    private final AccessService access;
    private final Mapper mapper;

    public ActivityService(TaskHistoryRepository taskHistory, TaskCommentRepository comments,
                           MilestoneRepository milestones, TeamMemberHistoryRepository memberHistory,
                           GithubPullRequestRepository pulls, TaskRepository tasks,
                           SubmissionRepository submissions, FeedbackRepository feedbacks,
                           RegistrationRepository registrations, TeamChangeRequestRepository changeRequests,
                           TeamService teamService, AccessService access, Mapper mapper) {
        this.taskHistory = taskHistory;
        this.comments = comments;
        this.milestones = milestones;
        this.memberHistory = memberHistory;
        this.pulls = pulls;
        this.tasks = tasks;
        this.submissions = submissions;
        this.feedbacks = feedbacks;
        this.registrations = registrations;
        this.changeRequests = changeRequests;
        this.teamService = teamService;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> feed(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        List<ActivityDto> all = new ArrayList<>();

        for (TaskHistory h : taskHistory.findTop50ByTaskMilestoneTeamIdOrderByCreatedAtDesc(teamId)) {
            Task t = h.getTask();
            all.add(new ActivityDto("TASK", describe(h.getAction(), actorName(h.getActor()), t.getTitle()),
                    user(h.getActor()), h.getCreatedAt(), t.getId(), t.getMilestone().getId()));
        }
        for (TaskComment c : comments.findTop50ByTaskMilestoneTeamIdOrderByCreatedAtDesc(teamId)) {
            all.add(new ActivityDto("COMMENT", c.getAuthor().getFullName() + " bình luận ở task \""
                    + c.getTask().getTitle() + "\": " + shorten(c.getContent()), user(c.getAuthor()),
                    c.getCreatedAt(), c.getTask().getId(), c.getTask().getMilestone().getId()));
        }
        for (Milestone m : milestones.findTop20ByTeamIdOrderByCreatedAtDesc(teamId)) {
            all.add(new ActivityDto("MILESTONE", "Tạo milestone \"" + m.getTitle() + "\"", null,
                    m.getCreatedAt(), null, m.getId()));
        }
        for (TeamMemberHistory h : memberHistory.findByTeamIdOrderByCreatedAtDescIdDesc(teamId)) {
            all.add(new ActivityDto("MEMBER", memberText(h), user(h.getActor()), h.getCreatedAt(), null, null));
        }
        for (GithubPullRequest p : pulls.findTop20ByTeamIdOrderByCreatedAtDesc(teamId)) {
            all.add(new ActivityDto("GITHUB", "Pull request #" + p.getNumber() + " \"" + p.getTitle() + "\" ("
                    + stateText(p.getState()) + ", " + (p.getAuthorLogin() == null ? "?" : p.getAuthorLogin()) + ")",
                    null, p.getMergedAt() != null ? p.getMergedAt() : p.getCreatedAt(), null, null));
        }
        // Bai nop milestone chinh, phan hoi cua giang vien, dang ky de tai, yeu cau thay doi nhom.
        // Khong dua diem dieu chinh va danh gia cheo vao day vi chi nguoi lien quan moi duoc xem.
        for (Submission s : submissions.findTop20ByMilestoneTeamIdOrderBySubmittedAtDesc(teamId)) {
            all.add(new ActivityDto("SUBMISSION", s.getSubmittedBy().getFullName() + " nộp bài v" + s.getVersion()
                    + " cho milestone \"" + s.getMilestone().getTitle() + "\"" + (s.isLate() ? " (trễ hạn)" : ""),
                    user(s.getSubmittedBy()), s.getSubmittedAt(), null, s.getMilestone().getId()));
        }
        for (Feedback f : feedbacks.findTop20BySubmissionMilestoneTeamIdOrderByCreatedAtDesc(teamId)) {
            all.add(new ActivityDto("FEEDBACK", f.getLecturer().getFullName() + " phản hồi bài nộp v"
                    + f.getSubmission().getVersion() + " của milestone \"" + f.getSubmission().getMilestone().getTitle()
                    + "\": " + shorten(f.getComment()), user(f.getLecturer()), f.getCreatedAt(), null,
                    f.getSubmission().getMilestone().getId()));
        }
        for (Registration r : registrations.findByTeamIdOrderByIdDesc(teamId)) {
            all.add(new ActivityDto("REGISTRATION", "Nhóm đăng ký đề tài \"" + r.getTopic().getTitle() + "\" ("
                    + registrationText(r.getStatus()) + ")", null, r.getCreatedAt(), null, null));
        }
        for (TeamChangeRequest r : changeRequests.findTop20ByTeamIdOrderByCreatedAtDesc(teamId)) {
            all.add(new ActivityDto("TEAM_CHANGE", r.getRequestedBy().getFullName() + " gửi yêu cầu: "
                    + TeamChangeService.describe(r), user(r.getRequestedBy()), r.getCreatedAt(), null, null));
            if (r.getDecidedAt() != null && r.getStatus() != ChangeRequestStatus.PENDING) {
                User who = r.getDecidedBy() != null ? r.getDecidedBy() : r.getRequestedBy();
                all.add(new ActivityDto("TEAM_CHANGE", who.getFullName() + " " + changeStatusText(r.getStatus())
                        + " yêu cầu: " + TeamChangeService.describe(r), user(who), r.getDecidedAt(), null, null));
            }
        }
        all.sort(Comparator.comparing(ActivityDto::at).reversed());
        return all.size() > MAX ? all.subList(0, MAX) : all;
    }

    @Transactional(readOnly = true)
    public List<MemberHistoryDto> memberHistory(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        return memberHistory.findByTeamIdOrderByCreatedAtDescIdDesc(teamId).stream()
                .map(h -> new MemberHistoryDto(h.getId(), mapper.user(h.getMember()), user(h.getActor()),
                        h.getAction(), h.getCreatedAt())).toList();
    }

    /** Han cua milestone (khong luu tru) va task con hieu luc trong khoang [from, to]; mac dinh khong gioi han. */
    @Transactional(readOnly = true)
    public List<CalendarEventDto> calendar(Long teamId, LocalDate from, LocalDate to) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        LocalDate today = LocalDate.now();
        List<CalendarEventDto> out = new ArrayList<>();
        for (Milestone m : milestones.findByTeamIdOrderByDueDateAsc(teamId)) {
            if (m.isArchived() || m.getDueDate() == null || !inRange(m.getDueDate(), from, to)) {
                continue;
            }
            out.add(new CalendarEventDto("MILESTONE", m.getId(), m.getId(),
                    (m.getParent() == null ? "Milestone: " : "Milestone phụ: ") + m.getTitle(),
                    m.getDueDate(), null, null, false));
        }
        for (Task t : tasks.findByTeam(teamId)) {
            if (t.getDueDate() == null || t.getMilestone().isArchived() || !TaskStatus.ACTIVE.contains(t.getStatus())
                    || !inRange(t.getDueDate(), from, to)) {
                continue;
            }
            boolean overdue = t.getStatus() != TaskStatus.DONE && t.getDueDate().isBefore(today);
            out.add(new CalendarEventDto("TASK", t.getId(), t.getMilestone().getId(), t.getTitle(), t.getDueDate(),
                    t.getStatus().name(), t.getAssignee() == null ? null : mapper.user(t.getAssignee()), overdue));
        }
        out.sort(Comparator.comparing(CalendarEventDto::date).thenComparing(CalendarEventDto::type));
        return out;
    }

    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    private UserDto user(User u) {
        return u == null ? null : mapper.user(u);
    }

    private static String actorName(User u) {
        return u == null ? "Hệ thống" : u.getFullName();
    }

    private static String shorten(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private static String stateText(String state) {
        return switch (state) {
            case "MERGED" -> "đã merge";
            case "CLOSED" -> "đã đóng";
            default -> "đang mở";
        };
    }

    public static String describe(TaskAction action, String who, String task) {
        String t = "\"" + task + "\"";
        return switch (action) {
            case CREATED -> who + " tạo task " + t;
            case SUGGESTED -> who + " đề xuất task " + t;
            case SUGGESTION_APPROVED -> who + " duyệt đề xuất task " + t;
            case SUGGESTION_REJECTED -> who + " từ chối đề xuất task " + t;
            case UPDATED -> who + " cập nhật task " + t;
            case ASSIGNED -> who + " giao task " + t;
            case UNASSIGNED -> who + " bỏ giao task " + t;
            case STARTED -> who + " bắt đầu làm task " + t;
            case SUBMITTED -> who + " nộp kết quả task " + t;
            case CONFIRMED -> who + " xác nhận hoàn thành task " + t;
            case REVISION_REQUESTED -> who + " yêu cầu sửa lại task " + t;
            case CANCELLED -> who + " hủy task " + t;
        };
    }

    public static String memberText(TeamMemberHistory h) {
        String name = h.getMember().getFullName();
        return switch (h.getAction()) {
            case TEAM_CREATED -> name + " tạo nhóm";
            case ADDED -> name + " được thêm vào nhóm";
            case REMOVED -> name + " bị xóa khỏi nhóm";
            case LEADER_CHANGED -> name + " trở thành nhóm trưởng";
        };
    }

    private static String registrationText(RegStatus status) {
        return switch (status) {
            case PENDING -> "chờ duyệt";
            case APPROVED -> "đã được duyệt";
            case REJECTED -> "bị từ chối";
        };
    }

    private static String changeStatusText(ChangeRequestStatus status) {
        return switch (status) {
            case APPROVED -> "duyệt";
            case REJECTED -> "từ chối";
            case CANCELLED -> "hủy";
            case PENDING -> "đang chờ";
        };
    }
}
