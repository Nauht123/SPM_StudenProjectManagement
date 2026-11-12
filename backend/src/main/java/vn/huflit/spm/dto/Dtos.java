package vn.huflit.spm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.huflit.spm.domain.MemberAction;
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

    // ---------- Dashboard dong gop (giai doan 2) ----------
    /** Mot lan bi nhom truong yeu cau sua (theo tung lan nop cua thanh vien). */
    public record RevisionEventDto(Long taskId, String taskTitle, String milestoneTitle, int attemptNo,
                                   LocalDateTime submittedAt, LocalDateTime reviewedAt, UserDto reviewedBy,
                                   String reviewNote) {
    }

    /**
     * Lich su nop ca nhan. kind: TASK (bao hoan thanh task) | MILESTONE (bai nop milestone chinh).
     * role: PERFORMER (nguoi thuc hien task) | UPLOADER | CONTRIBUTOR | UPLOADER_CONTRIBUTOR.
     * outcome chi co voi kind = TASK (PENDING / APPROVED / REVISION_REQUESTED).
     */
    public record IndividualSubmissionDto(String kind, Long id, String title, String milestoneTitle, int attemptNo,
                                          LocalDateTime at, String outcome, String role, boolean late, String note,
                                          String fileName) {
    }

    /** Chi tiet dong gop cua mot thanh vien: task, hieu suat deadline, theo doi sua lai, lich su nop. */
    public record MemberDetailDto(Long teamId, String teamName, String topicTitle, MemberContributionDto summary,
                                  List<RevisionEventDto> revisions, List<IndividualSubmissionDto> submissionHistory,
                                  boolean canExport, String formula) {
    }

    /** Tong hop dong gop cua mot nhom cho dashboard giang vien (members khong kem danh sach task). */
    public record TeamContributionSummaryDto(Long teamId, String teamName, String topicTitle, double totalScore,
                                             long doneTasks, long onTimeTasks, long lateTasks,
                                             long overdueOpenTasks, long revisionCount, int onTimeRate,
                                             List<String> flags, List<MemberContributionDto> members) {
    }

    public record ContributionOverviewDto(List<TeamContributionSummaryDto> teams) {
    }

    // ---------- Danh gia cheo an danh + dieu chinh diem (giai doan 3) ----------
    /** Mot danh gia cho mot dong doi: muc do dong gop va do nang cong viec duoc giao (1..5). */
    public record PeerRatingRequest(@NotNull Long evaluateeId,
                                    @NotNull @Min(1) @Max(5) Integer contributionScore,
                                    @NotNull @Min(1) @Max(5) Integer workloadScore,
                                    @Size(max = 500) String comment) {
    }

    public record PeerEvaluationRequest(@NotEmpty @Valid List<PeerRatingRequest> ratings) {
    }

    /** Danh gia cua chinh nguoi dang nhap (khong lo ai la nguoi danh gia vi chi tra ve cho chinh ho). */
    public record PeerRatingDto(Long evaluateeId, int contributionScore, int workloadScore, String comment) {
    }

    public record MyPeerEvaluationDto(Long milestoneId, String milestoneTitle, boolean open, List<UserDto> teammates,
                                      List<PeerRatingDto> ratings) {
    }

    /**
     * Ket qua tong hop cho mot thanh vien. KHONG co thong tin nguoi danh gia.
     * visible = false: chua du so danh gia toi thieu de hien thi cho nguoi duoc danh gia (bao ve an danh).
     */
    public record PeerResultDto(UserDto member, boolean visible, int ratingCount, Double avgContribution,
                                Double avgWorkload, Double avgOverall, List<String> comments) {
    }

    /** pendingEvaluators chi co du lieu cho giang vien/admin; sinh vien luon nhan danh sach rong. */
    public record PeerResultsDto(Long milestoneId, String milestoneTitle, boolean open, int evaluatorsDone,
                                 int evaluatorsTotal, List<String> pendingEvaluators, int minRatingsToShow,
                                 boolean seeAll, List<PeerResultDto> members) {
    }

    public record AdjustmentRequest(@NotNull Long userId,
                                    @NotNull @DecimalMin("-100") @DecimalMax("100") Double delta,
                                    @NotBlank @Size(min = 5, max = 500) String reason,
                                    Long milestoneId) {
    }

    public record AdjustmentDto(Long id, UserDto member, UserDto adjustedBy, Long milestoneId, String milestoneTitle,
                                double delta, String reason, LocalDateTime createdAt, boolean revoked,
                                LocalDateTime revokedAt) {
    }

    /**
     * Diem dong gop cuoi cung = diem tu dong (theo task) + cac dieu chinh con hieu luc cua giang vien (toi thieu 0).
     * peerAverage: trung binh danh gia cheo (thang 5) qua cac milestone; null neu chua co / chua du de hien thi.
     * finalSharePercent chi tinh cho giang vien/admin (null voi sinh vien).
     */
    public record EvaluationRowDto(UserDto member, boolean leader, double autoScore, double adjustment,
                                   double finalScore, Double finalSharePercent, Double peerAverage,
                                   int peerMilestonesRated, List<AdjustmentDto> adjustments) {
    }

    public record EvaluationSummaryDto(Long teamId, String teamName, boolean canAdjust, boolean seeAll,
                                       double totalFinalScore, List<EvaluationRowDto> members) {
    }

    // ---- GitHub ----
    public record GithubLinkRequest(@NotBlank @Size(max = 200) String repoUrl, @Size(max = 255) String token) {
    }

    /** login trống = gỡ liên kết tài khoản GitHub của thành viên. */
    public record GithubAccountRequest(@NotNull Long userId, @Size(max = 100) String login) {
    }

    public record GithubMemberStatDto(UserDto member, String login, int commits, int pullsOpened, int pullsMerged,
                                      LocalDateTime lastActivity) {
    }

    public record GithubUnmappedDto(String author, int commits, int pulls) {
    }

    public record GithubCommitDto(String sha, String message, String authorName, String authorLogin, UserDto member,
                                  LocalDateTime committedAt, String url) {
    }

    public record GithubPullDto(int number, String title, String state, String authorLogin, UserDto member,
                                LocalDateTime createdAt, LocalDateTime mergedAt, String url) {
    }

    public record GithubWeekDto(java.time.LocalDate weekStart, int commits) {
    }

    /** Không có trường token: token chỉ tồn tại dạng mã hóa trong CSDL (hasToken cho biết đã cấu hình hay chưa). */
    public record GithubOverviewDto(boolean linked, boolean canManage, String owner, String repo, String repoUrl,
                                    boolean hasToken, LocalDateTime lastSyncAt, String lastSyncError,
                                    int totalCommits, int totalPulls, List<GithubMemberStatDto> members,
                                    List<GithubUnmappedDto> unmapped, List<GithubCommitDto> recentCommits,
                                    List<GithubPullDto> recentPulls, List<GithubWeekDto> weekly) {
    }

    // ---- Binh luan, nhat ky hoat dong, lich ----
    public record CommentRequest(@NotBlank @Size(max = 1000) String content) {
    }

    public record CommentDto(Long id, Long taskId, UserDto author, String content, LocalDateTime createdAt,
                             boolean canDelete) {
    }

    /** type: TASK, COMMENT, MILESTONE, MEMBER, GITHUB. */
    public record ActivityDto(String type, String message, UserDto actor, LocalDateTime at, Long taskId,
                              Long milestoneId) {
    }

    public record MemberHistoryDto(Long id, UserDto member, UserDto actor, MemberAction action,
                                   LocalDateTime createdAt) {
    }

    /** type: MILESTONE hoac TASK. status chi co voi TASK. */
    public record CalendarEventDto(String type, Long id, Long milestoneId, String title, java.time.LocalDate date,
                                   String status, UserDto assignee, boolean overdue) {
    }
}
