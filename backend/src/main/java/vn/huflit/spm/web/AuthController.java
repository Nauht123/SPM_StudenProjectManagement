package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.AuthService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @GetMapping("/auth/me")
    public UserDto me() {
        return auth.me();
    }

    @GetMapping("/admin/users")
    public List<UserDto> users() {
        return auth.listUsers();
    }

    @PostMapping("/admin/users")
    public UserDto createUser(@Valid @RequestBody CreateUserRequest req) {
        return auth.createByAdmin(req);
    }
}
