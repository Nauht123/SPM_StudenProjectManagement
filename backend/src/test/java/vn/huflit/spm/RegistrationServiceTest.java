package vn.huflit.spm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.RegistrationRequest;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.TeamRepository;
import vn.huflit.spm.repo.TopicRepository;
import vn.huflit.spm.service.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {
    @Mock RegistrationRepository registrations;
    @Mock TopicRepository topics;
    @Mock TeamRepository teams;
    @Mock AccessService access;
    @Mock NotificationService notifications;
    @Mock Mapper mapper;
    @InjectMocks RegistrationService service;

    private User leader;
    private Team team;
    private Topic topic;

    private void arrange(int maxGroups, long approvedCount) {
        leader = new User();
        leader.setId(1L);
        leader.setRole(Role.STUDENT);
        team = new Team();
        team.setId(10L);
        team.setName("Nhom A");
        team.setLeader(leader);
        team.getMembers().add(leader);
        User lecturer = new User();
        lecturer.setId(2L);
        lecturer.setRole(Role.LECTURER);
        topic = new Topic();
        topic.setId(5L);
        topic.setTitle("De tai");
        topic.setLecturer(lecturer);
        topic.setMaxGroups(maxGroups);
        topic.setStatus(TopicStatus.OPEN);

        when(access.current()).thenReturn(leader);
        when(teams.findFirstByMembersId(1L)).thenReturn(Optional.of(team));
        when(topics.findById(5L)).thenReturn(Optional.of(topic));
        when(registrations.existsByTeamIdAndStatusIn(anyLong(), anyCollection())).thenReturn(false);
        when(registrations.countByTopicIdAndStatus(5L, RegStatus.APPROVED)).thenReturn(approvedCount);
    }

    @Test
    void registerFailsWhenTopicIsFull() {
        arrange(2, 2);
        assertThrows(ApiException.class, () -> service.register(new RegistrationRequest(5L)));
        verify(registrations, never()).save(any());
    }

    @Test
    void registerSucceedsWhenSlotsRemain() {
        arrange(2, 1);
        service.register(new RegistrationRequest(5L));
        verify(registrations).save(any(Registration.class));
        verify(notifications).notify(any(User.class), any(String.class));
    }

    @Test
    void registerFailsWhenTopicClosed() {
        arrange(2, 0);
        topic.setStatus(TopicStatus.CLOSED);
        assertThrows(ApiException.class, () -> service.register(new RegistrationRequest(5L)));
    }
}
