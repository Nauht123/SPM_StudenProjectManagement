package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.TeamMemberHistory;

import java.util.List;

public interface TeamMemberHistoryRepository extends JpaRepository<TeamMemberHistory, Long> {
    List<TeamMemberHistory> findByTeamIdOrderByCreatedAtDescIdDesc(Long teamId);
}
