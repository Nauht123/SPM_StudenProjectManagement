package vn.huflit.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;
import vn.huflit.spm.service.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Tich hop GitHub: doc URL repo, ma hoa token, dong bo tang dan, thong ke theo thanh vien, phan quyen khai bao. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GithubServiceTest {
    @Mock GithubRepoLinkRepository links;
    @Mock GithubAccountRepository accounts;
    @Mock GithubCommitRepository commits;
    @Mock GithubPullRequestRepository pulls;
    @Mock GithubClient client;
    @Spy TokenCrypto crypto = new TokenCrypto("test-secret");
    @Mock TeamService teamService;
    @Mock NotificationService notifications;
    @Mock AccessService access;
    @Mock Mapper mapper;
    @InjectMocks GithubService service;

    private User leader;
    private User member;
    private User other;
    private Team team;

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setFullName(name);
        u.setRole(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        leader = user(1, "An", Role.STUDENT);
        member = user(2, "Binh", Role.STUDENT);
        other = user(3, "Chi", Role.STUDENT);
        team = new Team();
        team.setId(10L);
        team.setName("T");
        team.setLeader(leader);
        team.getMembers().addAll(List.of(leader, member, other));
        when(teamService.find(10L)).thenReturn(team);
        when(access.current()).thenReturn(member);
        when(access.isLeader(any(), any())).thenAnswer(i -> ((User) i.getArgument(1)).getId().equals(1L));
        when(access.isMember(any(), any())).thenReturn(true);
        when(mapper.user(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), null, u.getRole());
        });
        when(links.findByTeamId(10L)).thenReturn(Optional.empty());
        when(accounts.findByTeamId(10L)).thenReturn(List.of());
        when(commits.findByTeamId(10L)).thenReturn(List.of());
        when(pulls.findByTeamId(10L)).thenReturn(List.of());
    }

    private GithubRepoLink existingLink(String token) {
        GithubRepoLink l = new GithubRepoLink();
        l.setId(5L);
        l.setTeam(team);
        l.setOwner("o");
        l.setRepo("r");
        l.setEncryptedToken(token == null ? null : crypto.encrypt(token));
        l.setLinkedBy(leader);
        return l;
    }

    private static GithubAccount account(Team t, User u, String login) {
        GithubAccount a = new GithubAccount();
        a.setTeam(t);
        a.setUser(u);
        a.setLogin(login);
        return a;
    }

    private static GithubClient.Commit commit(String sha, String login, LocalDateTime at) {
        return new GithubClient.Commit(sha, login, login, "msg " + sha, at, "http://c/" + sha);
    }

    @Test
    void parseRepoAcceptsCommonForms() {
        assertArrayEquals(new String[]{"a", "b"}, GithubService.parseRepo("https://github.com/a/b"));
        assertArrayEquals(new String[]{"a", "b"}, GithubService.parseRepo("https://github.com/a/b.git"));
        assertArrayEquals(new String[]{"a", "b.c"}, GithubService.parseRepo("github.com/a/b.c/"));
        assertArrayEquals(new String[]{"a", "b"}, GithubService.parseRepo("a/b"));
    }

    @Test
    void parseRepoRejectsGarbage() {
        assertThrows(ApiException.class, () -> GithubService.parseRepo(""));
        assertThrows(ApiException.class, () -> GithubService.parseRepo("https://github.com/onlyowner"));
        assertThrows(ApiException.class, () -> GithubService.parseRepo("https://example.com/a/b/c"));
    }

    @Test
    void linkStoresEncryptedTokenAndNeverExposesIt() {
        when(access.current()).thenReturn(leader);
        when(client.verify("o", "r", "ghp_secret")).thenReturn(new GithubClient.RepoInfo("o/r", true, "main"));
        List<GithubRepoLink> saved = new ArrayList<>();
        when(links.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });

        GithubOverviewDto dto = service.link(10L, new GithubLinkRequest("https://github.com/o/r", "ghp_secret"));

        assertEquals(1, saved.size());
        String stored = saved.get(0).getEncryptedToken();
        assertNotNull(stored);
        assertNotEquals("ghp_secret", stored);
        assertEquals("ghp_secret", crypto.decrypt(stored));
        assertTrue(dto.hasToken());
        assertTrue(dto.canManage());
        // DTO khong co truong nao chua token
        for (var c : GithubOverviewDto.class.getRecordComponents()) {
            assertFalse(c.getName().toLowerCase().contains("token") && !c.getName().equals("hasToken"), c.getName());
        }
    }

    @Test
    void linkRequiresLeaderOrAdmin() {
        doThrow(ApiException.forbidden("x")).when(access).requireLeaderOrAdmin(team);
        assertThrows(ApiException.class,
                () -> service.link(10L, new GithubLinkRequest("https://github.com/o/r", null)));
        verify(client, never()).verify(anyString(), anyString(), any());
    }

    @Test
    void linkFailsWhenGithubRejects() {
        when(client.verify(anyString(), anyString(), any())).thenThrow(ApiException.badRequest("404"));
        assertThrows(ApiException.class, () -> service.link(10L, new GithubLinkRequest("o/r", "t")));
        verify(links, never()).save(any());
    }

    @Test
    void changingRepoClearsOldData() {
        when(access.current()).thenReturn(leader);
        when(links.findByTeamId(10L)).thenReturn(Optional.of(existingLink("old")));
        when(client.verify("x", "y", null)).thenReturn(new GithubClient.RepoInfo("x/y", false, "main"));
        service.link(10L, new GithubLinkRequest("x/y", null));
        verify(commits).deleteByTeamId(10L);
        verify(pulls).deleteByTeamId(10L);
    }

    @Test
    void syncRequiresLinkedRepo() {
        assertThrows(ApiException.class, () -> service.sync(10L));
    }

    @Test
    void syncStoresOnlyNewCommitsAndCountsPerMember() {
        GithubRepoLink l = existingLink("tok");
        when(links.findByTeamId(10L)).thenReturn(Optional.of(l));
        when(accounts.findByTeamId(10L)).thenReturn(List.of(account(team, member, "binh-dev")));
        GithubCommit known = new GithubCommit();
        known.setSha("s1");
        known.setCommittedAt(LocalDateTime.now());
        known.setAuthorLogin("binh-dev");
        when(commits.findByTeamId(10L)).thenReturn(List.of(known));
        LocalDateTime t = LocalDateTime.now().minusDays(1);
        when(client.commits(eq("o"), eq("r"), eq("tok"), any())).thenReturn(List.of(
                commit("s1", "binh-dev", t), commit("s2", "binh-dev", t), commit("s3", "stranger", t)));
        when(client.pulls("o", "r", "tok")).thenReturn(List.of(
                new GithubClient.Pull(1, "PR", "MERGED", "binh-dev", t, t, t, "http://p/1")));

        GithubOverviewDto dto = service.sync(10L);

        verify(commits).saveAll(argThat(list -> ((List<GithubCommit>) list).size() == 2));
        verify(pulls).saveAll(any());
        assertNull(l.getLastSyncError());
        assertNotNull(l.getLastSyncAt());
        GithubMemberStatDto binh = dto.members().stream()
                .filter(m -> m.member().id().equals(2L)).findFirst().orElseThrow();
        assertEquals("binh-dev", binh.login());
    }

    @Test
    void statsAreMappedByAccountAndUnknownAuthorsListedSeparately() {
        when(links.findByTeamId(10L)).thenReturn(Optional.of(existingLink(null)));
        when(accounts.findByTeamId(10L)).thenReturn(List.of(account(team, member, "Binh-Dev")));
        LocalDateTime t = LocalDateTime.now();
        List<GithubCommit> cs = new ArrayList<>();
        for (String login : new String[]{"binh-dev", "BINH-DEV", "stranger"}) {
            GithubCommit c = new GithubCommit();
            c.setSha(login + cs.size());
            c.setAuthorLogin(login);
            c.setCommittedAt(t);
            cs.add(c);
        }
        when(commits.findByTeamId(10L)).thenReturn(cs);
        GithubPullRequest pr = new GithubPullRequest();
        pr.setNumber(1);
        pr.setState("MERGED");
        pr.setAuthorLogin("binh-dev");
        pr.setCreatedAt(t);
        when(pulls.findByTeamId(10L)).thenReturn(List.of(pr));

        GithubOverviewDto dto = service.overview(10L);

        GithubMemberStatDto binh = dto.members().stream()
                .filter(m -> m.member().id().equals(2L)).findFirst().orElseThrow();
        assertEquals(2, binh.commits());
        assertEquals(1, binh.pullsOpened());
        assertEquals(1, binh.pullsMerged());
        assertEquals(1, dto.unmapped().size());
        assertEquals("stranger", dto.unmapped().get(0).author());
        assertEquals(3, dto.totalCommits());
        assertEquals(8, dto.weekly().size());
        assertEquals(3, dto.weekly().get(7).commits());
    }

    @Test
    void syncIsRateLimited() {
        GithubRepoLink l = existingLink(null);
        l.setLastSyncAt(LocalDateTime.now());
        when(links.findByTeamId(10L)).thenReturn(Optional.of(l));
        assertThrows(ApiException.class, () -> service.sync(10L));
        verify(client, never()).commits(anyString(), anyString(), any(), any());
    }

    @Test
    void syncErrorIsRecordedAndRethrown() {
        GithubRepoLink l = existingLink("tok");
        when(links.findByTeamId(10L)).thenReturn(Optional.of(l));
        when(client.commits(anyString(), anyString(), any(), any())).thenThrow(ApiException.badRequest("Token hết hạn"));
        assertThrows(ApiException.class, () -> service.sync(10L));
        assertEquals("Token hết hạn", l.getLastSyncError());
        verify(links).save(l);
    }

    @Test
    void firstSyncFetchesEverythingThenIsIncremental() {
        GithubRepoLink l = existingLink(null);
        when(links.findByTeamId(10L)).thenReturn(Optional.of(l));
        when(client.commits(anyString(), anyString(), any(), any())).thenReturn(List.of());
        when(client.pulls(anyString(), anyString(), any())).thenReturn(List.of());
        service.sync(10L);
        verify(client).commits(eq("o"), eq("r"), isNull(), isNull());

        l.setLastSyncAt(LocalDateTime.now().minusHours(1));
        service.sync(10L);
        verify(client).commits(eq("o"), eq("r"), isNull(), argThat(s -> s != null));
    }

    @Test
    void memberMapsOwnAccount() {
        service.mapAccount(10L, new GithubAccountRequest(2L, "@Binh-Dev"));
        verify(accounts).save(argThat(a -> a.getLogin().equals("Binh-Dev") && a.getUser().getId().equals(2L)));
    }

    @Test
    void memberCannotMapSomeoneElse() {
        assertThrows(ApiException.class, () -> service.mapAccount(10L, new GithubAccountRequest(3L, "chi")));
        verify(accounts, never()).save(any());
    }

    @Test
    void leaderCanMapForOthers() {
        when(access.current()).thenReturn(leader);
        service.mapAccount(10L, new GithubAccountRequest(3L, "chi"));
        verify(accounts).save(argThat(a -> a.getUser().getId().equals(3L)));
    }

    @Test
    void duplicateLoginIsRejected() {
        when(accounts.findByTeamId(10L)).thenReturn(List.of(account(team, other, "SAME")));
        assertThrows(ApiException.class, () -> service.mapAccount(10L, new GithubAccountRequest(2L, "same")));
    }

    @Test
    void invalidLoginAndNonMemberAreRejected() {
        assertThrows(ApiException.class, () -> service.mapAccount(10L, new GithubAccountRequest(2L, "bad login!")));
        assertThrows(ApiException.class, () -> service.mapAccount(10L, new GithubAccountRequest(99L, "x")));
    }

    @Test
    void blankLoginRemovesMapping() {
        GithubAccount a = account(team, member, "binh");
        when(accounts.findByTeamIdAndUserId(10L, 2L)).thenReturn(Optional.of(a));
        service.mapAccount(10L, new GithubAccountRequest(2L, " "));
        verify(accounts).delete(a);
    }

    @Test
    void unlinkRemovesSyncedData() {
        when(access.current()).thenReturn(leader);
        GithubRepoLink l = existingLink(null);
        when(links.findByTeamId(10L)).thenReturn(Optional.of(l));
        service.unlink(10L);
        verify(commits).deleteByTeamId(10L);
        verify(pulls).deleteByTeamId(10L);
        verify(links).delete(l);
    }

    @Test
    void parsesGithubJson() throws Exception {
        var om = new ObjectMapper();
        var c = HttpGithubClient.parseCommit(om.readTree(
                "{\"sha\":\"abc\",\"html_url\":\"http://x\",\"author\":{\"login\":\"bob\"},"
                        + "\"commit\":{\"message\":\"Fix bug\\n\\nlong body\",\"author\":{\"name\":\"Bob\","
                        + "\"date\":\"2026-11-01T00:30:00Z\"}}}"));
        assertEquals("abc", c.sha());
        assertEquals("bob", c.authorLogin());
        assertEquals("Fix bug", c.message());
        assertEquals(LocalDateTime.of(2026, 11, 1, 7, 30), c.committedAt()); // gio Viet Nam (UTC+7)

        var noLogin = HttpGithubClient.parseCommit(om.readTree(
                "{\"sha\":\"d\",\"author\":null,\"commit\":{\"message\":\"m\",\"author\":{\"name\":\"N\","
                        + "\"date\":\"2026-11-01T00:00:00Z\"}}}"));
        assertNull(noLogin.authorLogin());

        var merged = HttpGithubClient.parsePull(om.readTree(
                "{\"number\":7,\"title\":\"T\",\"state\":\"closed\",\"user\":{\"login\":\"bob\"},"
                        + "\"created_at\":\"2026-11-01T00:00:00Z\",\"closed_at\":\"2026-11-02T00:00:00Z\","
                        + "\"merged_at\":\"2026-11-02T00:00:00Z\",\"html_url\":\"http://p\"}"));
        assertEquals("MERGED", merged.state());
        var closed = HttpGithubClient.parsePull(om.readTree(
                "{\"number\":8,\"title\":\"T\",\"state\":\"closed\",\"user\":{\"login\":\"b\"},"
                        + "\"created_at\":\"2026-11-01T00:00:00Z\",\"closed_at\":\"2026-11-02T00:00:00Z\","
                        + "\"merged_at\":null}"));
        assertEquals("CLOSED", closed.state());
        var open = HttpGithubClient.parsePull(om.readTree(
                "{\"number\":9,\"title\":\"T\",\"state\":\"open\",\"user\":{\"login\":\"b\"},"
                        + "\"created_at\":\"2026-11-01T00:00:00Z\"}"));
        assertEquals("OPEN", open.state());
    }
}
