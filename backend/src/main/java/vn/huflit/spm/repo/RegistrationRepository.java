package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.RegStatus;
import vn.huflit.spm.domain.Registration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Optional<Registration> findFirstByTeamIdAndStatus(Long teamId, RegStatus status);

    Optional<Registration> findFirstByTeamIdOrderByIdDesc(Long teamId);

    boolean existsByTeamIdAndStatusIn(Long teamId, Collection<RegStatus> statuses);

    long countByTopicIdAndStatus(Long topicId, RegStatus status);

    boolean existsByTopicId(Long topicId);

    List<Registration> findByTopicLecturerIdOrderByIdDesc(Long lecturerId);

    List<Registration> findByTopicLecturerIdAndStatus(Long lecturerId, RegStatus status);

    List<Registration> findByTeamIdOrderByIdDesc(Long teamId);
}
