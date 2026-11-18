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
import vn.huflit.spm.dto.Dtos.ContributionReportDto;
import vn.huflit.spm.dto.Dtos.MemberContributionDto;
import vn.huflit.spm.dto.Dtos.UserDto;
import vn.huflit.spm.repo.SubmissionContributorRepository;
import vn.huflit.spm.repo.SubmissionRepository;
import vn.huflit.spm.repo.TaskRepository;
import vn.huflit.spm.service.AccessService;
import vn.huflit.spm.service.ContributionService;
import vn.huflit.spm.service.Mapper;
import vn.huflit.spm.service.TeamService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Cong thuc diem dong gop va quyen xem dong gop. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContributionServiceTest {
    @Mock TeamService teamService;
    @Mock TaskRepository tasks;
    @Mock SubmissionRepository submissions;
    @Mock SubmissionContributorRepository contributors;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks ContributionService service;

    private User leader;
    private User member;
    private Team team;
    private Milestone sub;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setRole(role);
        return u;
    }

    private Task done(long id, User assignee, int weight, int difficulty, Integer quality, int revisions,
                      LocalDate due, LocalDateTime submittedAt) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T" + id);
        t.setMilestone(sub);
        t.setAssignee(assignee);
        t.setStatus(TaskStatus.DONE);
        t.setWeight(weight);
        t.setDifficulty(difficulty);
        t.setQualityRating(quality);
        t.setRevisionCount(revisions);
        t.setDueDate(due);
        t.setSubmittedAt(submittedAt);
        return t;
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "Truong nhom", Role.STUDENT);
        member = user(2, "Thanh vien", Role.STUDENT);
        team = new Team();
        team.setId(10L);
        team.setName("Nhom A");
        team.setLeader(leader);
        team.getMembers().add(leader);
        team.getMembers().add(member);
        sub = new Milestone();
        sub.setTitle("MS phu");
        when(teamService.find(10L)).thenReturn(team);
        when(access.isLeader(any(Team.class), any(User.class)))
                .thenAnswer(i -> ((User) i.getArgument(1)).getId().equals(1L));
        when(mapper.user(any(User.class)))
                .thenAnswer(i -> {
                    User u = i.getArgument(0);
                    return new UserDto(u.getId(), null, u.getFullName(), null, u.getRole());
                });
    }

    @Test
    void perfectTaskScoresWeightTimesDifficultyFactor() {
        Task t = done(1, member, 5, 5, 5, 0, null, null);
        // 5 x 1.2 x 1.0 x 1.0 x 1.0
        assertEquals(6.0, ContributionService.taskPoints(t), 0.001);
    }

    @Test
    void ungradedQualityCountsAsFullQuality() {
        Task graded = done(1, member, 4, 3, 5, 0, null, null);
        Task ungraded = done(2, member, 4, 3, null, 0, null, null);
        assertEquals(ContributionService.taskPoints(graded), ContributionService.taskPoints(ungraded), 0.001);
    }

    @Test
    void lowQualityReducesPoints() {
        Task good = done(1, member, 4, 3, 5, 0, null, null);
        Task poor = done(2, member, 4, 3, 1, 0, null, null);
        assertTrue(ContributionService.taskPoints(poor) < ContributionService.taskPoints(good));
        // 4 x 1.0 x 0.6 = 2.4
        assertEquals(2.4, ContributionService.taskPoints(poor), 0.001);
    }

    @Test
    void lateSubmissionIsPenalisedByTenPercent() {
        LocalDate due = LocalDate.of(2026, 11, 10);
        Task onTime = done(1, member, 10, 3, null, 0, due, due.atTime(23, 0));
        Task late = done(2, member, 10, 3, null, 0, due, due.plusDays(2).atTime(9, 0));
        assertTrue(ContributionService.isOnTime(onTime));
        assertFalse(ContributionService.isOnTime(late));
        assertEquals(10.0, ContributionService.taskPoints(onTime), 0.001);
        assertEquals(9.0, ContributionService.taskPoints(late), 0.001);
    }

    @Test
    void revisionsReduceScoreButNeverBelowSeventyPercent() {
        Task one = done(1, member, 10, 3, null, 1, null, null);
        Task many = done(2, member, 10, 3, null, 20, null, null);
        assertEquals(9.5, ContributionService.taskPoints(one), 0.001);
        assertEquals(7.0, ContributionService.taskPoints(many), 0.001);
    }

    @Test
    void reportSplitsSharesByScoreNotByTaskCount() {
        User lecturer = user(9, "GV", Role.LECTURER);
        when(access.current()).thenReturn(lecturer);
        // Truong nhom: 1 task nang (diem 10), thanh vien: 3 task nhe (diem 3 x 1 = 3)
        Task heavy = done(1, leader, 10, 3, null, 0, null, null);
        Task a = done(2, member, 1, 3, null, 0, null, null);
        Task b = done(3, member, 1, 3, null, 0, null, null);
        Task c = done(4, member, 1, 3, null, 0, null, null);
        when(tasks.findByTeam(10L)).thenReturn(List.of(heavy, a, b, c));

        ContributionReportDto report = service.report(10L);
        assertEquals(2, report.members().size());
        MemberContributionDto l = report.members().get(0);
        MemberContributionDto m = report.members().get(1);
        assertEquals(1, l.doneTasks());
        assertEquals(3, m.doneTasks());
        assertEquals(10.0, l.score(), 0.001);
        assertEquals(3.0, m.score(), 0.001);
        assertTrue(l.score() > m.score());
        assertEquals(13.0, report.totalScore(), 0.001);
        assertEquals(76.92, l.sharePercent(), 0.01);
        assertEquals(100.0, l.sharePercent() + m.sharePercent(), 0.05);
    }

    @Test
    void reportCountsOnTimeLateUnfinishedAndCancelled() {
        User lecturer = user(9, "GV", Role.LECTURER);
        when(access.current()).thenReturn(lecturer);
        LocalDate due = LocalDate.of(2026, 1, 10);
        Task onTime = done(1, member, 2, 3, null, 0, due, due.atTime(10, 0));
        Task late = done(2, member, 2, 3, null, 2, due, due.plusDays(3).atStartOfDay());
        Task open = done(3, member, 3, 3, null, 0, due, null);
        open.setStatus(TaskStatus.DOING);
        Task cancelled = done(4, member, 3, 3, null, 0, null, null);
        cancelled.setStatus(TaskStatus.CANCELLED);
        Task suggestion = done(5, null, 3, 3, null, 0, null, null);
        suggestion.setStatus(TaskStatus.SUGGESTED);
        suggestion.setSuggestedBy(member);
        when(tasks.findByTeam(10L)).thenReturn(List.of(onTime, late, open, cancelled, suggestion));

        MemberContributionDto m = service.report(10L).members().stream()
                .filter(x -> x.member().id().equals(2L)).findFirst().orElseThrow();
        assertEquals(3, m.assignedTasks());
        assertEquals(2, m.doneTasks());
        assertEquals(1, m.onTimeTasks());
        assertEquals(1, m.lateTasks());
        assertEquals(1, m.unfinishedTasks());
        assertEquals(1, m.overdueOpenTasks());
        assertEquals(1, m.cancelledTasks());
        assertEquals(2, m.revisionCount());
        assertEquals(1, m.suggestedTasks());
        assertEquals(0, m.approvedSuggestions());
        assertEquals(7, m.weightAssigned());
        assertEquals(4, m.weightDone());
        assertEquals(57, m.progressPercent());
    }

    @Test
    void plainMemberOnlySeesOwnContribution() {
        when(access.current()).thenReturn(member);
        when(tasks.findByTeam(10L)).thenReturn(List.of(done(1, leader, 5, 3, null, 0, null, null),
                done(2, member, 5, 3, null, 0, null, null)));
        ContributionReportDto report = service.report(10L);
        assertEquals(1, report.members().size());
        assertEquals(2L, report.members().get(0).member().id());
        assertEquals(50.0, report.members().get(0).sharePercent(), 0.01);
    }

    @Test
    void leaderSeesEveryMember() {
        when(access.current()).thenReturn(leader);
        when(tasks.findByTeam(10L)).thenReturn(List.of());
        assertEquals(2, service.report(10L).members().size());
    }

    @Test
    void formerMemberKeepsDoneTaskCreditInTeamTotal() {
        User gone = user(9, "Da roi", Role.STUDENT); // khong con trong team.getMembers()
        LocalDate due = LocalDate.now().plusDays(5);
        Task a = done(1, leader, 5, 3, 4, 0, due, LocalDateTime.now());
        Task b = done(2, gone, 5, 3, 4, 0, due, LocalDateTime.now());
        when(tasks.findByTeam(10L)).thenReturn(List.of(a, b));
        when(access.current()).thenReturn(leader);

        ContributionReportDto report = service.report(10L);

        assertEquals(3, report.members().size());
        MemberContributionDto former = report.members().stream().filter(ContributionService::isFormer).findFirst().orElseThrow();
        assertTrue(former.member().fullName().endsWith(ContributionService.FORMER_SUFFIX));
        assertFalse(former.leader());
        assertTrue(former.score() > 0);
        MemberContributionDto lead = report.members().stream().filter(m -> m.member().id().equals(1L)).findFirst().orElseThrow();
        // nguoi con lai khong duoc "an" phan cua nguoi da roi: ty le dua tren tong gom ca nguoi da roi
        assertEquals(lead.score(), former.score(), 0.001);
        assertEquals(50.0, lead.sharePercent(), 0.1);
    }

    @Test
    void formerMemberIsHiddenFromPlainMemberButStillCountsInTotal() {
        User gone = user(9, "Da roi", Role.STUDENT);
        LocalDate due = LocalDate.now().plusDays(5);
        when(tasks.findByTeam(10L)).thenReturn(List.of(done(1, member, 5, 3, 4, 0, due, LocalDateTime.now()),
                done(2, gone, 5, 3, 4, 0, due, LocalDateTime.now())));
        when(access.current()).thenReturn(member);

        ContributionReportDto report = service.report(10L);

        assertEquals(1, report.members().size());
        assertEquals(50.0, report.members().get(0).sharePercent(), 0.1);
    }

    @Test
    void openAndCancelledTasksOfFormerMemberDoNotCreateARow() {
        User gone = user(9, "Da roi", Role.STUDENT);
        Task open = done(1, gone, 5, 3, null, 0, null, null);
        open.setStatus(TaskStatus.CANCELLED);
        when(tasks.findByTeam(10L)).thenReturn(List.of(open));
        when(access.current()).thenReturn(leader);

        assertEquals(2, service.report(10L).members().size());
    }
}
