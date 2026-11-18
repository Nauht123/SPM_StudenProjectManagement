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
import vn.huflit.spm.dto.Dtos.DecisionRequest;
import vn.huflit.spm.dto.Dtos.TeamChangeRequestRequest;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.TeamChangeRequestRepository;
import vn.huflit.spm.repo.UserRepository;
import vn.huflit.spm.service.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Thay doi nhom sau khi de tai duoc duyet: phai qua giang vien huong dan, khong ai duoc don phuong thay doi. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamChangeServiceTest {
    @Mock TeamChangeRequestRepository requests;
    @Mock TeamService teamService;
    @Mock TaskService taskService;
    @Mock UserRepository users;
    @Mock RegistrationRepository registrations;
    @Mock AccessService access;
    @Mock NotificationService notifications;
    @Mock Mapper mapper;
    @InjectMocks TeamChangeService service;

    private User leader;
    private User binh;
    private User chi;
    private User newbie;
    private User lecturer;
    private Team team;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setStudentCode("SV00" + id);
        u.setRole(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "An", Role.STUDENT);
        binh = user(2, "Binh", Role.STUDENT);
        chi = user(3, "Chi", Role.STUDENT);
        newbie = user(4, "Dung", Role.STUDENT);
        lecturer = user(7, "Thay Khoa", Role.LECTURER);
        team = new Team();
        team.setId(10L);
        team.setName("Nhom A");
        team.setLeader(leader);
        team.getMembers().add(leader);
        team.getMembers().add(binh);
        team.getMembers().add(chi);

        Topic topic = new Topic();
        topic.setTitle("QL do an");
        topic.setLecturer(lecturer);
        Registration reg = new Registration();
        reg.setTeam(team);
        reg.setTopic(topic);
        reg.setStatus(RegStatus.APPROVED);

        when(teamService.find(10L)).thenReturn(team);
        when(registrations.findFirstByTeamIdAndStatus(10L, RegStatus.APPROVED)).thenReturn(Optional.of(reg));
        when(access.isMember(any(Team.class), any(User.class))).thenAnswer(i -> {
            Team t = i.getArgument(0);
            User u = i.getArgument(1);
            return t.getMembers().stream().anyMatch(m -> m.getId().equals(u.getId()));
        });
        when(access.isLeader(any(Team.class), any(User.class))).thenAnswer(i -> {
            Team t = i.getArgument(0);
            User u = i.getArgument(1);
            return t.getLeader().getId().equals(u.getId());
        });
        when(access.current()).thenReturn(leader);
        when(users.findByStudentCode("SV004")).thenReturn(Optional.of(newbie));
        when(requests.findByTeamIdAndStatus(10L, ChangeRequestStatus.PENDING)).thenReturn(List.of());
        when(requests.save(any(TeamChangeRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TeamChangeRequest pending(TeamChangeType type, User target, User added) {
        TeamChangeRequest r = new TeamChangeRequest();
        r.setId(50L);
        r.setTeam(team);
        r.setType(type);
        r.setRequestedBy(leader);
        r.setTargetMember(target);
        r.setNewMember(added);
        r.setReason("ly do");
        when(requests.findById(50L)).thenReturn(Optional.of(r));
        return r;
    }

    // ---------------- gui yeu cau ----------------
    @Test
    void leaderCreatesRemoveRequestAndLecturerIsNotified() {
        service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 2L, null, "Khong tham gia"));
        verify(requests).save(any(TeamChangeRequest.class));
        verify(notifications).notify(eq(lecturer), any(String.class));
        verify(teamService, never()).applyRemove(any(), any(), any()); // chua ap dung cho den khi duoc duyet
    }

    @Test
    void requestNotAllowedBeforeTopicApproved() {
        when(registrations.findFirstByTeamIdAndStatus(10L, RegStatus.APPROVED)).thenReturn(Optional.empty());
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 2L, null, "ly do ok")));
    }

    @Test
    void plainMemberCanOnlyAskToLeaveThemselves() {
        when(access.current()).thenReturn(binh);
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 3L, null, "xoa Chi")));
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.ADD_MEMBER, null, "SV004", "them nguoi")));
        service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 2L, null, "Xin roi nhom"));
        verify(requests).save(any(TeamChangeRequest.class));
    }

    @Test
    void outsiderCannotCreateRequest() {
        when(access.current()).thenReturn(user(9, "Ngoai", Role.STUDENT));
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 2L, null, "ly do ok")));
    }

    @Test
    void leaderMustTransferBeforeLeavingOrBeingReplaced() {
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 1L, null, "ly do ok")));
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REPLACE_MEMBER, 1L, "SV004", "ly do ok")));
    }

    @Test
    void cannotAddStudentWhoAlreadyBelongsToAnotherTeam() {
        when(teamService.isInAnyTeam(newbie)).thenReturn(true);
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.ADD_MEMBER, null, "SV004", "them nguoi")));
    }

    @Test
    void cannotAddWhenTeamIsFull() {
        team.getMembers().add(user(5, "E", Role.STUDENT));
        team.getMembers().add(user(6, "F", Role.STUDENT));
        assertEquals(TeamService.MAX_MEMBERS, team.getMembers().size());
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.ADD_MEMBER, null, "SV004", "them nguoi")));
    }

    @Test
    void rejectsDuplicatePendingRequest() {
        TeamChangeRequest existing = new TeamChangeRequest();
        existing.setType(TeamChangeType.REMOVE_MEMBER);
        existing.setTargetMember(binh);
        when(requests.findByTeamIdAndStatus(10L, ChangeRequestStatus.PENDING)).thenReturn(List.of(existing));
        assertThrows(ApiException.class,
                () -> service.create(10L, new TeamChangeRequestRequest(TeamChangeType.REMOVE_MEMBER, 2L, null, "ly do ok")));
    }

    // ---------------- duyet ----------------
    @Test
    void approveRemoveReleasesOpenTasksThenRemovesMember() {
        TeamChangeRequest r = pending(TeamChangeType.REMOVE_MEMBER, binh, null);
        when(access.current()).thenReturn(lecturer);
        service.decide(50L, new DecisionRequest(true, "dong y"));
        verify(taskService).releaseOpenTasksOf(team, binh, lecturer);
        verify(teamService).applyRemove(team, binh, lecturer);
        assertEquals(ChangeRequestStatus.APPROVED, r.getStatus());
        assertEquals(lecturer, r.getDecidedBy());
        assertNotNull(r.getDecidedAt());
    }

    @Test
    void approveReplaceRemovesOldAndAddsNew() {
        pending(TeamChangeType.REPLACE_MEMBER, binh, newbie);
        when(access.current()).thenReturn(lecturer);
        service.decide(50L, new DecisionRequest(true, null));
        verify(teamService).applyRemove(team, binh, lecturer);
        verify(teamService).applyAdd(team, newbie, lecturer);
    }

    @Test
    void approveTransferLeaderChangesLeader() {
        pending(TeamChangeType.TRANSFER_LEADER, chi, null);
        when(access.current()).thenReturn(lecturer);
        service.decide(50L, new DecisionRequest(true, null));
        verify(teamService).applyTransferLeader(team, chi, lecturer);
    }

    @Test
    void rejectingNeedsReasonAndChangesNothing() {
        TeamChangeRequest r = pending(TeamChangeType.REMOVE_MEMBER, binh, null);
        when(access.current()).thenReturn(lecturer);
        assertThrows(ApiException.class, () -> service.decide(50L, new DecisionRequest(false, " ")));
        service.decide(50L, new DecisionRequest(false, "Chua hop ly"));
        assertEquals(ChangeRequestStatus.REJECTED, r.getStatus());
        verify(teamService, never()).applyRemove(any(), any(), any());
        verify(taskService, never()).releaseOpenTasksOf(any(), any(), any());
    }

    @Test
    void onlySupervisorCanDecide() {
        pending(TeamChangeType.REMOVE_MEMBER, binh, null);
        doThrow(ApiException.forbidden("x")).when(access).requireSupervisorOrAdmin(team);
        when(access.current()).thenReturn(leader);
        assertThrows(ApiException.class, () -> service.decide(50L, new DecisionRequest(true, null)));
        verify(teamService, never()).applyRemove(any(), any(), any());
    }

    @Test
    void cannotDecideTwice() {
        TeamChangeRequest r = pending(TeamChangeType.REMOVE_MEMBER, binh, null);
        r.setStatus(ChangeRequestStatus.APPROVED);
        when(access.current()).thenReturn(lecturer);
        assertThrows(ApiException.class, () -> service.decide(50L, new DecisionRequest(true, null)));
    }

    @Test
    void approvalRevalidatesCurrentTeamState() {
        pending(TeamChangeType.ADD_MEMBER, null, newbie);
        when(access.current()).thenReturn(lecturer);
        when(teamService.isInAnyTeam(newbie)).thenReturn(true); // trong luc cho duyet, sinh vien da vao nhom khac
        assertThrows(ApiException.class, () -> service.decide(50L, new DecisionRequest(true, null)));
        verify(teamService, never()).applyAdd(any(), any(), any());
    }

    // ---------------- huy ----------------
    @Test
    void onlyRequesterCanCancelPendingRequest() {
        TeamChangeRequest r = pending(TeamChangeType.REMOVE_MEMBER, binh, null);
        when(access.current()).thenReturn(binh);
        assertThrows(ApiException.class, () -> service.cancel(50L));
        when(access.current()).thenReturn(leader);
        service.cancel(50L);
        assertEquals(ChangeRequestStatus.CANCELLED, r.getStatus());
    }

    @Test
    void describeReadsNaturally() {
        TeamChangeRequest leave = pending(TeamChangeType.REMOVE_MEMBER, leader, null);
        assertTrue(TeamChangeService.describe(leave).contains("xin rời nhóm"));
        TeamChangeRequest replace = pending(TeamChangeType.REPLACE_MEMBER, binh, newbie);
        assertEquals("thay Binh bằng Dung", TeamChangeService.describe(replace));
    }
}
