package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.ChangeRequestStatus;
import vn.huflit.spm.domain.TeamChangeRequest;

import java.util.Collection;
import java.util.List;

public interface TeamChangeRequestRepository extends JpaRepository<TeamChangeRequest, Long> {
    List<TeamChangeRequest> findByTeamIdOrderByIdDesc(Long teamId);

    List<TeamChangeRequest> findByTeamIdAndStatus(Long teamId, ChangeRequestStatus status);

    List<TeamChangeRequest> findByTeamIdInOrderByIdDesc(Collection<Long> teamIds);

    List<TeamChangeRequest> findAllByOrderByIdDesc();

    List<TeamChangeRequest> findTop20ByTeamIdOrderByCreatedAtDesc(Long teamId);
}
