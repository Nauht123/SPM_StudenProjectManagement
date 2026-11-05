package vn.huflit.spm.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.UserRepository;
import vn.huflit.spm.security.JwtService;

import java.util.List;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Mapper mapper;
    private final AccessService access;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, Mapper mapper,
                       AccessService access) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mapper = mapper;
        this.access = access;
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByUsername(req.username().trim())
                .filter(u -> encoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> ApiException.unauthorized("Sai tên đăng nhập hoặc mật khẩu"));
        return new AuthResponse(jwt.generate(user), mapper.user(user));
    }

    /** Sinh vien tu dang ky tai khoan. Tai khoan giang vien/quan tri do Admin tao. */
    public AuthResponse register(RegisterRequest req) {
        User user = create(req.username(), req.password(), req.fullName(), req.studentCode(), Role.STUDENT);
        return new AuthResponse(jwt.generate(user), mapper.user(user));
    }

    public UserDto createByAdmin(CreateUserRequest req) {
        if (req.role() == Role.STUDENT && (req.studentCode() == null || req.studentCode().isBlank())) {
            throw ApiException.badRequest("Sinh viên phải có mã số sinh viên");
        }
        return mapper.user(create(req.username(), req.password(), req.fullName(), req.studentCode(), req.role()));
    }

    public List<UserDto> listUsers() {
        return users.findAll().stream().map(mapper::user).toList();
    }

    public UserDto me() {
        return mapper.user(access.current());
    }

    private User create(String username, String password, String fullName, String studentCode, Role role) {
        String uname = username.trim();
        if (users.existsByUsername(uname)) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        }
        String code = studentCode == null || studentCode.isBlank() ? null : studentCode.trim();
        if (code != null && users.existsByStudentCode(code)) {
            throw ApiException.conflict("Mã số sinh viên đã tồn tại");
        }
        User u = new User();
        u.setUsername(uname);
        u.setPassword(encoder.encode(password));
        u.setFullName(fullName.trim());
        u.setStudentCode(role == Role.STUDENT ? code : null);
        u.setRole(role);
        return users.save(u);
    }
}
