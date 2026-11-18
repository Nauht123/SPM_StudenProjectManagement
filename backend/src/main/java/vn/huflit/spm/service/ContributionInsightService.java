package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.SubmissionContributorRepository;
import vn.huflit.spm.repo.SubmissionRepository;
import vn.huflit.spm.repo.TaskSubmissionHistoryRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Dashboard dong gop (giai doan 2): tong hop theo nhom cho giang vien, chi tiet tung thanh vien
 * (hieu suat deadline, theo doi sua lai, lich su nop ca nhan) va xuat bao cao Excel.
 * Phan quyen: giang vien huong dan / admin / nhom truong xem moi thanh vien; thanh vien chi xem chinh minh;
 * chi giang vien, nhom truong va admin duoc xuat bao cao.
 */
@Service
public class ContributionInsightService {
    /** So lan bi yeu cau sua tu muc nay tro len se bi gan co canh bao. */
    public static final int REVISION_ALERT = 3;

    private final ContributionService contributionService;
    private final TeamService teamService;
    private final TaskSubmissionHistoryRepository attempts;
    private final SubmissionRepository submissions;
    private final SubmissionContributorRepository contributors;
    private final RegistrationRepository registrations;
    private final AccessService access;
    private final Mapper mapper;

    public ContributionInsightService(ContributionService contributionService, TeamService teamService,
                                      TaskSubmissionHistoryRepository attempts, SubmissionRepository submissions,
                                      SubmissionContributorRepository contributors,
                                      RegistrationRepository registrations, AccessService access, Mapper mapper) {
        this.contributionService = contributionService;
        this.teamService = teamService;
        this.attempts = attempts;
        this.submissions = submissions;
        this.contributors = contributors;
        this.registrations = registrations;
        this.access = access;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ tong hop theo nhom
    /** Giang vien: cac nhom minh huong dan. Admin: tat ca nhom. */
    public ContributionOverviewDto overview() {
        List<TeamContributionSummaryDto> list = new ArrayList<>();
        for (TeamDto t : teamService.list()) {
            list.add(teamSummary(t.id()));
        }
        list.sort(Comparator.comparing(TeamContributionSummaryDto::teamName, String.CASE_INSENSITIVE_ORDER));
        return new ContributionOverviewDto(list);
    }

    private TeamContributionSummaryDto teamSummary(Long teamId) {
        ContributionReportDto report = contributionService.report(teamId);
        List<MemberContributionDto> ranked = new ArrayList<>(report.members());
        ranked.sort(Comparator.comparingDouble(MemberContributionDto::score).reversed());

        long done = 0, onTime = 0, late = 0, overdueOpen = 0, revisions = 0;
        for (MemberContributionDto m : ranked) {
            done += m.doneTasks();
            onTime += m.onTimeTasks();
            late += m.lateTasks();
            overdueOpen += m.overdueOpenTasks();
            revisions += m.revisionCount();
        }
        int rate = done == 0 ? 0 : (int) Math.round(onTime * 100.0 / done);
        List<String> flags = balanceFlags(ranked);
        if (overdueOpen > 0) {
            flags.add(overdueOpen + " task quá hạn chưa xong");
        }
        List<MemberContributionDto> lite = ranked.stream().map(ContributionInsightService::lite).toList();
        return new TeamContributionSummaryDto(teamId, report.teamName(), topicTitle(teamId), report.totalScore(),
                done, onTime, late, overdueOpen, revisions, rate, flags, lite);
    }

    /**
     * Canh bao cho giang vien: thanh vien chua duoc giao task, bi yeu cau sua nhieu, dong gop qua thap
     * (duoi nua muc chia deu) hoac dong gop tap trung vao mot nguoi (tren 60%; nhom 2 nguoi: tren 75%).
     */
    public static List<String> balanceFlags(List<MemberContributionDto> allRows) {
        // Thanh vien da roi nhom khong tinh vao canh bao can bang (chi con la dong gop lich su)
        List<MemberContributionDto> members = allRows.stream().filter(m -> !ContributionService.isFormer(m)).toList();
        List<String> flags = new ArrayList<>();
        int n = members.size();
        double total = members.stream().mapToDouble(MemberContributionDto::score).sum();
        long assignedAll = members.stream().mapToLong(MemberContributionDto::assignedTasks).sum();
        for (MemberContributionDto m : members) {
            String name = m.member().fullName();
            if (n > 1 && assignedAll > 0 && m.assignedTasks() == 0) {
                flags.add(name + ": chưa được giao task nào");
            }
            if (m.revisionCount() >= REVISION_ALERT) {
                flags.add(name + ": bị yêu cầu sửa " + m.revisionCount() + " lần");
            }
        }
        if (n >= 2 && total > 0) {
            double expected = 100.0 / n;
            double crowded = n == 2 ? 75.0 : 60.0;
            for (MemberContributionDto m : members) {
                double share = m.score() * 100.0 / total;
                String name = m.member().fullName();
                if (m.assignedTasks() > 0 && share < expected * 0.5) {
                    flags.add(name + ": đóng góp thấp (" + round1(share) + "% so với mức chia đều " + round1(expected) + "%)");
                } else if (share > crowded) {
                    flags.add("Đóng góp tập trung vào " + name + " (" + round1(share) + "%)");
                }
            }
        }
        return flags;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static MemberContributionDto lite(MemberContributionDto r) {
        return new MemberContributionDto(r.member(), r.leader(), r.assignedTasks(), r.doneTasks(), r.onTimeTasks(),
                r.lateTasks(), r.unfinishedTasks(), r.overdueOpenTasks(), r.cancelledTasks(), r.revisionCount(),
                r.weightAssigned(), r.weightDone(), r.workloadHoursDone(), r.suggestedTasks(),
                r.approvedSuggestions(), r.submissionsUploaded(), r.submissionsContributed(), r.score(),
                r.sharePercent(), r.progressPercent(), List.of());
    }

    // ------------------------------------------------------------------ chi tiet mot thanh vien
    public MemberDetailDto memberDetail(Long teamId, Long userId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        User viewer = access.current();
        if (!privileged(team, viewer) && !viewer.getId().equals(userId)) {
            throw ApiException.forbidden("Bạn chỉ xem được đóng góp của chính mình");
        }
        boolean isMember = team.getMembers().stream().anyMatch(m -> m.getId().equals(userId));
        if (!isMember) {
            throw ApiException.notFound("Thành viên không thuộc nhóm");
        }
        MemberContributionDto summary = contributionService.report(teamId).members().stream()
                .filter(r -> r.member().id().equals(userId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Không có dữ liệu đóng góp của thành viên này"));

        List<TaskSubmissionHistory> teamAttempts = attempts.findByTeam(teamId);
        List<RevisionEventDto> revisions = teamAttempts.stream()
                .filter(h -> h.getSubmittedBy().getId().equals(userId)
                        && h.getOutcome() == SubmissionOutcome.REVISION_REQUESTED)
                .sorted(Comparator.comparing(TaskSubmissionHistory::getSubmittedAt).reversed())
                .map(h -> new RevisionEventDto(h.getTask().getId(), h.getTask().getTitle(),
                        h.getTask().getMilestone().getTitle(), h.getAttemptNo(), h.getSubmittedAt(),
                        h.getReviewedAt(), mapper.user(h.getReviewedBy()), h.getReviewNote()))
                .toList();

        return new MemberDetailDto(teamId, team.getName(), topicTitle(teamId), summary, revisions,
                submissionHistory(teamId, userId, teamAttempts), canExport(team, viewer), ContributionService.FORMULA);
    }

    /** Gop lan bao hoan thanh task cua thanh vien va cac bai nop milestone ma thanh vien upload hoac dong gop. */
    private List<IndividualSubmissionDto> submissionHistory(Long teamId, Long userId,
                                                            List<TaskSubmissionHistory> teamAttempts) {
        List<IndividualSubmissionDto> history = new ArrayList<>();
        for (TaskSubmissionHistory h : teamAttempts) {
            if (!h.getSubmittedBy().getId().equals(userId)) {
                continue;
            }
            Task t = h.getTask();
            boolean late = t.getDueDate() != null && SubmissionService.isLate(t.getDueDate(), h.getSubmittedAt());
            history.add(new IndividualSubmissionDto("TASK", h.getId(), t.getTitle(), t.getMilestone().getTitle(),
                    h.getAttemptNo(), h.getSubmittedAt(), h.getOutcome().name(), "PERFORMER", late, h.getNote(),
                    h.getFileName()));
        }
        Map<Long, Set<Long>> contributorsBySubmission = new HashMap<>();
        for (SubmissionContributor c : contributors.findByTeam(teamId)) {
            contributorsBySubmission.computeIfAbsent(c.getSubmission().getId(), k -> new HashSet<>())
                    .add(c.getUser().getId());
        }
        for (Submission s : submissions.findByMilestoneTeamId(teamId)) {
            boolean uploader = s.getSubmittedBy().getId().equals(userId);
            boolean contributor = contributorsBySubmission.getOrDefault(s.getId(), Set.of()).contains(userId);
            if (!uploader && !contributor) {
                continue;
            }
            String role = uploader && contributor ? "UPLOADER_CONTRIBUTOR" : uploader ? "UPLOADER" : "CONTRIBUTOR";
            history.add(new IndividualSubmissionDto("MILESTONE", s.getId(), s.getFileName(),
                    s.getMilestone().getTitle(), s.getVersion(), s.getSubmittedAt(), null, role, s.isLate(),
                    s.getNote(), s.getFileName()));
        }
        history.sort(Comparator.comparing(IndividualSubmissionDto::at,
                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())));
        return history;
    }

    // ------------------------------------------------------------------ xuat bao cao
    public byte[] exportTeam(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        requireExport(team);
        return ContributionExport.team(contributionService.report(teamId), topicTitle(teamId), LocalDateTime.now());
    }

    public byte[] exportMember(Long teamId, Long userId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        requireExport(team);
        return ContributionExport.member(memberDetail(teamId, userId), LocalDateTime.now());
    }

    private void requireExport(Team team) {
        if (!canExport(team, access.current())) {
            throw ApiException.forbidden("Chỉ giảng viên, nhóm trưởng hoặc quản trị viên mới xuất được báo cáo đóng góp");
        }
    }

    private boolean canExport(Team team, User viewer) {
        return privileged(team, viewer);
    }

    private boolean privileged(Team team, User viewer) {
        return viewer.getRole() == Role.ADMIN || viewer.getRole() == Role.LECTURER || access.isLeader(team, viewer);
    }

    private String topicTitle(Long teamId) {
        return registrations.findFirstByTeamIdAndStatus(teamId, RegStatus.APPROVED)
                .map(r -> r.getTopic().getTitle()).orElse(null);
    }
}
