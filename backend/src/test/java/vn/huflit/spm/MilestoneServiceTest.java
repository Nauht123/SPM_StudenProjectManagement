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
import vn.huflit.spm.repo.*;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Milestone co du lieu lien quan thi luu tru (archive), khong xoa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MilestoneServiceTest {
    @Mock MilestoneRepository milestones;
    @Mock TaskRepository tasks;
    @Mock SubmissionRepository submissions;
    @Mock RegistrationRepository registrations;
    @Mock TeamService teamService;
    @Mock AccessService access;
    @Mock NotificationService notifications;
    @Mock Mapper mapper;
    @InjectMocks MilestoneService service;

    private Milestone main;
    private Milestone sub;

    @BeforeEach
    void setUp() {
        User leader = new User();
        leader.setId(1L);
        Team team = new Team();
        team.setId(10L);
        team.setLeader(leader);
        main = new Milestone();
        main.setId(1L);
        main.setTeam(team);
        main.setTitle("Chinh");
        main.setDueDate(LocalDate.of(2026, 12, 1));
        sub = new Milestone();
        sub.setId(2L);
        sub.setTeam(team);
        sub.setParent(main);
        sub.setTitle("Phu");
        sub.setDueDate(LocalDate.of(2026, 11, 20));
        when(milestones.findById(1L)).thenReturn(Optional.of(main));
        when(milestones.findById(2L)).thenReturn(Optional.of(sub));
        when(milestones.findByParentId(1L)).thenReturn(List.of(sub));
        when(access.current()).thenReturn(leader);
    }

    @Test
    void mainMilestoneWithSubmissionsCannotBeDeleted() {
        when(submissions.countByMilestoneId(1L)).thenReturn(2L);
        assertThrows(ApiException.class, () -> service.delete(1L));
        verify(milestones, never()).delete(any(Milestone.class));
    }

    @Test
    void mainMilestoneWithSubMilestonesCannotBeDeleted() {
        when(milestones.countByParentId(1L)).thenReturn(1L);
        assertThrows(ApiException.class, () -> service.delete(1L));
    }

    @Test
    void subMilestoneWithTasksCannotBeDeleted() {
        when(tasks.countByMilestoneId(2L)).thenReturn(3L);
        assertThrows(ApiException.class, () -> service.delete(2L));
        verify(milestones, never()).delete(any(Milestone.class));
    }

    @Test
    void emptySubMilestoneCanBeDeleted() {
        service.delete(2L);
        verify(milestones).delete(sub);
    }

    @Test
    void archivingMainMilestoneArchivesItsSubMilestones() {
        service.archive(1L);
        assertTrue(main.isArchived());
        assertNotNull(main.getArchivedAt());
        assertTrue(sub.isArchived());
    }

    @Test
    void cannotArchiveTwice() {
        main.setArchived(true);
        assertThrows(ApiException.class, () -> service.archive(1L));
    }

    @Test
    void cannotRestoreSubMilestoneWhileMainIsArchived() {
        main.setArchived(true);
        sub.setArchived(true);
        assertThrows(ApiException.class, () -> service.restore(2L));
    }

    @Test
    void restoreReopensMilestone() {
        main.setArchived(true);
        service.restore(1L);
        assertFalse(main.isArchived());
        assertNull(main.getArchivedAt());
    }
}
