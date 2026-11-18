package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.DecisionRequest;
import vn.huflit.spm.dto.Dtos.TeamChangeRequestDto;
import vn.huflit.spm.dto.Dtos.TeamChangeRequestRequest;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.TeamChangeRequestRepository;
import vn.huflit.spm.repo.UserRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thay doi nhom sau khi de tai da duoc duyet phai qua giang vien huong dan:
 * - Nhom truong gui yeu cau them / xoa / thay thanh vien hoac chuyen quyen nhom truong.
 * - Thanh vien chi duoc gui yeu cau xin roi nhom (xoa chinh minh).
 * - Chi giang vien huong dan (hoac admin) duyet; khi duyet he thong kiem tra lai moi dieu kien roi moi ap dung.
 * Truoc khi de tai duoc duyet, nhom truong van them/xoa truc tiep (TeamService).
 */
@Service
public class TeamChangeService {
    private final TeamChangeRequestRepository requests;
    private final TeamService teamService;
    private final TaskService taskService;
    private final UserRepository users;
    private final RegistrationRepository registrations;
    private final AccessService access;
    private final NotificationService notifications;
    private final Mapper mapper;

    public TeamChangeService(TeamChangeRequestRepository requests, TeamService teamService, TaskService taskService,
                             UserRepository users, RegistrationRepository registrations, AccessService access,
                             NotificationService notifications, Mapper mapper) {
        this.requests = requests;
        this.teamService = teamService;
        this.taskService = taskService;
        this.users = users;
        this.registrations = registrations;
        this.access = access;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ gui yeu cau
    @Transactional
    public TeamChangeRequestDto create(Long teamId, TeamChangeRequestRequest req) {
        Team team = teamService.find(teamId);
        User me = access.current();
        if (!access.isMember(team, me)) {
            throw ApiException.forbidden("Chỉ thành viên của nhóm mới gửi được yêu cầu");
        }
        Registration reg = registrations.findFirstByTeamIdAndStatus(teamId, RegStatus.APPROVED)
                .orElseThrow(() -> ApiException.conflict(
                        "Nhóm chưa được duyệt đề tài nên nhóm trưởng thêm/xóa thành viên trực tiếp, không cần yêu cầu"));
        boolean leader = access.isLeader(team, me);
        if (!leader && !(req.type() == TeamChangeType.REMOVE_MEMBER && me.getId().equals(req.targetMemberId()))) {
            throw ApiException.forbidden("Thành viên chỉ được gửi yêu cầu xin rời nhóm của chính mình");
        }

        User target = null;
        User newMember = null;
        switch (req.type()) {
            case ADD_MEMBER -> newMember = findNewMember(req.newMemberCode());
            case REMOVE_MEMBER -> target = memberOf(team, req.targetMemberId());
            case REPLACE_MEMBER -> {
                target = memberOf(team, req.targetMemberId());
                newMember = findNewMember(req.newMemberCode());
            }
            case TRANSFER_LEADER -> target = memberOf(team, req.targetMemberId());
        }
        checkRequest(team, req.type(), target, newMember);

        for (TeamChangeRequest p : requests.findByTeamIdAndStatus(teamId, ChangeRequestStatus.PENDING)) {
            if (p.getType() == req.type() && sameUser(p.getTargetMember(), target) && sameUser(p.getNewMember(), newMember)) {
                throw ApiException.conflict("Đã có một yêu cầu giống hệt đang chờ giảng viên duyệt");
            }
        }
        TeamChangeRequest r = new TeamChangeRequest();
        r.setTeam(team);
        r.setType(req.type());
        r.setRequestedBy(me);
        r.setTargetMember(target);
        r.setNewMember(newMember);
        r.setReason(req.reason().trim());
        requests.save(r);
        notifications.notify(reg.getTopic().getLecturer(), "Nhóm \"" + team.getName() + "\" gửi yêu cầu: " + describe(r));
        return mapper.changeRequest(r);
    }

    @Transactional
    public TeamChangeRequestDto cancel(Long id) {
        TeamChangeRequest r = find(id);
        if (!r.getRequestedBy().getId().equals(access.current().getId())) {
            throw ApiException.forbidden("Chỉ người gửi mới hủy được yêu cầu");
        }
        requirePending(r);
        r.setStatus(ChangeRequestStatus.CANCELLED);
        r.setDecidedAt(LocalDateTime.now());
        return mapper.changeRequest(requests.save(r));
    }

    // ------------------------------------------------------------------ duyet
    @Transactional
    public TeamChangeRequestDto decide(Long id, DecisionRequest req) {
        TeamChangeRequest r = find(id);
        Team team = r.getTeam();
        access.requireSupervisorOrAdmin(team);
        requirePending(r);
        User decider = access.current();
        String note = req.note() == null ? null : req.note().trim();
        if (!req.approve() && (note == null || note.isEmpty())) {
            throw ApiException.badRequest("Vui lòng ghi lý do từ chối");
        }
        if (req.approve()) {
            apply(team, r, decider);
            r.setStatus(ChangeRequestStatus.APPROVED);
        } else {
            r.setStatus(ChangeRequestStatus.REJECTED);
        }
        r.setDecidedBy(decider);
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionNote(note == null || note.isEmpty() ? null : note);
        requests.save(r);

        String verdict = req.approve() ? "đã được duyệt" : "bị từ chối";
        String text = "Yêu cầu \"" + describe(r) + "\" " + verdict + (r.getDecisionNote() == null ? "" : ". Ghi chú: " + r.getDecisionNote());
        for (User u : team.getMembers()) {
            notifications.notify(u, text);
        }
        for (User u : Arrays.asList(r.getTargetMember(), r.getNewMember(), r.getRequestedBy())) { // co phan tu null
            if (u != null && team.getMembers().stream().noneMatch(m -> m.getId().equals(u.getId()))) {
                notifications.notify(u, text); // nguoi vua roi nhom / vua duoc them van duoc bao
            }
        }
        return mapper.changeRequest(r);
    }

    /** Ap dung thay doi sau khi kiem tra lai (tinh trang nhom co the da doi tu luc gui yeu cau). */
    private void apply(Team team, TeamChangeRequest r, User decider) {
        User target = r.getTargetMember();
        User newMember = r.getNewMember();
        checkRequest(team, r.getType(), target, newMember);
        switch (r.getType()) {
            case ADD_MEMBER -> teamService.applyAdd(team, newMember, decider);
            case REMOVE_MEMBER -> {
                taskService.releaseOpenTasksOf(team, target, decider);
                teamService.applyRemove(team, target, decider);
            }
            case REPLACE_MEMBER -> {
                taskService.releaseOpenTasksOf(team, target, decider);
                teamService.applyRemove(team, target, decider);
                teamService.applyAdd(team, newMember, decider);
            }
            case TRANSFER_LEADER -> teamService.applyTransferLeader(team, target, decider);
        }
    }

    /** Dieu kien hop le cua yeu cau tai thoi diem gui va thoi diem duyet. */
    private void checkRequest(Team team, TeamChangeType type, User target, User newMember) {
        boolean targetIsLeader = target != null && team.getLeader().getId().equals(target.getId());
        switch (type) {
            case REMOVE_MEMBER, REPLACE_MEMBER -> {
                if (targetIsLeader) {
                    throw ApiException.badRequest("Nhóm trưởng cần chuyển quyền nhóm trưởng trước khi rời nhóm hoặc bị thay");
                }
            }
            case TRANSFER_LEADER -> {
                if (targetIsLeader) {
                    throw ApiException.badRequest("Thành viên này đã là nhóm trưởng");
                }
            }
            case ADD_MEMBER -> {
                if (team.getMembers().size() >= TeamService.MAX_MEMBERS) {
                    throw ApiException.conflict("Nhóm đã đủ " + TeamService.MAX_MEMBERS + " thành viên");
                }
            }
        }
        if (newMember != null) {
            if (team.getMembers().stream().anyMatch(m -> m.getId().equals(newMember.getId()))) {
                throw ApiException.conflict(newMember.getFullName() + " đã thuộc nhóm này");
            }
            if (teamService.isInAnyTeam(newMember)) {
                throw ApiException.conflict(newMember.getFullName() + " đã thuộc một nhóm khác");
            }
        }
    }

    // ------------------------------------------------------------------ xem
    public List<TeamChangeRequestDto> forTeam(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        return requests.findByTeamIdOrderByIdDesc(teamId).stream().map(mapper::changeRequest).toList();
    }

    /** Giang vien: yeu cau cua cac nhom minh huong dan. Admin: tat ca. */
    public List<TeamChangeRequestDto> forLecturer() {
        User u = access.current();
        if (u.getRole() == Role.ADMIN) {
            return requests.findAllByOrderByIdDesc().stream().map(mapper::changeRequest).toList();
        }
        Set<Long> teamIds = registrations.findByTopicLecturerIdAndStatus(u.getId(), RegStatus.APPROVED).stream()
                .map(reg -> reg.getTeam().getId()).collect(Collectors.toSet());
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return requests.findByTeamIdInOrderByIdDesc(teamIds).stream().map(mapper::changeRequest).toList();
    }

    // ------------------------------------------------------------------ helpers
    public static String describe(TeamChangeRequest r) {
        String target = r.getTargetMember() == null ? "" : r.getTargetMember().getFullName();
        String added = r.getNewMember() == null ? "" : r.getNewMember().getFullName();
        boolean self = r.getTargetMember() != null && r.getTargetMember().getId().equals(r.getRequestedBy().getId());
        return switch (r.getType()) {
            case ADD_MEMBER -> "thêm " + added + " vào nhóm";
            case REMOVE_MEMBER -> self ? target + " xin rời nhóm" : "xóa " + target + " khỏi nhóm";
            case REPLACE_MEMBER -> "thay " + target + " bằng " + added;
            case TRANSFER_LEADER -> "chuyển quyền nhóm trưởng cho " + target;
        };
    }

    private User findNewMember(String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Vui lòng nhập MSSV của sinh viên mới");
        }
        return users.findByStudentCode(code.trim())
                .filter(s -> s.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sinh viên với MSSV này"));
    }

    private User memberOf(Team team, Long userId) {
        if (userId == null) {
            throw ApiException.badRequest("Vui lòng chọn thành viên");
        }
        return team.getMembers().stream().filter(m -> m.getId().equals(userId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Thành viên không thuộc nhóm"));
    }

    private static boolean sameUser(User a, User b) {
        return Objects.equals(a == null ? null : a.getId(), b == null ? null : b.getId());
    }

    private void requirePending(TeamChangeRequest r) {
        if (r.getStatus() != ChangeRequestStatus.PENDING) {
            throw ApiException.conflict("Yêu cầu này đã được xử lý");
        }
    }

    private TeamChangeRequest find(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
    }
}
