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
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.PeerEvaluationRepository;
import vn.huflit.spm.service.*;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Danh gia cheo an danh theo milestone: quy tac cham, ket qua tong hop va bao ve an danh. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeerEvaluationServiceTest {
    @Mock PeerEvaluationRepository evaluations;
    @Mock MilestoneRepository milestones;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks PeerEvaluationService service;

    private User leader;
    private User m2;
    private User m3;
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

    private PeerEvaluation eval(User evaluator, User evaluatee, int c, int w, String comment) {
        PeerEvaluation e = new PeerEvaluation();
        e.setMilestone(main);
        e.setEvaluator(evaluator);
        e.setEvaluatee(evaluatee);
        e.setContributionScore(c);
        e.setWorkloadScore(w);
        e.setComment(comment);
        return e;
    }

    private static PeerRatingRequest rating(long evaluatee, int c, int w) {
        return new PeerRatingRequest(evaluatee, c, w, null);
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "Truong nhom", Role.STUDENT);
        m2 = user(2, "Thanh vien 2", Role.STUDENT);
        m3 = user(3, "Thanh vien 3", Role.STUDENT);
        lecturer = user(9, "Giang vien", Role.LECTURER);
        team = new Team();
        team.setId(10L);
        team.setLeader(leader);
        team.getMembers().add(leader);
        team.getMembers().add(m2);
        team.getMembers().add(m3);
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
        when(milestones.findById(1L)).thenReturn(Optional.of(main));
        when(milestones.findById(2L)).thenReturn(Optional.of(sub));
        when(access.current()).thenReturn(m2);
        when(mapper.user(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            return new UserDto(u.getId(), null, u.getFullName(), null, u.getRole());
        });
    }

    // ---------------- cham diem ----------------
    @Test
    void mustRateEveryOtherMember() {
        assertThrows(ApiException.class,
                () -> service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 4, 3)))));
        verify(evaluations, never()).save(any(PeerEvaluation.class));
    }

    @Test
    void cannotRateYourself() {
        assertThrows(ApiException.class,
                () -> service.save(1L, new PeerEvaluationRequest(List.of(rating(2, 5, 5), rating(1, 4, 3), rating(3, 3, 3)))));
        verify(evaluations, never()).save(any(PeerEvaluation.class));
    }

    @Test
    void cannotRateSomeoneOutsideTheTeam() {
        assertThrows(ApiException.class,
                () -> service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 4, 3), rating(99, 4, 3)))));
    }

    @Test
    void cannotRateSamePersonTwice() {
        assertThrows(ApiException.class,
                () -> service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 4, 3), rating(1, 2, 2), rating(3, 3, 3)))));
    }

    @Test
    void savesOneRatingPerTeammateWithEvaluator() {
        service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 5, 4), new PeerRatingRequest(3L, 3, 2, "  lam tot  "))));
        ArgumentCaptor<PeerEvaluation> captor = ArgumentCaptor.forClass(PeerEvaluation.class);
        verify(evaluations, times(2)).save(captor.capture());
        for (PeerEvaluation e : captor.getAllValues()) {
            assertEquals(m2, e.getEvaluator());
            assertEquals(main, e.getMilestone());
        }
        PeerEvaluation toM3 = captor.getAllValues().stream().filter(e -> e.getEvaluatee().equals(m3)).findFirst().orElseThrow();
        assertEquals(3, toM3.getContributionScore());
        assertEquals(2, toM3.getWorkloadScore());
        assertEquals("lam tot", toM3.getComment());
    }

    @Test
    void resubmittingUpdatesExistingRatingInsteadOfDuplicating() {
        PeerEvaluation old = eval(m2, leader, 2, 2, null);
        when(evaluations.findByMilestoneIdAndEvaluatorId(1L, 2L)).thenReturn(List.of(old));
        service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 5, 5), rating(3, 4, 4))));
        assertEquals(5, old.getContributionScore());
        assertEquals(5, old.getWorkloadScore());
        verify(evaluations, times(2)).save(any(PeerEvaluation.class));
    }

    @Test
    void cannotEvaluateArchivedMilestone() {
        main.setArchived(true);
        assertThrows(ApiException.class,
                () -> service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 4, 3), rating(3, 4, 3)))));
    }

    @Test
    void evaluationBelongsToMainMilestoneNotSubMilestone() {
        assertThrows(ApiException.class, () -> service.mine(2L));
        assertThrows(ApiException.class, () -> service.results(2L));
    }

    @Test
    void nonMemberCannotEvaluate() {
        doThrow(ApiException.forbidden("no")).when(access).requireMember(any(Team.class));
        assertThrows(ApiException.class,
                () -> service.save(1L, new PeerEvaluationRequest(List.of(rating(1, 4, 3), rating(3, 4, 3)))));
        verify(evaluations, never()).save(any(PeerEvaluation.class));
    }

    @Test
    void myViewListsTeammatesAndOnlyMyOwnRatings() {
        when(evaluations.findByMilestoneIdAndEvaluatorId(1L, 2L)).thenReturn(List.of(eval(m2, m3, 4, 5, "ok")));
        MyPeerEvaluationDto dto = service.mine(1L);
        assertEquals(2, dto.teammates().size());
        assertTrue(dto.teammates().stream().noneMatch(u -> u.id().equals(2L)));
        assertEquals(1, dto.ratings().size());
        assertEquals(3L, dto.ratings().get(0).evaluateeId());
        assertTrue(dto.open());
    }

    // ---------------- ket qua tong hop ----------------
    private void sixRatings() {
        when(evaluations.findByMilestoneId(1L)).thenReturn(List.of(
                eval(m2, leader, 5, 4, "b nhan xet"),
                eval(m3, leader, 3, 2, "a nhan xet"),
                eval(m2, m3, 4, 4, null),
                eval(m3, m2, 2, 3, null)));
    }

    @Test
    void lecturerSeesAveragesSortedCommentsAndWhoHasNotRated() {
        when(access.current()).thenReturn(lecturer);
        sixRatings();
        PeerResultsDto res = service.results(1L);
        assertTrue(res.seeAll());
        assertEquals(3, res.members().size());
        PeerResultDto lead = res.members().get(0);
        assertEquals(1L, lead.member().id());
        assertEquals(4.0, lead.avgContribution(), 0.001);
        assertEquals(3.0, lead.avgWorkload(), 0.001);
        assertEquals(3.5, lead.avgOverall(), 0.001);
        assertEquals(List.of("a nhan xet", "b nhan xet"), lead.comments());
        // m2 va m3 da cham du 2 nguoi con lai; nhom truong chua cham
        assertEquals(2, res.evaluatorsDone());
        assertEquals(3, res.evaluatorsTotal());
        assertEquals(List.of("Truong nhom"), res.pendingEvaluators());
    }

    @Test
    void studentOnlySeesOwnRowAndNeverWhoIsPending() {
        when(access.current()).thenReturn(m3);
        sixRatings();
        PeerResultsDto res = service.results(1L);
        assertFalse(res.seeAll());
        assertEquals(1, res.members().size());
        assertEquals(3L, res.members().get(0).member().id());
        assertTrue(res.pendingEvaluators().isEmpty());
    }

    @Test
    void studentResultHiddenUntilEnoughRatings() {
        when(access.current()).thenReturn(m3);
        when(evaluations.findByMilestoneId(1L)).thenReturn(List.of(eval(m2, m3, 5, 5, "chi mot nguoi viet")));
        PeerResultDto row = service.results(1L).members().get(0);
        assertFalse(row.visible());
        assertEquals(1, row.ratingCount());
        assertNull(row.avgContribution());
        assertNull(row.avgOverall());
        assertTrue(row.comments().isEmpty());

        when(evaluations.findByMilestoneId(1L)).thenReturn(List.of(eval(m2, m3, 5, 5, "x"), eval(leader, m3, 3, 3, "y")));
        PeerResultDto shown = service.results(1L).members().get(0);
        assertTrue(shown.visible());
        assertEquals(4.0, shown.avgContribution(), 0.001);
    }

    @Test
    void resultDtosNeverContainTheEvaluator() {
        List<String> row = Arrays.stream(PeerResultDto.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertEquals(List.of("member", "visible", "ratingCount", "avgContribution", "avgWorkload", "avgOverall", "comments"), row);
        List<String> all = Arrays.stream(PeerResultsDto.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertFalse(all.contains("evaluator"));
        assertFalse(all.contains("evaluators"));
        // evaluatee-side DTO co chua "member" chu khong co UserDto cua nguoi cham
        for (RecordComponent c : PeerResultDto.class.getRecordComponents()) {
            assertNotEquals("evaluator", c.getName());
        }
    }

    @Test
    void peerAverageForStudentsNeedsMinimumRatingsPerMilestone() {
        when(evaluations.findByMilestoneTeamId(10L)).thenReturn(List.of(
                eval(m2, leader, 5, 5, null),                 // leader chi co 1 danh gia
                eval(m2, m3, 4, 4, null), eval(leader, m3, 2, 2, null)));
        Map<Long, PeerEvaluationService.PeerAverage> forStudent = service.peerAverages(team, false);
        assertFalse(forStudent.containsKey(1L));
        assertEquals(3.0, forStudent.get(3L).average(), 0.001);
        Map<Long, PeerEvaluationService.PeerAverage> forLecturer = service.peerAverages(team, true);
        assertEquals(5.0, forLecturer.get(1L).average(), 0.001);
        assertEquals(1, forLecturer.get(1L).milestones());
    }
}
