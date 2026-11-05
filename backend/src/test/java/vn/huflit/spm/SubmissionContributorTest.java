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
import org.springframework.web.multipart.MultipartFile;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.repo.*;
import vn.huflit.spm.service.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nguoi upload khac nguoi dong gop: bai nop ghi nhan ro nhung thanh vien thuc su tham gia. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionContributorTest {
    @Mock SubmissionRepository submissions;
    @Mock FeedbackRepository feedbacks;
    @Mock SubmissionContributorRepository contributors;
    @Mock MilestoneRepository milestones;
    @Mock RegistrationRepository registrations;
    @Mock AccessService access;
    @Mock NotificationService notifications;
    @Mock Mapper mapper;
    @Mock FileStorage storage;
    @Mock MultipartFile file;
    @InjectMocks SubmissionService service;

    private User uploader;
    private User other;
    private Milestone main;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setFullName("U" + id);
        return u;
    }

    @BeforeEach
    void setUp() {
        uploader = user(1);
        other = user(2);
        Team team = new Team();
        team.setId(10L);
        team.setLeader(uploader);
        team.getMembers().add(uploader);
        team.getMembers().add(other);
        main = new Milestone();
        main.setId(1L);
        main.setTeam(team);
        main.setDueDate(LocalDate.of(2099, 1, 1));
        when(milestones.findById(1L)).thenReturn(Optional.of(main));
        when(access.current()).thenReturn(uploader);
        when(file.isEmpty()).thenReturn(false);
        when(storage.save(anyString(), any(MultipartFile.class))).thenReturn(new FileStorage.StoredFile("a.pdf", "p/a.pdf"));
    }

    @Test
    void uploaderIsAlwaysRecordedAsContributor() {
        service.submit(1L, file, null, null);
        ArgumentCaptor<SubmissionContributor> captor = ArgumentCaptor.forClass(SubmissionContributor.class);
        verify(contributors).save(captor.capture());
        assertEquals(uploader, captor.getValue().getUser());
    }

    @Test
    void realContributorsAreRecordedSeparatelyFromUploader() {
        service.submit(1L, file, "ghi chu", List.of(2L));
        ArgumentCaptor<SubmissionContributor> captor = ArgumentCaptor.forClass(SubmissionContributor.class);
        verify(contributors, times(2)).save(captor.capture());
        List<Long> ids = captor.getAllValues().stream().map(c -> c.getUser().getId()).toList();
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }

    @Test
    void duplicateContributorIdsAreCollapsed() {
        service.submit(1L, file, null, List.of(1L, 2L, 2L));
        verify(contributors, times(2)).save(any(SubmissionContributor.class));
    }

    @Test
    void contributorMustBelongToTheTeam() {
        assertThrows(ApiException.class, () -> service.submit(1L, file, null, List.of(99L)));
        verify(contributors, never()).save(any(SubmissionContributor.class));
        verify(submissions, never()).save(any(Submission.class));
    }

    @Test
    void cannotSubmitToArchivedMilestone() {
        main.setArchived(true);
        assertThrows(ApiException.class, () -> service.submit(1L, file, null, null));
    }
}
