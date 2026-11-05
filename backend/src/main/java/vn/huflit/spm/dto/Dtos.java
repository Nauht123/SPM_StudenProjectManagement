package vn.huflit.spm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.huflit.spm.domain.RegStatus;
import vn.huflit.spm.domain.Role;
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
                               long pendingReview, long submissions, boolean overdue) {
    }

    public record TaskRequest(@NotBlank String title, String description, Long assigneeId, LocalDate dueDate) {
    }

    public record ReviewRequest(@NotBlank String note) {
    }

    public record TaskDto(Long id, Long milestoneId, String title, String description, UserDto assignee,
                          TaskStatus status, LocalDate dueDate, String resultNote, String resultFileName,
                          LocalDateTime submittedAt, String reviewNote, LocalDateTime confirmedAt) {
    }

    // ---------- Submission / Feedback ----------
    public record SubmissionDto(Long id, Long milestoneId, int version, String fileName, String note,
                                UserDto submittedBy, LocalDateTime submittedAt, boolean late) {
    }

    public record FeedbackRequest(@NotBlank String comment) {
    }

    public record FeedbackDto(Long id, Long submissionId, UserDto lecturer, String comment,
                              LocalDateTime createdAt) {
    }

    // ---------- Notification / Dashboard ----------
    public record NotificationDto(Long id, String message, boolean seen, LocalDateTime createdAt) {
    }

    public record ProgressDto(Long teamId, String teamName, String topicTitle, long totalTasks, long doneTasks,
                              int percent, long overdueMilestones, long lateSubmissions, boolean atRisk) {
    }
}
