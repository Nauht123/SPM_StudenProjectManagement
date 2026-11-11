package vn.huflit.spm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.ContributionAdjustmentRepository;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Diem tong hop: diem tu dong + dieu chinh cua giang vien (co ly do, thu hoi duoc) + phan quyen xem. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationServiceTest {
    @Mock ContributionAdjustmentRepository adjustments;
    @Mock ContributionService contributionService;
    @Mock PeerEvaluationService peerService;
    @Mock TeamService teamService;
    @Mock MilestoneRepository milestones;
    @Mock NotificationService notifications;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks EvaluationService service;

    private User leader;
    private User member;
    private User lecturer;
    private Team team;
    private Milestone main;
    private Milestone sub;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setRole(role);
        return u;
    }

    private static MemberContributionDto row(User u, boolean leader, double score) {
        return new MemberContributionDto(new UserDto(u.getId(), null, u.getFullName(), null, u.getRole()), leader,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, score, 0, 0, List.of());
    }

    private ContributionAdjustment adj(User who, double delta, boolean revoked) {
        ContributionAdjustment a = new ContributionAdjustment();
        a.setTeam(team);
        a.setMember(who);
        a.setAdjustedBy(lecturer);
        a.setDelta(delta);
        a.setReason("Ly do dieu chinh");
        a.setRevoked(revoked);
        return a;
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "Truong nhom", Role.STUDENT);
        member = user(2, "Thanh vien", Role.STUDENT);
        lecturer = user(9, "Giang vien", Role.LECTURER);
        team = new Team();
        team.setId(10L);
        team.setName("Nhom A");
        team.setLeader(leader);
        team.getMembers().add(leader);
        team.getMembers().add(member);
        main = new Milestone();
        main.setId(1L);
        main.setTeam(team);
        main.setTitle("Bao cao 1");
        main.setDueDate(LocalDate.of(2026, 12, 1));
        sub = new Milestone();
        sub.setId(2L);
        sub.setTeam(team);
        sub.setParent(main);
        sub.setDueDate(LocalDate.of(2026, 11, 20));
        when(teamService.find(10L)).thenReturn(team);
        when(milestones.findById(1L)).thenReturn(Optional.of(main));
        when(milestones.findById(2L)).thenReturn(Optional.of(sub));
        when(access.current()).thenReturn(lecturer);
        when(mapper.user(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            return new UserDto(u.getId(), null, u.getFullName(), null, u.getRole());
        });
        when(contributionService.report(10L)).thenReturn(new ContributionReportDto(10L, "Nhom A", 15, "f",
                List.of(row(leader, true, 10), row(member, false, 5))));
        when(peerService.peerAverages(any(Team.class), anyBoolean()))
                .thenReturn(Map.of(2L, new PeerEvaluationService.PeerAverage(4.2, 2)));
    }

    // ---------------- dieu chinh ----------------
    @Test
    void adjustSavesReasonAdjusterAndNotifiesMember() {
        AdjustmentDto dto = service.adjust(10L, new AdjustmentRequest(2L, 2.5, "  Lam them phan bao mat  ", 1L));
        ArgumentCaptor<ContributionAdjustment> captor = ArgumentCaptor.forClass(ContributionAdjustment.class);
        verify(adjustments).save(captor.capture());
        ContributionAdjustment a = captor.getValue();
        assertEquals(member, a.getMember());
        assertEquals(lecturer, a.getAdjustedBy());
        assertEquals(2.5, a.getDelta(), 0.001);
        assertEquals("Lam them phan bao mat", a.getReason());
        assertEquals(main, a.getMilestone());
        assertEquals(1L, dto.milestoneId());
        verify(notifications).notify(eq(member), anyString());
    }

    @Test
    void onlySupervisorCanAdjust() {
        doThrow(ApiException.forbidden("no")).when(access).requireSupervisor(any(Team.class));
        assertThrows(ApiException.class, () -> service.adjust(10L, new AdjustmentRequest(2L, 1.0, "Ly do hop le", null)));
        verify(adjustments, never()).save(any(ContributionAdjustment.class));
    }

    @Test
    void zeroAdjustmentIsRejected() {
        assertThrows(ApiException.class, () -> service.adjust(10L, new AdjustmentRequest(2L, 0.001, "Ly do hop le", null)));
    }

    @Test
    void adjustedPersonMustBeInTheTeam() {
        assertThrows(ApiException.class, () -> service.adjust(10L, new AdjustmentRequest(99L, 1.0, "Ly do hop le", null)));
    }

    @Test
    void milestoneMustBeMainMilestoneOfTheSameTeam() {
        assertThrows(ApiException.class, () -> service.adjust(10L, new AdjustmentRequest(2L, 1.0, "Ly do hop le", 2L)));
        Team other = new Team();
        other.setId(77L);
        main.setTeam(other);
        assertThrows(ApiException.class, () -> service.adjust(10L, new AdjustmentRequest(2L, 1.0, "Ly do hop le", 1L)));
    }

    @Test
    void revokeKeepsRecordAndNotifies() {
        ContributionAdjustment a = adj(member, -2, false);
        when(adjustments.findById(5L)).thenReturn(Optional.of(a));
        service.revoke(5L);
        assertTrue(a.isRevoked());
        assertNotNull(a.getRevokedAt());
        assertEquals(lecturer, a.getRevokedBy());
        verify(adjustments, never()).delete(any(ContributionAdjustment.class));
        verify(notifications).notify(eq(member), anyString());
    }

    @Test
    void cannotRevokeTwice() {
        when(adjustments.findById(5L)).thenReturn(Optional.of(adj(member, 1, true)));
        assertThrows(ApiException.class, () -> service.revoke(5L));
    }

    // ---------------- tong hop ----------------
    @Test
    void lecturerSummaryAddsOnlyActiveAdjustments() {
        when(adjustments.findByTeamIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(List.of(adj(leader, 3, false), adj(leader, -2, true), adj(member, 1.5, false)));
        EvaluationSummaryDto s = service.summary(10L);
        assertTrue(s.seeAll());
        assertTrue(s.canAdjust());
        EvaluationRowDto l = s.members().get(0);
        EvaluationRowDto m = s.members().get(1);
        assertEquals(3.0, l.adjustment(), 0.001);       // dieu chinh da thu hoi khong tinh
        assertEquals(13.0, l.finalScore(), 0.001);
        assertEquals(6.5, m.finalScore(), 0.001);
        assertEquals(19.5, s.totalFinalScore(), 0.001);
        assertEquals(66.67, l.finalSharePercent(), 0.01);
        assertEquals(2, l.adjustments().size());        // van liet ke ca ban ghi da thu hoi de minh bach
        assertEquals(4.2, m.peerAverage(), 0.001);
        assertNull(l.peerAverage());
    }

    @Test
    void finalScoreNeverNegative() {
        assertEquals(0.0, EvaluationService.finalScore(2, -5), 0.001);
        assertEquals(7.5, EvaluationService.finalScore(5, 2.5), 0.001);
    }

    @Test
    void studentSeesOnlyOwnScoreAndOwnAdjustments() {
        when(access.current()).thenReturn(leader); // nhom truong cung chi thay cua chinh minh
        when(adjustments.findByTeamIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(List.of(adj(leader, 3, false), adj(member, 1.5, false)));
        EvaluationSummaryDto s = service.summary(10L);
        assertFalse(s.seeAll());
        assertFalse(s.canAdjust());
        assertEquals(1, s.members().size());
        EvaluationRowDto r = s.members().get(0);
        assertEquals(1L, r.member().id());
        assertEquals(13.0, r.finalScore(), 0.001);
        assertNull(r.finalSharePercent());
        assertEquals(1, r.adjustments().size());
        assertEquals(0.0, s.totalFinalScore(), 0.001);
        verify(peerService).peerAverages(team, false);
    }
}
