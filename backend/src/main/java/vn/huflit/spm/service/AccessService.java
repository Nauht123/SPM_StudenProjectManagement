package vn.huflit.spm.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.huflit.spm.domain.RegStatus;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.Team;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.repo.RegistrationRepository;

/** Tap trung toan bo quy tac phan quyen theo nhom/doi tuong (tranh lap if-else trong controller). */
@Service
public class AccessService {
    private final RegistrationRepository registrations;

    public AccessService(RegistrationRepository registrations) {
        this.registrations = registrations;
    }

    public User current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw ApiException.unauthorized("Chưa đăng nhập");
        }
        return user;
    }

    public boolean isMember(Team team, User user) {
        return team.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
    }

    public boolean isLeader(Team team, User user) {
        return team.getLeader().getId().equals(user.getId());
    }

    /** Giang vien huong dan = giang vien cua de tai da duoc duyet cho nhom. */
    public boolean isSupervisor(Team team, User user) {
        return registrations.findFirstByTeamIdAndStatus(team.getId(), RegStatus.APPROVED)
                .map(r -> r.getTopic().getLecturer().getId().equals(user.getId()))
                .orElse(false);
    }

    public void requireView(Team team) {
        User u = current();
        if (u.getRole() == Role.ADMIN || isMember(team, u) || isSupervisor(team, u)) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền xem nhóm này");
    }

    public void requireMember(Team team) {
        if (!isMember(team, current())) {
            throw ApiException.forbidden("Chỉ thành viên nhóm mới thực hiện được thao tác này");
        }
    }

    public void requireLeader(Team team) {
        if (!isLeader(team, current())) {
            throw ApiException.forbidden("Chỉ nhóm trưởng mới thực hiện được thao tác này");
        }
    }

    public void requireLeaderOrSupervisor(Team team) {
        User u = current();
        if (isLeader(team, u) || isSupervisor(team, u)) {
            return;
        }
        throw ApiException.forbidden("Chỉ nhóm trưởng hoặc giảng viên hướng dẫn mới thực hiện được thao tác này");
    }

    public void requireSupervisor(Team team) {
        User u = current();
        if (u.getRole() == Role.LECTURER && isSupervisor(team, u)) {
            return;
        }
        throw ApiException.forbidden("Chỉ giảng viên hướng dẫn của nhóm mới thực hiện được thao tác này");
    }
}
