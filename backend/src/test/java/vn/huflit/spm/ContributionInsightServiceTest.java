package vn.huflit.spm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.SubmissionContributorRepository;
import vn.huflit.spm.repo.SubmissionRepository;
import vn.huflit.spm.repo.TaskSubmissionHistoryRepository;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Dashboard dong gop: canh bao cho giang vien, chi tiet ca nhan, phan quyen xem/xuat bao cao. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContributionInsightServiceTest {
    @Mock ContributionService contributionService;
    @Mock TeamService teamService;
    @Mock TaskSubmissionHistoryRepository attempts;
    @Mock SubmissionRepository submissions;
    @Mock SubmissionContributorRepository contributors;
    @Mock RegistrationRepository registrations;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks ContributionInsightService service;

    private User leader;
    private User memberA;
    private User memberB;
    private User lecturer;
    private Team team;
    private Milestone milestone;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setStudentCode("SV00" + id);
        u.setRole(role);
        return u;
    }

    private static MemberContributionDto row(User u, boolean leader, long assigned, long done, long revisions,
                                             double score) {
        return new MemberContributionDto(new UserDto(u.getId(), "u", u.getFullName(), u.getStudentCode(), u.getRole()),
                leader, assigned, done, done, 0, assigned - done, 0, 0, revisions, 10, 5, 8, 0, 0, 0, 0, score, 0, 50,
                List.of());
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "Truong nhom", Role.STUDENT);
        memberA = user(2, "Thanh vien A", Role.STUDENT);
        memberB = user(3, "Thanh vien B", Role.STUDENT);
        lecturer = user(7, "Giang vien", Role.LECTURER);
        team = new Team();
        team.setId(10L);
        team.setName("Nhom Alpha");
        team.setLeader(leader);
        team.getMembers().add(leader);
        team.getMembers().add(memberA);
        team.getMembers().add(memberB);
        milestone = new Milestone();
        milestone.setId(5L);
        milestone.setTitle("Milestone phu 1");
        milestone.setTeam(team);

        when(teamService.find(10L)).thenReturn(team);
        when(access.isLeader(team, leader)).thenReturn(true);
        when(access.current()).thenReturn(memberA);
        ContributionReportDto report = new ContributionReportDto(10L, "Nhom Alpha", 20, "f", List.of(
                row(leader, true, 3, 3, 0, 12), row(memberA, false, 2, 1, 1, 5), row(memberB, false, 2, 1, 0, 3)));
        when(contributionService.report(10L)).thenReturn(report);
    }

    // ---------------- canh bao ----------------
    @Test
    void noFlagsWhenWorkIsEvenlyShared() {
        List<String> flags = ContributionInsightService.balanceFlags(List.of(
                row(leader, true, 2, 2, 0, 5), row(memberA, false, 2, 2, 0, 5), row(memberB, false, 2, 2, 0, 5)));
        assertTrue(flags.isEmpty(), flags.toString());
    }

    @Test
    void flagsMemberWithNoAssignedTask() {
        List<String> flags = ContributionInsightService.balanceFlags(List.of(
                row(leader, true, 3, 3, 0, 9), row(memberA, false, 3, 3, 0, 9), row(memberB, false, 0, 0, 0, 0)));
        assertTrue(flags.stream().anyMatch(f -> f.contains("Thanh vien B") && f.contains("chưa được giao")));
    }

    @Test
    void flagsLowAndConcentratedContribution() {
        List<String> flags = ContributionInsightService.balanceFlags(List.of(
                row(leader, true, 4, 4, 0, 20), row(memberA, false, 2, 1, 0, 3), row(memberB, false, 2, 1, 0, 3)));
        assertTrue(flags.stream().anyMatch(f -> f.contains("tập trung") && f.contains("Truong nhom")));
        assertTrue(flags.stream().anyMatch(f -> f.contains("đóng góp thấp") && f.contains("Thanh vien A")));
    }

    @Test
    void flagsManyRevisionRequests() {
        List<String> flags = ContributionInsightService.balanceFlags(List.of(
                row(leader, true, 2, 2, 0, 5), row(memberA, false, 2, 2, ContributionInsightService.REVISION_ALERT, 5)));
        assertTrue(flags.stream().anyMatch(f -> f.contains("Thanh vien A") && f.contains("yêu cầu sửa")));
    }

    @Test
    void noBalanceFlagsWhenNobodyHasScoreYet() {
        List<String> flags = ContributionInsightService.balanceFlags(List.of(
                row(leader, true, 0, 0, 0, 0), row(memberA, false, 0, 0, 0, 0)));
        assertTrue(flags.isEmpty());
    }

    // ---------------- phan quyen ----------------
    @Test
    void memberCannotViewAnotherMemberDetail() {
        when(access.current()).thenReturn(memberA);
        assertThrows(ApiException.class, () -> service.memberDetail(10L, 3L));
    }

    @Test
    void memberCanViewOwnDetailButCannotExport() {
        when(access.current()).thenReturn(memberA);
        MemberDetailDto d = service.memberDetail(10L, 2L);
        assertEquals(2L, d.summary().member().id());
        assertFalse(d.canExport());
        assertThrows(ApiException.class, () -> service.exportMember(10L, 2L));
        assertThrows(ApiException.class, () -> service.exportTeam(10L));
    }

    @Test
    void leaderAndLecturerCanViewAnyMemberAndExport() {
        when(access.current()).thenReturn(leader);
        assertTrue(service.memberDetail(10L, 3L).canExport());
        assertTrue(service.exportTeam(10L).length > 0);
        when(access.current()).thenReturn(lecturer);
        assertTrue(service.memberDetail(10L, 2L).canExport());
        assertTrue(service.exportMember(10L, 2L).length > 0);
    }

    @Test
    void unknownMemberIsNotFound() {
        when(access.current()).thenReturn(lecturer);
        assertThrows(ApiException.class, () -> service.memberDetail(10L, 99L));
    }

    // ---------------- theo doi sua lai + lich su nop ca nhan ----------------
    @Test
    void collectsRevisionsAndIndividualSubmissionHistoryOfThatMemberOnly() {
        when(access.current()).thenReturn(lecturer);
        Task task = new Task();
        task.setId(100L);
        task.setTitle("Viet bao cao");
        task.setMilestone(milestone);
        task.setDueDate(LocalDate.of(2026, 11, 10));

        TaskSubmissionHistory revised = attempt(1L, task, memberA, 1, SubmissionOutcome.REVISION_REQUESTED,
                LocalDateTime.of(2026, 11, 8, 9, 0));
        revised.setReviewNote("Thieu muc 2");
        revised.setReviewedAt(LocalDateTime.of(2026, 11, 8, 15, 0));
        TaskSubmissionHistory approved = attempt(2L, task, memberA, 2, SubmissionOutcome.APPROVED,
                LocalDateTime.of(2026, 11, 12, 9, 0)); // sau han => tre
        TaskSubmissionHistory others = attempt(3L, task, memberB, 1, SubmissionOutcome.APPROVED,
                LocalDateTime.of(2026, 11, 9, 9, 0));
        when(attempts.findByTeam(10L)).thenReturn(List.of(revised, approved, others));

        Submission uploaded = submission(7L, memberA, 1);
        Submission contributedByA = submission(8L, leader, 2);
        Submission unrelated = submission(9L, leader, 3);
        when(submissions.findByMilestoneTeamId(10L)).thenReturn(List.of(uploaded, contributedByA, unrelated));
        when(contributors.findByTeam(10L)).thenReturn(List.of(
                contributor(uploaded, memberA), contributor(contributedByA, memberA), contributor(unrelated, memberB)));

        MemberDetailDto d = service.memberDetail(10L, 2L);

        assertEquals(1, d.revisions().size());
        assertEquals("Thieu muc 2", d.revisions().get(0).reviewNote());

        assertEquals(4, d.submissionHistory().size()); // 2 lan nop task + 2 bai milestone, khong co cua B
        assertTrue(d.submissionHistory().stream().noneMatch(h -> h.id() == 3L && "TASK".equals(h.kind())));
        IndividualSubmissionDto latestTask = d.submissionHistory().stream()
                .filter(h -> "TASK".equals(h.kind()) && h.attemptNo() == 2).findFirst().orElseThrow();
        assertTrue(latestTask.late());
        assertEquals("PERFORMER", latestTask.role());
        assertEquals("UPLOADER_CONTRIBUTOR", roleOf(d, 7L));
        assertEquals("CONTRIBUTOR", roleOf(d, 8L));
        // sap xep moi nhat truoc
        for (int i = 1; i < d.submissionHistory().size(); i++) {
            assertFalse(d.submissionHistory().get(i - 1).at().isBefore(d.submissionHistory().get(i).at()));
        }
    }

    private static String roleOf(MemberDetailDto d, long submissionId) {
        return d.submissionHistory().stream()
                .filter(h -> "MILESTONE".equals(h.kind()) && h.id() == submissionId)
                .findFirst().orElseThrow().role();
    }

    private TaskSubmissionHistory attempt(long id, Task task, User by, int no, SubmissionOutcome outcome,
                                          LocalDateTime at) {
        TaskSubmissionHistory h = new TaskSubmissionHistory();
        h.setId(id);
        h.setTask(task);
        h.setSubmittedBy(by);
        h.setAttemptNo(no);
        h.setOutcome(outcome);
        h.setSubmittedAt(at);
        return h;
    }

    private Submission submission(long id, User uploader, int version) {
        Submission s = new Submission();
        s.setId(id);
        s.setMilestone(milestone);
        s.setSubmittedBy(uploader);
        s.setVersion(version);
        s.setFileName("bai-" + id + ".docx");
        s.setSubmittedAt(LocalDateTime.of(2026, 11, 1, 8, 0).plusDays(id));
        return s;
    }

    private SubmissionContributor contributor(Submission s, User u) {
        SubmissionContributor c = new SubmissionContributor();
        c.setSubmission(s);
        c.setUser(u);
        return c;
    }
}
