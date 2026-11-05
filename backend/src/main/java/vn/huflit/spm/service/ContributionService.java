package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.SubmissionContributorRepository;
import vn.huflit.spm.repo.SubmissionRepository;
import vn.huflit.spm.repo.TaskRepository;

import java.time.LocalDate;
import java.util.*;

/**
 * Theo doi dong gop ca nhan. Diem dong gop cua mot task da hoan thanh:
 *   diem = trong so x he so do kho x he so chat luong x he so dung han x he so sua lai
 * - he so do kho = 0.7 + 0.1 x do kho (1..5 => 0.8..1.2)
 * - he so chat luong = 0.5 + 0.1 x diem chat luong (1..5 => 0.6..1.0); chua cham = 1.0
 * - he so dung han = 1.0 neu nop dung han (hoac task khong co han), 0.9 neu tre
 * - he so sua lai = max(0.7, 1 - 0.05 x so lan bi yeu cau sua)
 * Task chua xong, bi huy hoac moi chi la de xuat khong duoc tinh diem. Khong tinh theo so luong task hay nguoi upload.
 */
@Service
public class ContributionService {
    public static final String FORMULA = "Điểm task = trọng số × hệ số độ khó (0.7 + 0.1×độ khó) × hệ số chất lượng "
            + "(0.5 + 0.1×điểm chất lượng, chưa chấm = 1.0) × hệ số đúng hạn (1.0 đúng hạn, 0.9 trễ) × "
            + "hệ số sửa lại (1 − 0.05×số lần bị yêu cầu sửa, tối thiểu 0.7). Chỉ tính task đã được nhóm trưởng xác nhận.";

    private final TeamService teamService;
    private final TaskRepository tasks;
    private final SubmissionRepository submissions;
    private final SubmissionContributorRepository contributors;
    private final AccessService access;
    private final Mapper mapper;

    public ContributionService(TeamService teamService, TaskRepository tasks, SubmissionRepository submissions,
                               SubmissionContributorRepository contributors, AccessService access, Mapper mapper) {
        this.teamService = teamService;
        this.tasks = tasks;
        this.submissions = submissions;
        this.contributors = contributors;
        this.access = access;
        this.mapper = mapper;
    }

    /** Task dung han: khong co han, hoac lan nop duoc duyet khong tre qua 24:00 ngay han. */
    public static boolean isOnTime(Task t) {
        return t.getDueDate() == null || t.getSubmittedAt() == null
                || !SubmissionService.isLate(t.getDueDate(), t.getSubmittedAt());
    }

    /** Diem dong gop cua mot task (xem cong thuc o tren). */
    public static double taskPoints(Task t) {
        double difficulty = 0.7 + 0.1 * t.getDifficulty();
        double quality = t.getQualityRating() == null ? 1.0 : 0.5 + 0.1 * t.getQualityRating();
        double timeliness = isOnTime(t) ? 1.0 : 0.9;
        double revision = Math.max(0.7, 1.0 - 0.05 * t.getRevisionCount());
        return round2(t.getWeight() * difficulty * quality * timeliness * revision);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Giang vien huong dan, admin va nhom truong xem duoc moi thanh vien;
     * thanh vien thuong chi xem ket qua cua chinh minh.
     */
    public ContributionReportDto report(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        User viewer = access.current();
        boolean seeAll = viewer.getRole() == Role.ADMIN || viewer.getRole() == Role.LECTURER
                || access.isLeader(team, viewer);

        List<Task> teamTasks = tasks.findByTeam(teamId);
        List<Submission> teamSubmissions = submissions.findByMilestoneTeamId(teamId);
        List<SubmissionContributor> teamContributors = contributors.findByTeam(teamId);

        List<MemberContributionDto> rows = new ArrayList<>();
        double total = 0;
        for (User member : team.getMembers().stream().sorted(Comparator.comparing(User::getId)).toList()) {
            MemberContributionDto row = member(team, member, teamTasks, teamSubmissions, teamContributors);
            rows.add(row);
            total += row.score();
        }
        List<MemberContributionDto> result = new ArrayList<>();
        for (MemberContributionDto r : rows) {
            if (!seeAll && !r.member().id().equals(viewer.getId())) {
                continue;
            }
            double share = total <= 0 ? 0 : round2(r.score() * 100.0 / total);
            result.add(withShare(r, share));
        }
        return new ContributionReportDto(team.getId(), team.getName(), round2(total), FORMULA, result);
    }

    private MemberContributionDto member(Team team, User member, List<Task> teamTasks, List<Submission> teamSubmissions,
                                         List<SubmissionContributor> teamContributors) {
        LocalDate today = LocalDate.now();
        long assigned = 0, done = 0, onTime = 0, late = 0, overdueOpen = 0, cancelled = 0, revisions = 0;
        long weightAssigned = 0, weightDone = 0, hoursDone = 0, suggested = 0, approved = 0;
        double score = 0;
        List<ContributionTaskDto> detail = new ArrayList<>();

        for (Task t : teamTasks) {
            if (t.getSuggestedBy() != null && t.getSuggestedBy().getId().equals(member.getId())) {
                suggested++;
                boolean rejected = t.getStatus() == TaskStatus.CANCELLED && t.getCancelReason() != null
                        && t.getCancelReason().startsWith(TaskService.REJECTED_SUGGESTION_PREFIX);
                if (t.getStatus() != TaskStatus.SUGGESTED && !rejected) {
                    approved++;
                }
            }
            if (t.getAssignee() == null || !t.getAssignee().getId().equals(member.getId())
                    || t.getStatus() == TaskStatus.SUGGESTED) {
                continue;
            }
            if (t.getStatus() == TaskStatus.CANCELLED) {
                cancelled++;
                continue;
            }
            assigned++;
            weightAssigned += t.getWeight();
            revisions += t.getRevisionCount();
            Boolean taskOnTime = null;
            double points = 0;
            if (t.getStatus() == TaskStatus.DONE) {
                done++;
                weightDone += t.getWeight();
                hoursDone += t.getWorkloadHours();
                taskOnTime = isOnTime(t);
                if (taskOnTime) onTime++; else late++;
                points = taskPoints(t);
                score += points;
            } else if (t.getDueDate() != null && t.getDueDate().isBefore(today)) {
                overdueOpen++;
            }
            detail.add(new ContributionTaskDto(t.getId(), t.getTitle(), t.getMilestone().getTitle(), t.getStatus(),
                    t.getWeight(), t.getDifficulty(), t.getWorkloadHours(), t.getDueDate(), taskOnTime,
                    t.getRevisionCount(), t.getQualityRating(), points));
        }
        long uploaded = teamSubmissions.stream()
                .filter(s -> s.getSubmittedBy().getId().equals(member.getId())).count();
        long contributed = teamContributors.stream()
                .filter(c -> c.getUser().getId().equals(member.getId())).count();
        int progress = Mapper.weightedPercent(weightDone, weightAssigned);
        return new MemberContributionDto(mapper.user(member), access.isLeader(team, member), assigned, done, onTime,
                late, assigned - done, overdueOpen, cancelled, revisions, weightAssigned, weightDone,
                (int) hoursDone, suggested, approved, uploaded, contributed, round2(score), 0, progress, detail);
    }

    private static MemberContributionDto withShare(MemberContributionDto r, double share) {
        return new MemberContributionDto(r.member(), r.leader(), r.assignedTasks(), r.doneTasks(), r.onTimeTasks(),
                r.lateTasks(), r.unfinishedTasks(), r.overdueOpenTasks(), r.cancelledTasks(), r.revisionCount(),
                r.weightAssigned(), r.weightDone(), r.workloadHoursDone(), r.suggestedTasks(),
                r.approvedSuggestions(), r.submissionsUploaded(), r.submissionsContributed(), r.score(), share,
                r.progressPercent(), r.tasks());
    }
}
