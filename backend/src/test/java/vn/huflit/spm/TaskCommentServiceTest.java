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
import vn.huflit.spm.repo.TaskCommentRepository;
import vn.huflit.spm.repo.TaskRepository;
import vn.huflit.spm.service.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Binh luan task: quyen xem/viet/xoa va thong bao. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskCommentServiceTest {
    @Mock TaskCommentRepository comments;
    @Mock TaskRepository tasks;
    @Mock NotificationService notifications;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks TaskCommentService service;

    private User leader, worker, lecturer, admin;
    private Task task;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setUsername("u" + id);
        u.setRole(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "An", Role.STUDENT);
        worker = user(2, "Binh", Role.STUDENT);
        lecturer = user(3, "GV", Role.LECTURER);
        admin = user(4, "Ad", Role.ADMIN);
        Team team = new Team();
        team.setId(10L);
        team.setLeader(leader);
        Milestone m = new Milestone();
        m.setId(20L);
        m.setTeam(team);
        task = new Task();
        task.setId(30L);
        task.setTitle("Viet bao cao");
        task.setMilestone(m);
        task.setAssignee(worker);
        when(tasks.findById(30L)).thenReturn(Optional.of(task));
        when(access.current()).thenReturn(worker);
        when(comments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mapper.user(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), null, u.getRole());
        });
    }

    @Test
    void addNotifiesAssigneeAndLeaderButNotAuthor() {
        when(access.current()).thenReturn(lecturer);
        CommentDto dto = service.add(30L, new CommentRequest("  Nho bo sung phan 2  "));
        assertEquals("Nho bo sung phan 2", dto.content());
        verify(notifications).notify(eq(worker), anyString());
        verify(notifications).notify(eq(leader), anyString());
        verify(notifications, never()).notify(eq(lecturer), anyString());
    }

    @Test
    void authorOfCommentDoesNotNotifyHimself() {
        service.add(30L, new CommentRequest("xong"));
        verify(notifications, never()).notify(eq(worker), anyString());
        verify(notifications).notify(eq(leader), anyString());
    }

    @Test
    void blankCommentIsRejected() {
        assertThrows(ApiException.class, () -> service.add(30L, new CommentRequest("   ")));
        verify(comments, never()).save(any());
    }

    @Test
    void outsidersCannotCommentOrRead() {
        doThrow(ApiException.forbidden("x")).when(access).requireView(any());
        assertThrows(ApiException.class, () -> service.add(30L, new CommentRequest("hi")));
        assertThrows(ApiException.class, () -> service.list(30L));
    }

    private TaskComment commentBy(User author) {
        TaskComment c = new TaskComment();
        c.setId(7L);
        c.setTask(task);
        c.setAuthor(author);
        c.setContent("x");
        when(comments.findById(7L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void onlyAuthorOrAdminCanDelete() {
        TaskComment c = commentBy(leader);
        assertThrows(ApiException.class, () -> service.delete(7L)); // worker khong phai tac gia
        verify(comments, never()).delete(any());

        when(access.current()).thenReturn(leader);
        service.delete(7L);
        verify(comments).delete(c);
    }

    @Test
    void adminCanDelete() {
        TaskComment c = commentBy(leader);
        when(access.current()).thenReturn(admin);
        service.delete(7L);
        verify(comments).delete(c);
    }

    @Test
    void canDeleteFlagIsPerViewer() {
        TaskComment mine = commentBy(worker); // khong goi mock ngay trong thenReturn (UnfinishedStubbingException)
        when(comments.findByTaskIdOrderByCreatedAtAscIdAsc(30L)).thenReturn(java.util.List.of(mine));
        assertTrue(service.list(30L).get(0).canDelete());
        when(access.current()).thenReturn(lecturer);
        assertFalse(service.list(30L).get(0).canDelete());
    }
}
