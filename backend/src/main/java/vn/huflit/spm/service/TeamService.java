package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.MemberAction;
import vn.huflit.spm.domain.RegStatus;
import vn.huflit.spm.domain.Registration;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.Team;
import vn.huflit.spm.domain.TeamMemberHistory;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.dto.Dtos.AddMemberRequest;
import vn.huflit.spm.dto.Dtos.TeamDto;
import vn.huflit.spm.dto.Dtos.TeamRequest;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.TeamMemberHistoryRepository;
import vn.huflit.spm.repo.TeamRepository;
import vn.huflit.spm.repo.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class TeamService {
    public static final int MAX_MEMBERS = 5;

    private final TeamRepository teams;
    private final UserRepository users;
    private final RegistrationRepository registrations;
    private final TeamMemberHistoryRepository memberHistory;
    private final AccessService access;
    private final NotificationService notifications;
    private final Mapper mapper;

    public TeamService(TeamRepository teams, UserRepository users, RegistrationRepository registrations,
                       TeamMemberHistoryRepository memberHistory, AccessService access, NotificationService notifications, Mapper mapper) {
        this.teams = teams;
        this.users = users;
        this.registrations = registrations;
        this.memberHistory = memberHistory;
        this.access = access;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    public Optional<TeamDto> myTeam() {
        return teams.findFirstByMembersId(access.current().getId()).map(mapper::team);
    }

    public TeamDto get(Long id) {
        Team t = find(id);
        access.requireView(t);
        return mapper.team(t);
    }

    /** Admin: tat ca nhom. Giang vien: cac nhom thuoc de tai da duyet cua minh. */
    public List<TeamDto> list() {
        User u = access.current();
        if (u.getRole() == Role.ADMIN) {
            return teams.findAll().stream().map(mapper::team).toList();
        }
        return registrations.findByTopicLecturerIdAndStatus(u.getId(), RegStatus.APPROVED).stream()
                .map(Registration::getTeam)
                .map(mapper::team)
                .toList();
    }

    @Transactional
    public TeamDto create(TeamRequest req) {
        User u = access.current();
        if (u.getRole() != Role.STUDENT) {
            throw ApiException.forbidden("Chỉ sinh viên mới tạo nhóm");
        }
        if (teams.findFirstByMembersId(u.getId()).isPresent()) {
            throw ApiException.conflict("Bạn đã thuộc một nhóm");
        }
        Team t = new Team();
        t.setName(req.name().trim());
        t.setLeader(u);
        t.getMembers().add(u);
        Team saved = teams.save(t);
        record(saved, u, u, MemberAction.TEAM_CREATED);
        return mapper.team(saved);
    }

    @Transactional
    public TeamDto addMember(Long teamId, AddMemberRequest req) {
        Team t = find(teamId);
        access.requireLeader(t);
        if (registrations.existsByTeamIdAndStatusIn(teamId, List.of(RegStatus.APPROVED))) {
            throw ApiException.conflict("Nhóm đã được duyệt đề tài. Hãy gửi yêu cầu thay đổi để giảng viên hướng dẫn duyệt.");
        }
        if (t.getMembers().size() >= MAX_MEMBERS) {
            throw ApiException.conflict("Nhóm đã đủ " + MAX_MEMBERS + " thành viên");
        }
        User target = users.findByStudentCode(req.studentCode().trim())
                .filter(s -> s.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sinh viên với MSSV này"));
        if (teams.findFirstByMembersId(target.getId()).isPresent()) {
            throw ApiException.conflict("Sinh viên này đã thuộc một nhóm khác");
        }
        t.getMembers().add(target);
        teams.save(t);
        record(t, target, access.current(), MemberAction.ADDED);
        notifications.notify(target, "Bạn đã được thêm vào nhóm \"" + t.getName() + "\"");
        return mapper.team(t);
    }

    @Transactional
    public TeamDto removeMember(Long teamId, Long userId) {
        Team t = find(teamId);
        access.requireLeader(t);
        if (registrations.existsByTeamIdAndStatusIn(teamId, List.of(RegStatus.APPROVED))) {
            throw ApiException.conflict("Nhóm đã được duyệt đề tài. Hãy gửi yêu cầu thay đổi để giảng viên hướng dẫn duyệt.");
        }
        if (t.getLeader().getId().equals(userId)) {
            throw ApiException.badRequest("Không thể xóa nhóm trưởng khỏi nhóm");
        }
        User removed = t.getMembers().stream().filter(m -> m.getId().equals(userId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Thành viên không thuộc nhóm"));
        t.getMembers().remove(removed);
        teams.save(t);
        record(t, removed, access.current(), MemberAction.REMOVED);
        notifications.notify(removed, "Bạn đã bị xóa khỏi nhóm \"" + t.getName() + "\"");
        return mapper.team(t);
    }

    // ---- Thay doi do giang vien duyet (khong kiem tra quyen o day; TeamChangeService da kiem tra) ----

    /** Them sinh vien vao nhom sau khi duoc duyet: kiem tra lai suc chua va sinh vien chua thuoc nhom khac. */
    @Transactional
    public void applyAdd(Team team, User target, User actor) {
        if (team.getMembers().size() >= MAX_MEMBERS) {
            throw ApiException.conflict("Nhóm đã đủ " + MAX_MEMBERS + " thành viên");
        }
        if (target.getRole() != Role.STUDENT) {
            throw ApiException.badRequest("Chỉ thêm được sinh viên vào nhóm");
        }
        if (teams.findFirstByMembersId(target.getId()).isPresent()) {
            throw ApiException.conflict(target.getFullName() + " đã thuộc một nhóm khác");
        }
        team.getMembers().add(target);
        teams.save(team);
        record(team, target, actor, MemberAction.ADDED);
    }

    @Transactional
    public void applyRemove(Team team, User removed, User actor) {
        if (team.getLeader().getId().equals(removed.getId())) {
            throw ApiException.conflict("Nhóm trưởng cần chuyển quyền trước khi rời nhóm");
        }
        if (team.getMembers().stream().noneMatch(m -> m.getId().equals(removed.getId()))) {
            throw ApiException.conflict(removed.getFullName() + " không còn thuộc nhóm");
        }
        team.getMembers().removeIf(m -> m.getId().equals(removed.getId()));
        teams.save(team);
        record(team, removed, actor, MemberAction.REMOVED);
    }

    @Transactional
    public void applyTransferLeader(Team team, User newLeader, User actor) {
        if (team.getMembers().stream().noneMatch(m -> m.getId().equals(newLeader.getId()))) {
            throw ApiException.conflict(newLeader.getFullName() + " không còn thuộc nhóm");
        }
        if (team.getLeader().getId().equals(newLeader.getId())) {
            throw ApiException.conflict(newLeader.getFullName() + " đã là nhóm trưởng");
        }
        team.setLeader(newLeader);
        teams.save(team);
        record(team, newLeader, actor, MemberAction.LEADER_CHANGED);
    }

    private void record(Team team, User member, User actor, MemberAction action) {
        TeamMemberHistory h = new TeamMemberHistory();
        h.setTeam(team);
        h.setMember(member);
        h.setActor(actor);
        h.setAction(action);
        memberHistory.save(h);
    }

    public boolean isInAnyTeam(User user) {
        return teams.findFirstByMembersId(user.getId()).isPresent();
    }

    public Team find(Long id) {
        return teams.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhóm"));
    }
}
