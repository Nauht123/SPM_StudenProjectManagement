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
import vn.huflit.spm.repo.*;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Nhat ky hoat dong gop tu nhieu nguon, lich su thanh vien va lich han chot. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityServiceTest {
    @Mock TaskHistoryRepository taskHistory;
    @Mock TaskCommentRepository comments;
    @Mock MilestoneRepository milestones;
    @Mock TeamMemberHistoryRepository memberHistory;
    @Mock GithubPullRequestRepository pulls;
    @Mock TaskRepository tasks;
    @Mock TeamService teamService;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks ActivityService service;

    private User an;
    private Team team;
    private Milestone main;

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setUsername("u" + id);
        u.setRole(Role.STUDENT);
        return u;
    }

    @BeforeEach
    void setUp() {
        an = user(1, "An");
        team = new Team();
        team.setId(10L);
        team.setLeader(an);
        main = new Milestone();
        main.setId(20L);
        main.setTeam(team);
        main.setTitle("MS1");
        main.setDueDate(LocalDate.now().plusDays(10));
        when(teamService.find(10L)).thenReturn(team);
        when(mapper.user(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), null, u.getRole());
        });
    }

    private Task task(long id, String title, TaskStatus st, LocalDate due) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setMilestone(main);
        t.setStatus(st);
        t.setDueDate(due);
        return t;
    }

    @Test
    void feedMergesSourcesNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        Task t = task(1, "Thiet ke", TaskStatus.TODO, null);
        TaskHistory h = new TaskHistory();
        h.setTask(t);
        h.setActor(an);
        h.setAction(TaskAction.CREATED);
        h.setCreatedAt(now.minusHours(3));
        when(taskHistory.findTop50ByTaskMilestoneTeamIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(h));
        TaskComment c = new TaskComment();
        c.setTask(t);
        c.setAuthor(an);
        c.setContent("ok");
        c.setCreatedAt(now.minusHours(1));
        when(comments.findTop50ByTaskMilestoneTeamIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(c));
        main.setCreatedAt(now.minusDays(1));
        when(milestones.findTop20ByTeamIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(main));
        TeamMemberHistory mh = new TeamMemberHistory();
        mh.setMember(an);
        mh.setAction(MemberAction.TEAM_CREATED);
        mh.setCreatedAt(now.minusDays(2));
        when(memberHistory.findByTeamIdOrderByCreatedAtDescIdDesc(10L)).thenReturn(List.of(mh));

        List<ActivityDto> feed = service.feed(10L);

        assertEquals(List.of("COMMENT", "TASK", "MILESTONE", "MEMBER"), feed.stream().map(ActivityDto::type).toList());
        assertTrue(feed.get(1).message().contains("An tạo task"));
    }

    @Test
    void feedRequiresViewPermission() {
        doThrow(ApiException.forbidden("x")).when(access).requireView(team);
        assertThrows(ApiException.class, () -> service.feed(10L));
        assertThrows(ApiException.class, () -> service.memberHistory(10L));
        assertThrows(ApiException.class, () -> service.calendar(10L, null, null));
    }

    @Test
    void everyTaskActionHasMessage() {
        for (TaskAction a : TaskAction.values()) {
            assertTrue(ActivityService.describe(a, "An", "T").contains("T"), a.name());
        }
    }

    @Test
    void calendarListsDeadlinesAndSkipsArchivedAndInactive() {
        Milestone archived = new Milestone();
        archived.setId(21L);
        archived.setTeam(team);
        archived.setTitle("Cu");
        archived.setDueDate(LocalDate.now());
        archived.setArchived(true);
        when(milestones.findByTeamIdOrderByDueDateAsc(10L)).thenReturn(List.of(archived, main));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(tasks.findByTeam(10L)).thenReturn(List.of(
                task(1, "Tre", TaskStatus.DOING, yesterday),
                task(2, "Xong", TaskStatus.DONE, yesterday),
                task(3, "Huy", TaskStatus.CANCELLED, yesterday),
                task(4, "Khong han", TaskStatus.TODO, null)));

        List<CalendarEventDto> ev = service.calendar(10L, null, null);

        assertEquals(3, ev.size());
        assertEquals("Tre", ev.get(0).title());
        assertTrue(ev.get(0).overdue());
        assertEquals("Xong", ev.get(1).title());
        assertFalse(ev.get(1).overdue());
        assertEquals("MILESTONE", ev.get(2).type());
    }

    @Test
    void calendarHonoursDateRange() {
        when(milestones.findByTeamIdOrderByDueDateAsc(10L)).thenReturn(List.of(main));
        when(tasks.findByTeam(10L)).thenReturn(List.of());
        assertEquals(1, service.calendar(10L, LocalDate.now(), LocalDate.now().plusDays(10)).size());
        assertEquals(0, service.calendar(10L, LocalDate.now().plusDays(11), null).size());
        assertEquals(0, service.calendar(10L, null, LocalDate.now().plusDays(9)).size());
    }
}
