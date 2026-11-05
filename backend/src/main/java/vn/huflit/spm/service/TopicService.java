package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.Topic;
import vn.huflit.spm.domain.TopicStatus;
import vn.huflit.spm.domain.User;
import vn.huflit.spm.dto.Dtos.TopicDto;
import vn.huflit.spm.dto.Dtos.TopicRequest;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.TopicRepository;

import java.util.List;

@Service
public class TopicService {
    private final TopicRepository topics;
    private final RegistrationRepository registrations;
    private final AccessService access;
    private final Mapper mapper;

    public TopicService(TopicRepository topics, RegistrationRepository registrations, AccessService access,
                        Mapper mapper) {
        this.topics = topics;
        this.registrations = registrations;
        this.access = access;
        this.mapper = mapper;
    }

    public List<TopicDto> listAll() {
        return topics.findAllByOrderByIdDesc().stream().map(mapper::topic).toList();
    }

    public List<TopicDto> listMine() {
        return topics.findByLecturerIdOrderByIdDesc(access.current().getId()).stream().map(mapper::topic).toList();
    }

    @Transactional
    public TopicDto create(TopicRequest req) {
        Topic t = new Topic();
        t.setLecturer(access.current());
        apply(t, req);
        return mapper.topic(topics.save(t));
    }

    @Transactional
    public TopicDto update(Long id, TopicRequest req) {
        Topic t = owned(id);
        apply(t, req);
        return mapper.topic(topics.save(t));
    }

    @Transactional
    public void delete(Long id) {
        Topic t = owned(id);
        if (registrations.existsByTopicId(id)) {
            throw ApiException.conflict("Đề tài đã có nhóm đăng ký, không thể xóa. Hãy đóng đề tài thay vì xóa.");
        }
        topics.delete(t);
    }

    private Topic owned(Long id) {
        Topic t = topics.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đề tài"));
        User u = access.current();
        if (!t.getLecturer().getId().equals(u.getId())) {
            throw ApiException.forbidden("Bạn không phải giảng viên phụ trách đề tài này");
        }
        return t;
    }

    private void apply(Topic t, TopicRequest req) {
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setMaxGroups(req.maxGroups());
        t.setSemester(req.semester());
        t.setStatus(req.status() == null ? TopicStatus.OPEN : req.status());
    }
}
