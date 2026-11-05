package vn.huflit.spm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.Team;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.service.AccessService;
import vn.huflit.spm.service.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** Kiem tra quy tac phan quyen: giang vien khong duoc quan ly task, chi nhom truong va admin. */
class AccessServiceTest {
    private final AccessService access = new AccessService(mock(RegistrationRepository.class));

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private static void login(User u) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    private Team team(User leader, User member) {
        Team t = new Team();
        t.setId(1L);
        t.setLeader(leader);
        t.getMembers().add(leader);
        t.getMembers().add(member);
        return t;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leaderCanManageTasks() {
        User leader = user(1, Role.STUDENT);
        Team t = team(leader, user(2, Role.STUDENT));
        login(leader);
        assertDoesNotThrow(() -> access.requireLeaderOrAdmin(t));
    }

    @Test
    void adminCanManageTasks() {
        Team t = team(user(1, Role.STUDENT), user(2, Role.STUDENT));
        login(user(9, Role.ADMIN));
        assertDoesNotThrow(() -> access.requireLeaderOrAdmin(t));
    }

    @Test
    void lecturerCannotManageTasks() {
        Team t = team(user(1, Role.STUDENT), user(2, Role.STUDENT));
        login(user(7, Role.LECTURER));
        assertThrows(ApiException.class, () -> access.requireLeaderOrAdmin(t));
    }

    @Test
    void plainMemberCannotManageTasks() {
        User member = user(2, Role.STUDENT);
        Team t = team(user(1, Role.STUDENT), member);
        login(member);
        assertThrows(ApiException.class, () -> access.requireLeaderOrAdmin(t));
    }
}
