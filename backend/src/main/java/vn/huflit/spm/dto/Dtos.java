package vn.huflit.spm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.huflit.spm.domain.RegStatus;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.SubmissionOutcome;
import vn.huflit.spm.domain.TaskAction;
import vn.huflit.spm.domain.TaskStatus;
import vn.huflit.spm.domain.TopicStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Tat ca request/response DTO cua API. */
public final class Dtos {
    private Dtos() {
    }

    // ---------- Auth / User ----------
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank String fullName,
            @NotBlank String studentCode) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank String fullName,
            String studentCode,
            @NotNull Role role) {
    }

    public record UserDto(Long id, String username, String fullName, String studentCode, Role role) {
    }

    public record AuthResponse(String token, UserDto user) {
    }

    // ---------- Topic ----------
    public record TopicRequest(
            @NotBlank String title,
            String description,
            @Min(1) int maxGroups,
            String semester,
            TopicStatus status) {
    }

    public record TopicDto(Long id, String title, String description, int maxGroups, long approvedGroups,
                           String semester, TopicStatus status, UserDto lecturer) {
    }

    // ---------- Team / Registration ----------
    public record TeamRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record AddMemberRequest(@NotBlank String studentCode) {
    }

    public record TeamDto(Long id, String name, UserDto leader, List<UserDto> members,
                          RegistrationDto registration) {
    }

    public record RegistrationRequest(@NotNull Long topicId) {
    }

    public record DecisionRequest(boolean approve, String note) {
    }

    public record RegistrationDto(Long id, Long teamId, String teamName, TopicDto topic, RegStatus status,
                                  String note, LocalDateTime createdAt) {
    }

    // ---------- Milestone / Task ----------
    /** parentId = null: milestone chinh (giang vien tao); khac null: milestone phu (nhom truong tao). */
    public record MilestoneRequest(@NotBlank String title, String description, @NotNull LocalDate dueDate,
                                   Long parentId) {
    }

    public record MilestoneDto(Long id, Long teamId, Long parentId, String title, String description,
                               LocalDate dueDate, long subMilestones, long totalTasks, long doneTasks,
                               long pendingReview, long submissions, boolean overdue, int percent,
                               boolean archived, LocalDateTime archivedAt) {
    }

    /**
     * difficulty 1..5, weight 1..10, workloadHours 1..200. Bo trong = dung gia tri mac dinh (khi tao) hoac giu nguyen (khi sua).
     */
    public record TaskRequest(@NotBlank String title, String description, Long assigneeId, LocalDate dueDate,
                              @Min(1) @Max(5) Integer difficulty, @Min(1) @Max(10) Integer weight,
                              @Min(1) @Max(200) Integer workloadHours) {
        public TaskRequest(String title, String description, Long assigneeId, LocalDate dueDate) {
            this(title, description, assigneeId, dueDate, null, null, null);
        }
    }

    /** Thanh vien de xuat task moi (chua co nguoi giao / trong so chinh thuc). */
    public record TaskSuggestionRequest(@NotBlank String title, String description, LocalDate dueDate,
                                        @Min(1) @Max(5) Integer difficulty, @Min(1) @Max(200) Integer workloadHours) {
    }

    /** Nhom truong duyet de xuat: chot nguoi giao, trong so, do kho... */
    public record ApproveSuggestionRequest(Long assigneeId, LocalDate dueDate, @Min(1) @Max(5) Integer difficulty,
                                           @Min(1) @Max(10) Integer weight, @Min(1) @Max(200) Integer workloadHours) {
    }

    public record ConfirmRequest(@Min(1) @Max(5) Integer qualityRating) {
    }

    public record ReviewRequest(@NotBlank String note) {
    }

    public record TaskDto(Long id, Long milestoneId, String title, String description, UserDto assignee,
                          TaskStatus status, LocalDate dueDate, String resultNote, String resultFileName,
                          LocalDateTime submittedAt, String reviewNote, LocalDateTime confirmedAt,
                          int difficulty, int weight, int workloadHours, int revisionCount, Integer qualityRating,
                          UserDto assignedBy, LocalDateTime assignedAt, UserDto suggestedBy, UserDto createdBy,
                          LocalDateTime createdAt, LocalDateTime cancelledAt, String cancelReason) {
    }

    public record TaskHistoryDto(Long id, UserDto actor, TaskAction action, TaskStatus fromStatus,
                                 TaskStatus toStatus, String detail, LocalDateTime createdAt) {
    }

    public record TaskAssignmentDto(Long id, UserDto assignee, UserDto assignedBy, LocalDateTime assignedAt,
                                    LocalDateTime endedAt) {
    }

    public record TaskSubmissionHistoryDto(Long id, int attemptNo, UserDto submittedBy, String note, String fileName,
                                           LocalDateTime submittedAt, SubmissionOutcome outcome, UserDto reviewedBy,
                                           LocalDateTime reviewedAt, String reviewNote) {
    }

    /** Toan bo lich su cua mot task: su kien, phan cong, cac lan bao hoan thanh. */
    public record TaskTimelineDto(List<TaskHistoryDto> events, List<TaskAssignmentDto> assignments,
                                  List<TaskSubmissionHistoryDto> submissions) {
    }

    // ---------- Submission / Feedback ----------
    /** submittedBy = nguoi upload file; contributors = nhung thanh vien thuc su dong gop noi dung. */
    public record SubmissionDto(Long id, Long milestoneId, int version, String fileName, String note,
                                UserDto submittedBy, LocalDateTime submittedAt, boolean late,
                                List<UserDto> contributors) {
    }

    public record FeedbackRequest(@NotBlank String comment) {
    }

    public record FeedbackDto(Long id, Long submissionId, UserDto lecturer, String comment,
                              LocalDateTime createdAt) {
    }

    // ---------- Notification / Dashboard ----------
    public record NotificationDto(Long id, String message, boolean seen, LocalDateTime createdAt) {
    }

    /** percent = tien do co xet trong so Task (tong trong so task xong / tong trong so task dang hieu luc). */
    public record ProgressDto(Long teamId, String teamName, String topicTitle, long totalTasks, long doneTasks,
                              int percent, long overdueMilestones, long lateSubmissions, boolean atRisk,
                              long totalWeight, long doneWeight) {
    }

    // ---------- Dong gop ca nhan ----------
    public record ContributionTaskDto(Long taskId, String title, String milestoneTitle, TaskStatus status,
                                      int weight, int difficulty, int workloadHours, LocalDate dueDate,
                                      Boolean onTime, int revisions, Integer qualityRating, double points) {
    }

    public record MemberContributionDto(UserDto member, boolean leader, long assignedTasks, long doneTasks,
                                        long onTimeTasks, long lateTasks, long unfinishedTasks,
                                        long overdueOpenTasks, long cancelledTasks, long revisionCount,
                                        long weightAssigned, long weightDone, int workloadHoursDone,
                                        long suggestedTasks, long approvedSuggestions,
                                        long submissionsUploaded, long submissionsContributed,
                                        double score, double sharePercent, int progressPercent,
                                        List<ContributionTaskDto> tasks) {
    }

    public record ContributionReportDto(Long teamId, String teamName, double totalScore, String formula,
                                        List<MemberContributionDto> members) {
    }
}
