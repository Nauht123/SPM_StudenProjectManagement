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
import org.mockito.ArgumentCaptor;
import vn.huflit.spm.dto.Dtos.ApproveSuggestionRequest;
import vn.huflit.spm.dto.Dtos.TaskRequest;
import vn.huflit.spm.dto.Dtos.TaskSuggestionRequest;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.TaskAssignmentHistoryRepository;
import vn.huflit.spm.repo.TaskHistoryRepository;
import vn.huflit.spm.repo.TaskRepository;
import vn.huflit.spm.repo.TaskSubmissionHistoryRepository;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {
    @Mock TaskRepository tasks;
    @Mock MilestoneRepository milestones;
    @Mock TaskHistoryRepository history;
    @Mock TaskAssignmentHistoryRepository assignments;
    @Mock TaskSubmissionHistoryRepository attempts;
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
        service.confirm(5L, 4);
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertNotNull(task.getConfirmedAt());
        assertEquals(Integer.valueOf(4), task.getQualityRating());
    }

    @Test
    void confirmFailsWhenNotWaitingForReview() {
        task.setStatus(TaskStatus.DOING);
        assertThrows(ApiException.class, () -> service.confirm(5L, null));
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

    // ---------------- lich su, de xuat, huy, trong so ----------------
    @Test
    void everySubmitIsStoredAsAnAttemptInHistory() {
        service.submit(5L, null, "Lan 1");
        ArgumentCaptor<TaskSubmissionHistory> captor = ArgumentCaptor.forClass(TaskSubmissionHistory.class);
        verify(attempts).save(captor.capture());
        TaskSubmissionHistory a = captor.getValue();
        assertEquals(1, a.getAttemptNo());
        assertEquals(member, a.getSubmittedBy());
        assertEquals(SubmissionOutcome.PENDING, a.getOutcome());
        assertEquals("Lan 1", a.getNote());
    }

    @Test
    void secondAttemptGetsNextNumber() {
        when(attempts.maxAttempt(5L)).thenReturn(2);
        service.submit(5L, null, "Lan 3");
        ArgumentCaptor<TaskSubmissionHistory> captor = ArgumentCaptor.forClass(TaskSubmissionHistory.class);
        verify(attempts).save(captor.capture());
        assertEquals(3, captor.getValue().getAttemptNo());
    }

    @Test
    void rejectCountsRevisionAndMarksAttemptRevisionRequested() {
        task.setStatus(TaskStatus.REVIEW);
        TaskSubmissionHistory pending = new TaskSubmissionHistory();
        pending.setAttemptNo(1);
        when(attempts.findFirstByTaskIdAndOutcomeOrderByAttemptNoDesc(5L, SubmissionOutcome.PENDING))
                .thenReturn(Optional.of(pending));
        when(access.current()).thenReturn(leader);
        service.reject(5L, "Thieu muc 2");
        assertEquals(1, task.getRevisionCount());
        assertEquals(SubmissionOutcome.REVISION_REQUESTED, pending.getOutcome());
        assertEquals("Thieu muc 2", pending.getReviewNote());
        assertEquals(leader, pending.getReviewedBy());
    }

    @Test
    void confirmMarksPendingAttemptApproved() {
        task.setStatus(TaskStatus.REVIEW);
        TaskSubmissionHistory pending = new TaskSubmissionHistory();
        when(attempts.findFirstByTaskIdAndOutcomeOrderByAttemptNoDesc(5L, SubmissionOutcome.PENDING))
                .thenReturn(Optional.of(pending));
        when(access.current()).thenReturn(leader);
        service.confirm(5L, null);
        assertEquals(SubmissionOutcome.APPROVED, pending.getOutcome());
        assertNotNull(pending.getReviewedAt());
    }

    @Test
    void statusChangesAreWrittenToTaskHistory() {
        service.submit(5L, null, "x");
        ArgumentCaptor<TaskHistory> captor = ArgumentCaptor.forClass(TaskHistory.class);
        verify(history, atLeastOnce()).save(captor.capture());
        TaskHistory h = captor.getValue();
        assertEquals(TaskAction.SUBMITTED, h.getAction());
        assertEquals(TaskStatus.DOING, h.getFromStatus());
        assertEquals(TaskStatus.REVIEW, h.getToStatus());
        assertEquals(member, h.getActor());
    }

    @Test
    void createStoresWeightFieldsAndRecordsAssignment() {
        when(access.current()).thenReturn(leader);
        service.create(2L, new TaskRequest("Task moi", null, 2L, LocalDate.of(2026, 11, 10), 5, 8, 12));
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(tasks, atLeastOnce()).save(captor.capture());
        Task saved = captor.getValue();
        assertEquals(5, saved.getDifficulty());
        assertEquals(8, saved.getWeight());
        assertEquals(12, saved.getWorkloadHours());
        assertEquals(member, saved.getAssignee());
        assertEquals(leader, saved.getAssignedBy());
        assertNotNull(saved.getAssignedAt());
        ArgumentCaptor<TaskAssignmentHistory> a = ArgumentCaptor.forClass(TaskAssignmentHistory.class);
        verify(assignments).save(a.capture());
        assertEquals(member, a.getValue().getAssignee());
        assertEquals(leader, a.getValue().getAssignedBy());
    }

    @Test
    void createUsesDefaultWeightsWhenOmitted() {
        when(access.current()).thenReturn(leader);
        service.create(2L, new TaskRequest("T", null, null, null));
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(tasks, atLeastOnce()).save(captor.capture());
        assertEquals(Task.DEFAULT_WEIGHT, captor.getValue().getWeight());
        assertEquals(Task.DEFAULT_DIFFICULTY, captor.getValue().getDifficulty());
    }

    @Test
    void reassignClosesOldAssignmentAndOpensNewOne() {
        task.setStatus(TaskStatus.TODO);
        TaskAssignmentHistory open = new TaskAssignmentHistory();
        open.setAssignee(member);
        when(assignments.findFirstByTaskIdAndEndedAtIsNullOrderByIdDesc(5L)).thenReturn(Optional.of(open));
        when(access.current()).thenReturn(leader);
        service.update(5L, new TaskRequest("Viet bao cao", null, 1L, null));
        assertNotNull(open.getEndedAt());
        assertEquals(leader, task.getAssignee());
        assertEquals(leader, task.getAssignedBy());
    }

    @Test
    void cannotReassignTaskWaitingForReview() {
        task.setStatus(TaskStatus.REVIEW);
        when(access.current()).thenReturn(leader);
        assertThrows(ApiException.class, () -> service.update(5L, new TaskRequest("Viet bao cao", null, 1L, null)));
    }

    @Test
    void memberSuggestionIsStoredAsSuggestedAndLeaderIsNotified() {
        service.suggest(2L, new TaskSuggestionRequest("Them test", "mo ta", null, 4, 6));
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(tasks).save(captor.capture());
        Task s = captor.getValue();
        assertEquals(TaskStatus.SUGGESTED, s.getStatus());
        assertEquals(member, s.getSuggestedBy());
        assertNull(s.getAssignee());
        verify(notifications).notify(any(User.class), any(String.class));
    }

    @Test
    void nonMemberCannotSuggest() {
        doThrow(ApiException.forbidden("no")).when(access).requireMember(any(Team.class));
        assertThrows(ApiException.class, () -> service.suggest(2L, new TaskSuggestionRequest("T", null, null, null, null)));
        verify(tasks, never()).save(any(Task.class));
    }

    @Test
    void leaderApprovesSuggestionWithWeightAndAssignee() {
        task.setStatus(TaskStatus.SUGGESTED);
        task.setAssignee(null);
        task.setSuggestedBy(member);
        when(access.current()).thenReturn(leader);
        service.approveSuggestion(5L, new ApproveSuggestionRequest(2L, null, 4, 7, 10));
        assertEquals(TaskStatus.TODO, task.getStatus());
        assertEquals(7, task.getWeight());
        assertEquals(4, task.getDifficulty());
        assertEquals(member, task.getAssignee());
        assertEquals(leader, task.getAssignedBy());
        verify(notifications, atLeastOnce()).notify(any(User.class), any(String.class));
    }

    @Test
    void cannotApproveTaskThatIsNotASuggestion() {
        when(access.current()).thenReturn(leader);
        assertThrows(ApiException.class, () -> service.approveSuggestion(5L, new ApproveSuggestionRequest(null, null, null, null, null)));
    }

    @Test
    void rejectedSuggestionBecomesCancelledWithPrefixedReason() {
        task.setStatus(TaskStatus.SUGGESTED);
        task.setAssignee(null);
        task.setSuggestedBy(member);
        when(access.current()).thenReturn(leader);
        service.rejectSuggestion(5L, "Ngoai pham vi");
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertTrue(task.getCancelReason().startsWith(TaskService.REJECTED_SUGGESTION_PREFIX));
        assertNotNull(task.getCancelledAt());
    }

    @Test
    void cancelKeepsTaskAndHistoryAndNotifiesAssignee() {
        when(access.current()).thenReturn(leader);
        service.cancel(5L, "Doi pham vi");
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals("Doi pham vi", task.getCancelReason());
        assertEquals(member, task.getAssignee());
        verify(tasks, never()).delete(any(Task.class));
        verify(notifications).notify(any(User.class), any(String.class));
    }

    @Test
    void cannotCancelDoneTask() {
        task.setStatus(TaskStatus.DONE);
        when(access.current()).thenReturn(leader);
        assertThrows(ApiException.class, () -> service.cancel(5L, "x"));
    }

    @Test
    void suggesterCanWithdrawOwnSuggestionWithoutBeingLeader() {
        task.setStatus(TaskStatus.SUGGESTED);
        task.setAssignee(null);
        task.setSuggestedBy(member);
        doThrow(ApiException.forbidden("no")).when(access).requireLeaderOrAdmin(any(Team.class));
        service.cancel(5L, "Rut lai");
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void plainMemberCannotCancelSomeoneElsesTask() {
        doThrow(ApiException.forbidden("no")).when(access).requireLeaderOrAdmin(any(Team.class));
        assertThrows(ApiException.class, () -> service.cancel(5L, "x"));
        assertEquals(TaskStatus.DOING, task.getStatus());
    }

    @Test
    void assignedTaskCannotBeHardDeleted() {
        assertThrows(ApiException.class, () -> service.delete(5L));
        verify(tasks, never()).delete(any(Task.class));
    }

    @Test
    void untouchedTaskCanBeHardDeleted() {
        task.setStatus(TaskStatus.TODO);
        task.setAssignee(null);
        service.delete(5L);
        verify(history).deleteByTaskId(5L);
        verify(tasks).delete(task);
    }

    @Test
    void archivedMilestoneIsReadOnlyForTasks() {
        sub.setArchived(true);
        when(access.current()).thenReturn(leader);
        assertThrows(ApiException.class, () -> service.create(2L, new TaskRequest("T", null, null, null)));
        assertThrows(ApiException.class, () -> service.start(5L));
        assertThrows(ApiException.class, () -> service.confirm(5L, null));
    }

    @Test
    void unassigningDoingTaskSendsItBackToTodo() {
        when(access.current()).thenReturn(leader);
        service.update(5L, new TaskRequest("Viet bao cao", null, null, null));
        assertNull(task.getAssignee());
        assertEquals(TaskStatus.TODO, task.getStatus());
    }

    // ---------------- thanh vien roi nhom ----------------
    @Test
    void releaseOpenTasksUnassignsTodoAndDoingButKeepsReviewAndDone() {
        Team team = sub.getTeam();
        Task doing = task; // dang lam, giao cho member
        Task todo = task(6L, TaskStatus.TODO, member);
        Task review = task(7L, TaskStatus.REVIEW, member);
        Task done = task(8L, TaskStatus.DONE, member);
        Task others = task(9L, TaskStatus.DOING, leader);
        when(tasks.findByTeam(10L)).thenReturn(java.util.List.of(doing, todo, review, done, others));

        int released = service.releaseOpenTasksOf(team, member, leader);

        assertEquals(2, released);
        assertNull(doing.getAssignee());
        assertEquals(TaskStatus.TODO, doing.getStatus());
        assertNull(todo.getAssignee());
        assertEquals(member, review.getAssignee()); // cho xac nhan: giu de khong mat dong gop
        assertEquals(member, done.getAssignee());
        assertEquals(leader, others.getAssignee());
    }

    private Task task(long id, TaskStatus status, User assignee) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T" + id);
        t.setMilestone(sub);
        t.setStatus(status);
        t.setAssignee(assignee);
        return t;
    }
}
