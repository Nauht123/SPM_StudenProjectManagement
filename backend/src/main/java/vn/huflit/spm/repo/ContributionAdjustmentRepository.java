package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.ContributionAdjustment;

import java.util.List;

public interface ContributionAdjustmentRepository extends JpaRepository<ContributionAdjustment, Long> {
    List<ContributionAdjustment> findByTeamIdOrderByCreatedAtDescIdDesc(Long teamId);
}
