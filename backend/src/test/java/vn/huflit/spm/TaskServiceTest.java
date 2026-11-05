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
import vn.huflit.spm.dto.Dtos.TaskRequest;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.TaskRepository;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {
    @Mock TaskRepository tasks;
    @Mock MilestoneRepository milestones;
    @Mock AccessService access;
    @Mock NotificationService notifications;
    @Mock FileStorage storage;
    @Mock Mapper mapper;
    @InjectMocks TaskService service;

    private User leader;
    private User member;
    private User outsider;
    private Milestone main;
    private Milestone sub;
    private Task task;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setRole(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "Truong nhom", Role.STUDENT);
        member = user(2, "Thanh vien", Role.STUDENT);
        outsider = user(3, "Nguoi ngoai", Role.STUDENT);
        Team team = new Team();
        team.setId(10L);
        team.setLeader(leader);
        team.getMembers().add(leader);
        team.getMembers().add(member);

        main = new Milestone();
        main.setId(1L);
        main.setTeam(team);
        main.setDueDate(LocalDate.of(2026, 12, 1));

        sub = new Milestone();
        sub.setId(2L);
        sub.setTeam(team);
        sub.setParent(main);
        sub.setDueDate(LocalDate.of(2026, 11, 20));

        task = new Task();
        task.setId(5L);
        task.setTitle("Viet bao cao");
        task.setMilestone(sub);
        task.setAssignee(member);
        task.setStatus(TaskStatus.DOING);

        when(tasks.findById(5L)).thenReturn(Optional.of(task));
        when(milestones.findById(1L)).thenReturn(Optional.of(main));
        when(milestones.findById(2L)).thenReturn(Optional.of(sub));
        when(access.current()).thenReturn(member);
    }

    @Test
    void assigneeSubmitMovesTaskToReviewAndRecordsTime() {
        service.submit(5L, null, "Da xong");
        assertEquals(TaskStatus.REVIEW, task.getStatus());
        assertNotNull(task.getSubmittedAt());
        assertEquals("Da xong", task.getResultNote());
        verify(notifications).notify(any(User.class), any(String.class));
    }

    @Test
    void nonAssigneeCannotSubmit() {
        when(access.current()).thenReturn(outsider);
        assertThrows(ApiException.class, () -> service.submit(5L, null, "x"));
        assertEquals(TaskStatus.DOING, task.getStatus());
    }

    @Test
    void cannotSubmitWhenTaskNotDoing() {
        task.setStatus(TaskStatus.TODO);
        assertThrows(ApiException.class, () -> service.submit(5L, null, "x"));
    }

    @Test
    void leaderConfirmMovesReviewToDone() {
        task.setStatus(TaskStatus.REVIEW);
        when(access.current()).thenReturn(leader);
        service.confirm(5L);
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertNotNull(task.getConfirmedAt());
    }

    @Test
    void confirmFailsWhenNotWaitingForReview() {
        task.setStatus(TaskStatus.DOING);
        assertThrows(ApiException.class, () -> service.confirm(5L));
    }

    @Test
    void rejectSendsTaskBackToDoingWithNote() {
        task.setStatus(TaskStatus.REVIEW);
        service.reject(5L, "Thieu muc 2");
        assertEquals(TaskStatus.DOING, task.getStatus());
        assertEquals("Thieu muc 2", task.getReviewNote());
    }

    @Test
    void cannotCreateTaskDirectlyUnderMainMilestone() {
        assertThrows(ApiException.class, () -> service.create(1L, new TaskRequest("T", null, null, null)));
    }

    @Test
    void taskDueDateCannotBeAfterSubMilestoneDueDate() {
        assertThrows(ApiException.class,
                () -> service.create(2L, new TaskRequest("T", null, null, LocalDate.of(2026, 11, 25))));
    }
}
