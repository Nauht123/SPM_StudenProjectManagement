package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.DecisionRequest;
import vn.huflit.spm.dto.Dtos.RegistrationDto;
import vn.huflit.spm.dto.Dtos.RegistrationRequest;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.TeamRepository;
import vn.huflit.spm.repo.TopicRepository;

import java.util.List;

@Service
public class RegistrationService {
    private final RegistrationRepository registrations;
    private final TopicRepository topics;
    private final TeamRepository teams;
    private final AccessService access;
    private final NotificationService notifications;
    private final Mapper mapper;

    public RegistrationService(RegistrationRepository registrations, TopicRepository topics, TeamRepository teams,
                               AccessService access, NotificationService notifications, Mapper mapper) {
        this.registrations = registrations;
        this.topics = topics;
        this.teams = teams;
        this.access = access;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    /** Nhom truong dang ky de tai cho nhom. */
    @Transactional
    public RegistrationDto register(RegistrationRequest req) {
        User u = access.current();
        Team team = teams.findFirstByMembersId(u.getId())
                .orElseThrow(() -> ApiException.badRequest("Bạn cần tạo hoặc tham gia một nhóm trước"));
        if (!team.getLeader().getId().equals(u.getId())) {
            throw ApiException.forbidden("Chỉ nhóm trưởng mới đăng ký đề tài");
        }
        Topic topic = topics.findById(req.topicId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đề tài"));
        if (topic.getStatus() != TopicStatus.OPEN) {
            throw ApiException.conflict("Đề tài đã đóng đăng ký");
        }
        if (registrations.existsByTeamIdAndStatusIn(team.getId(), List.of(RegStatus.PENDING, RegStatus.APPROVED))) {
            throw ApiException.conflict("Nhóm đã có đăng ký đang chờ duyệt hoặc đã được duyệt");
        }
        if (registrations.countByTopicIdAndStatus(topic.getId(), RegStatus.APPROVED) >= topic.getMaxGroups()) {
            throw ApiException.conflict("Đề tài đã đủ số nhóm tối đa");
        }
        Registration r = new Registration();
        r.setTeam(team);
        r.setTopic(topic);
        r.setStatus(RegStatus.PENDING);
        registrations.save(r);
        notifications.notify(topic.getLecturer(),
                "Nhóm \"" + team.getName() + "\" đăng ký đề tài \"" + topic.getTitle() + "\"");
        return mapper.registration(r);
    }

    /** Nhom truong huy dang ky dang cho duyet. */
    @Transactional
    public void cancel(Long id) {
        Registration r = find(id);
        access.requireLeader(r.getTeam());
        if (r.getStatus() != RegStatus.PENDING) {
            throw ApiException.conflict("Chỉ hủy được đăng ký đang chờ duyệt");
        }
        registrations.delete(r);
    }

    /** Giang vien duyet / tu choi. Kiem tra lai suc chua tai thoi diem duyet. */
    @Transactional
    public RegistrationDto decide(Long id, DecisionRequest req) {
        User u = access.current();
        Registration r = find(id);
        Topic topic = r.getTopic();
        if (!topic.getLecturer().getId().equals(u.getId())) {
            throw ApiException.forbidden("Bạn không phải giảng viên phụ trách đề tài này");
        }
        if (r.getStatus() != RegStatus.PENDING) {
            throw ApiException.conflict("Đăng ký này đã được xử lý");
        }
        if (req.approve()) {
            if (registrations.countByTopicIdAndStatus(topic.getId(), RegStatus.APPROVED) >= topic.getMaxGroups()) {
                throw ApiException.conflict("Đề tài đã đủ số nhóm tối đa, không thể duyệt thêm");
            }
            r.setStatus(RegStatus.APPROVED);
        } else {
            r.setStatus(RegStatus.REJECTED);
        }
        r.setNote(req.note());
        registrations.save(r);
        String msg = (req.approve() ? "Đề tài \"" : "Đăng ký đề tài \"") + topic.getTitle()
                + (req.approve() ? "\" đã được duyệt cho nhóm của bạn" : "\" bị từ chối")
                + (req.note() == null || req.note().isBlank() ? "" : ". Ghi chú: " + req.note());
        notifications.notifyTeam(r.getTeam(), msg, null);
        return mapper.registration(r);
    }

    public List<RegistrationDto> forLecturer() {
        return registrations.findByTopicLecturerIdOrderByIdDesc(access.current().getId()).stream()
                .map(mapper::registration).toList();
    }

    public List<RegistrationDto> forMyTeam() {
        User u = access.current();
        return teams.findFirstByMembersId(u.getId())
                .map(t -> registrations.findByTeamIdOrderByIdDesc(t.getId()).stream().map(mapper::registration).toList())
                .orElse(List.of());
    }

    private Registration find(Long id) {
        return registrations.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đăng ký"));
    }
}
