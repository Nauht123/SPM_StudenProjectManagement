package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.Milestone;

import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByTeamIdOrderByDueDateAsc(Long teamId);

    List<Milestone> findTop20ByTeamIdOrderByCreatedAtDesc(Long teamId);

    List<Milestone> findByParentId(Long parentId);

    long countByParentId(Long parentId);

    long countByParentIdAndArchivedFalse(Long parentId);
}
