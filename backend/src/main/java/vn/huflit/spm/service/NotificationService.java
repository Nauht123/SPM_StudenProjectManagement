package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.Notification;
import vn.huflit.spm.domain.Team;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.dto.Dtos.NotificationDto;
import vn.huflit.spm.repo.NotificationRepository;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final AccessService access;
    private final Mapper mapper;

    public NotificationService(NotificationRepository notifications, AccessService access, Mapper mapper) {
        this.notifications = notifications;
        this.access = access;
        this.mapper = mapper;
    }

    public void notify(User user, String message) {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message);
        notifications.save(n);
    }

    /** Gui thong bao cho moi thanh vien nhom, tru nguoi thuc hien (neu co). */
    public void notifyTeam(Team team, String message, User except) {
        for (User m : team.getMembers()) {
            if (except == null || !m.getId().equals(except.getId())) {
                notify(m, message);
            }
        }
    }

    public List<NotificationDto> mine() {
        return notifications.findByUserIdOrderByCreatedAtDesc(access.current().getId()).stream()
                .map(mapper::notification).toList();
    }

    public long unreadCount() {
        return notifications.countByUserIdAndSeenFalse(access.current().getId());
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (!n.getUser().getId().equals(access.current().getId())) {
            throw ApiException.forbidden("Không có quyền");
        }
        n.setSeen(true);
        notifications.save(n);
    }

    @Transactional
    public void markAllRead() {
        List<Notification> list = notifications.findByUserIdOrderByCreatedAtDesc(access.current().getId());
        list.forEach(n -> n.setSeen(true));
        notifications.saveAll(list);
    }
}
