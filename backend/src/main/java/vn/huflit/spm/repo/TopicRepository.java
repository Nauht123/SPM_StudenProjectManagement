package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.Topic;

import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    List<Topic> findByLecturerIdOrderByIdDesc(Long lecturerId);

    List<Topic> findAllByOrderByIdDesc();
}
