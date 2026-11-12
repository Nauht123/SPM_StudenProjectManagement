package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tich hop GitHub: lien ket repo cua nhom (token ca nhan, ma hoa), dong bo commit va pull request,
 * thong ke dong gop theo thanh vien.
 * Phan quyen: nhom truong/admin lien ket, go lien ket; moi thanh vien (va giang vien huong dan) xem va dong bo;
 * moi thanh vien tu khai bao tai khoan GitHub cua minh, nhom truong/admin khai bao ho nguoi khac.
 */
@Service
public class GithubService {
    /** Khoang cach toi thieu giua hai lan dong bo (tranh bi GitHub gioi han luu luong). */
    static final long MIN_SYNC_SECONDS = 30;
    private static final Pattern REPO = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?(?:github\\.com/)?([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$");
    private static final Pattern LOGIN = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");

    private final GithubRepoLinkRepository links;
    private final GithubAccountRepository accounts;
    private final GithubCommitRepository commits;
    private final GithubPullRequestRepository pulls;
    private final GithubClient client;
    private final TokenCrypto crypto;
    private final TeamService teamService;
    private final NotificationService notifications;
    private final AccessService access;
    private final Mapper mapper;

    public GithubService(GithubRepoLinkRepository links, GithubAccountRepository accounts,
                         GithubCommitRepository commits, GithubPullRequestRepository pulls, GithubClient client,
                         TokenCrypto crypto, TeamService teamService, NotificationService notifications,
                         AccessService access, Mapper mapper) {
        this.links = links;
        this.accounts = accounts;
        this.commits = commits;
        this.pulls = pulls;
        this.client = client;
        this.crypto = crypto;
        this.teamService = teamService;
        this.notifications = notifications;
        this.access = access;
        this.mapper = mapper;
    }

    /** Tach owner/repo tu URL (https://github.com/o/r, .git, github.com/o/r) hoac dang o/r. */
    public static String[] parseRepo(String input) {
        Matcher m = REPO.matcher(input == null ? "" : input.strip());
        if (!m.matches() || m.group(1).equalsIgnoreCase("github.com")) {
            throw ApiException.badRequest("Địa chỉ repo không hợp lệ. Ví dụ: https://github.com/ten-nguoi-dung/ten-repo");
        }
        return new String[]{m.group(1), m.group(2)};
    }

    @Transactional(readOnly = true)
    public GithubOverviewDto overview(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        return build(team, links.findByTeamId(teamId).orElse(null));
    }

    @Transactional
    public GithubOverviewDto link(Long teamId, GithubLinkRequest req) {
        Team team = teamService.find(teamId);
        access.requireLeaderOrAdmin(team);
        String[] or = parseRepo(req.repoUrl());
        String token = req.token() == null || req.token().isBlank() ? null : req.token().strip();
        GithubClient.RepoInfo info = client.verify(or[0], or[1], token);

        GithubRepoLink link = links.findByTeamId(teamId).orElseGet(GithubRepoLink::new);
        boolean changedRepo = link.getId() != null
                && (!or[0].equalsIgnoreCase(link.getOwner()) || !or[1].equalsIgnoreCase(link.getRepo()));
        if (changedRepo) {
            commits.deleteByTeamId(teamId);
            pulls.deleteByTeamId(teamId);
            link.setLastSyncAt(null);
        }
        link.setTeam(team);
        link.setOwner(or[0]);
        link.setRepo(or[1]);
        // Neu khong nhap token moi khi doi sang cung repo thi giu token cu; doi repo thi bat buoc dung token moi.
        if (token != null) {
            link.setEncryptedToken(crypto.encrypt(token));
        } else if (changedRepo || link.getId() == null) {
            link.setEncryptedToken(null);
        }
        link.setLinkedBy(access.current());
        link.setLinkedAt(LocalDateTime.now());
        link.setLastSyncError(null);
        links.save(link);
        notifications.notifyTeam(team, "Nhóm đã liên kết repo GitHub " + info.fullName(), access.current());
        return build(team, link);
    }

    @Transactional
    public void unlink(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireLeaderOrAdmin(team);
        GithubRepoLink link = links.findByTeamId(teamId)
                .orElseThrow(() -> ApiException.notFound("Nhóm chưa liên kết GitHub"));
        commits.deleteByTeamId(teamId);
        pulls.deleteByTeamId(teamId);
        links.delete(link);
    }

    /** Lay commit/PR moi tu GitHub. Loi GitHub duoc ghi lai (khong rollback) roi nem ra cho nguoi dung. */
    @Transactional(noRollbackFor = ApiException.class)
    public GithubOverviewDto sync(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        GithubRepoLink link = links.findByTeamId(teamId)
                .orElseThrow(() -> ApiException.notFound("Nhóm chưa liên kết GitHub"));
        LocalDateTime now = LocalDateTime.now();
        if (link.getLastSyncAt() != null
                && Duration.between(link.getLastSyncAt(), now).getSeconds() < MIN_SYNC_SECONDS) {
            throw ApiException.conflict("Vừa đồng bộ xong, vui lòng đợi " + MIN_SYNC_SECONDS + " giây rồi thử lại");
        }
        String token = link.getEncryptedToken() == null ? null : crypto.decrypt(link.getEncryptedToken());
        // Dong bo tang dan: lan sau chi lay commit tu truoc lan dong bo thanh cong gan nhat 2 ngay (de du an toan)
        LocalDateTime since = link.getLastSyncAt() != null && link.getLastSyncError() == null
                ? link.getLastSyncAt().minusDays(2) : null;
        List<GithubClient.Commit> newCommits;
        List<GithubClient.Pull> newPulls;
        try {
            newCommits = client.commits(link.getOwner(), link.getRepo(), token, since);
            newPulls = client.pulls(link.getOwner(), link.getRepo(), token);
        } catch (ApiException e) {
            link.setLastSyncAt(now);
            link.setLastSyncError(e.getMessage());
            links.save(link);
            throw e;
        }

        Set<String> knownShas = new HashSet<>();
        commits.findByTeamId(teamId).forEach(c -> knownShas.add(c.getSha()));
        List<GithubCommit> toSave = new ArrayList<>();
        for (GithubClient.Commit c : newCommits) {
            if (c.sha() == null || c.committedAt() == null || !knownShas.add(c.sha())) {
                continue;
            }
            GithubCommit e = new GithubCommit();
            e.setTeam(team);
            e.setSha(c.sha());
            e.setAuthorLogin(c.authorLogin());
            e.setAuthorName(c.authorName());
            e.setMessage(c.message());
            e.setCommittedAt(c.committedAt());
            e.setUrl(c.url());
            toSave.add(e);
        }
        commits.saveAll(toSave);

        Map<Integer, GithubPullRequest> existing = new HashMap<>();
        pulls.findByTeamId(teamId).forEach(p -> existing.put(p.getNumber(), p));
        List<GithubPullRequest> prs = new ArrayList<>();
        for (GithubClient.Pull p : newPulls) {
            if (p.createdAt() == null) {
                continue;
            }
            GithubPullRequest e = existing.getOrDefault(p.number(), new GithubPullRequest());
            e.setTeam(team);
            e.setNumber(p.number());
            e.setTitle(p.title());
            e.setState(p.state());
            e.setAuthorLogin(p.authorLogin());
            e.setCreatedAt(p.createdAt());
            e.setClosedAt(p.closedAt());
            e.setMergedAt(p.mergedAt());
            e.setUrl(p.url());
            prs.add(e);
        }
        pulls.saveAll(prs);

        link.setLastSyncAt(now);
        link.setLastSyncError(null);
        links.save(link);
        return build(team, link);
    }

    /** Khai bao tai khoan GitHub cua thanh vien. Thanh vien tu khai bao cho minh; nhom truong/admin khai bao ho. */
    @Transactional
    public GithubOverviewDto mapAccount(Long teamId, GithubAccountRequest req) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        User me = access.current();
        User target = team.getMembers().stream().filter(m -> m.getId().equals(req.userId())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Người này không thuộc nhóm"));
        boolean manager = me.getRole() == Role.ADMIN || access.isLeader(team, me);
        if (!target.getId().equals(me.getId()) && !manager) {
            throw ApiException.forbidden("Bạn chỉ được khai báo tài khoản GitHub của chính mình");
        }
        String login = req.login() == null ? "" : req.login().strip();
        if (login.startsWith("@")) {
            login = login.substring(1);
        }
        Optional<GithubAccount> current = accounts.findByTeamIdAndUserId(teamId, target.getId());
        if (login.isEmpty()) {
            current.ifPresent(accounts::delete);
        } else {
            if (!LOGIN.matcher(login).matches()) {
                throw ApiException.badRequest("Tên tài khoản GitHub không hợp lệ");
            }
            final String wanted = login;
            boolean taken = accounts.findByTeamId(teamId).stream()
                    .anyMatch(a -> a.getLogin().equalsIgnoreCase(wanted) && !a.getUser().getId().equals(target.getId()));
            if (taken) {
                throw ApiException.conflict("Tài khoản GitHub này đã được gán cho thành viên khác");
            }
            GithubAccount a = current.orElseGet(GithubAccount::new);
            a.setTeam(team);
            a.setUser(target);
            a.setLogin(wanted);
            accounts.save(a);
        }
        return build(team, links.findByTeamId(teamId).orElse(null));
    }

    private GithubOverviewDto build(Team team, GithubRepoLink link) {
        User me = access.current();
        boolean canManage = me.getRole() == Role.ADMIN || access.isLeader(team, me);
        Long teamId = team.getId();

        Map<String, User> byLogin = new HashMap<>();
        Map<Long, String> loginOf = new HashMap<>();
        for (GithubAccount a : accounts.findByTeamId(teamId)) {
            byLogin.put(a.getLogin().toLowerCase(Locale.ROOT), a.getUser());
            loginOf.put(a.getUser().getId(), a.getLogin());
        }
        List<GithubCommit> allCommits = link == null ? List.of() : commits.findByTeamId(teamId);
        List<GithubPullRequest> allPulls = link == null ? List.of() : pulls.findByTeamId(teamId);

        Map<Long, int[]> stat = new HashMap<>(); // [commits, pullsOpened, pullsMerged]
        Map<Long, LocalDateTime> last = new HashMap<>();
        Map<String, int[]> unmapped = new LinkedHashMap<>(); // ten hien thi -> [commits, pulls]
        Map<String, String> display = new HashMap<>();

        for (GithubCommit c : allCommits) {
            User u = c.getAuthorLogin() == null ? null : byLogin.get(c.getAuthorLogin().toLowerCase(Locale.ROOT));
            if (u != null) {
                stat.computeIfAbsent(u.getId(), k -> new int[3])[0]++;
                last.merge(u.getId(), c.getCommittedAt(), (a, b) -> a.isAfter(b) ? a : b);
            } else {
                String name = c.getAuthorLogin() != null ? c.getAuthorLogin()
                        : c.getAuthorName() != null ? c.getAuthorName() : "(không rõ)";
                String key = name.toLowerCase(Locale.ROOT);
                display.putIfAbsent(key, name);
                unmapped.computeIfAbsent(key, k -> new int[2])[0]++;
            }
        }
        for (GithubPullRequest p : allPulls) {
            User u = p.getAuthorLogin() == null ? null : byLogin.get(p.getAuthorLogin().toLowerCase(Locale.ROOT));
            if (u != null) {
                int[] s = stat.computeIfAbsent(u.getId(), k -> new int[3]);
                s[1]++;
                if ("MERGED".equals(p.getState())) {
                    s[2]++;
                }
                last.merge(u.getId(), p.getCreatedAt(), (a, b) -> a.isAfter(b) ? a : b);
            } else {
                String name = p.getAuthorLogin() != null ? p.getAuthorLogin() : "(không rõ)";
                String key = name.toLowerCase(Locale.ROOT);
                display.putIfAbsent(key, name);
                unmapped.computeIfAbsent(key, k -> new int[2])[1]++;
            }
        }

        List<GithubMemberStatDto> members = new ArrayList<>();
        team.getMembers().stream().sorted(Comparator.comparing(User::getFullName)).forEach(m -> {
            int[] s = stat.getOrDefault(m.getId(), new int[3]);
            members.add(new GithubMemberStatDto(mapper.user(m), loginOf.get(m.getId()), s[0], s[1], s[2],
                    last.get(m.getId())));
        });
        members.sort(Comparator.comparingInt(GithubMemberStatDto::commits).reversed());

        List<GithubUnmappedDto> unmappedList = unmapped.entrySet().stream()
                .map(e -> new GithubUnmappedDto(display.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(GithubUnmappedDto::commits).reversed()).toList();

        List<GithubCommitDto> recentCommits = link == null ? List.of()
                : commits.findTop30ByTeamIdOrderByCommittedAtDesc(teamId).stream()
                .map(c -> new GithubCommitDto(c.getSha(), c.getMessage(), c.getAuthorName(), c.getAuthorLogin(),
                        owner(byLogin, c.getAuthorLogin()), c.getCommittedAt(), c.getUrl())).toList();
        List<GithubPullDto> recentPulls = link == null ? List.of()
                : pulls.findTop20ByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(p -> new GithubPullDto(p.getNumber(), p.getTitle(), p.getState(), p.getAuthorLogin(),
                        owner(byLogin, p.getAuthorLogin()), p.getCreatedAt(), p.getMergedAt(), p.getUrl())).toList();

        return new GithubOverviewDto(link != null, canManage,
                link == null ? null : link.getOwner(), link == null ? null : link.getRepo(),
                link == null ? null : "https://github.com/" + link.getOwner() + "/" + link.getRepo(),
                link != null && link.getEncryptedToken() != null,
                link == null ? null : link.getLastSyncAt(), link == null ? null : link.getLastSyncError(),
                allCommits.size(), allPulls.size(), members, unmappedList, recentCommits, recentPulls,
                weekly(allCommits));
    }

    private UserDto owner(Map<String, User> byLogin, String login) {
        User u = login == null ? null : byLogin.get(login.toLowerCase(Locale.ROOT));
        return u == null ? null : mapper.user(u);
    }

    /** So commit cua 8 tuan gan nhat (tuan bat dau tu thu Hai), tu cu den moi. */
    public static List<GithubWeekDto> weekly(List<GithubCommit> all) {
        LocalDate thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = thisWeek.minusWeeks(7);
        int[] counts = new int[8];
        for (GithubCommit c : all) {
            LocalDate w = c.getCommittedAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            long idx = ChronoUnit.WEEKS.between(start, w);
            if (idx >= 0 && idx < 8) {
                counts[(int) idx]++;
            }
        }
        List<GithubWeekDto> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            out.add(new GithubWeekDto(start.plusWeeks(i), counts[i]));
        }
        return out;
    }
}
