package vn.huflit.spm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.Topic;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.repo.TopicRepository;
import vn.huflit.spm.repo.UserRepository;

/** Tao du lieu demo khi CSDL trong (tat bang SEED_DEMO_DATA=false khi trien khai that). */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final TopicRepository topics;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DataSeeder(UserRepository users, TopicRepository topics, PasswordEncoder encoder,
                      @Value("${app.seed-demo-data}") boolean enabled) {
        this.users = users;
        this.topics = topics;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled || users.count() > 0) {
            return;
        }
        make("admin", "admin123", "Quản trị viên", null, Role.ADMIN);
        User gv1 = make("gv.khoa", "gv123456", "Nguyễn Tấn Khoa", null, Role.LECTURER);
        User gv2 = make("gv.lan", "gv123456", "Trần Thị Lan", null, Role.LECTURER);
        for (int i = 1; i <= 6; i++) {
            make("sv0" + i, "sv123456", "Sinh viên " + i, "SV00" + i, Role.STUDENT);
        }
        topic(gv1, "Hệ thống quản lý đồ án sinh viên", "Xây dựng web quản lý vòng đời đồ án môn học.", 3);
        topic(gv1, "Ứng dụng đặt lịch hẹn trực tuyến", "Đặt/hủy lịch, quản lý khung giờ trống.", 2);
        topic(gv2, "Website thương mại điện tử", "Giỏ hàng, đơn hàng, thanh toán giả lập.", 2);
    }

    private User make(String username, String password, String fullName, String code, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setFullName(fullName);
        u.setStudentCode(code);
        u.setRole(role);
        return users.save(u);
    }

    private void topic(User lecturer, String title, String desc, int max) {
        Topic t = new Topic();
        t.setLecturer(lecturer);
        t.setTitle(title);
        t.setDescription(desc);
        t.setMaxGroups(max);
        t.setSemester("HK1 2026-2027");
        topics.save(t);
    }
}
