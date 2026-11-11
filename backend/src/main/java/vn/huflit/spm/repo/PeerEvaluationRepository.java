package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.PeerEvaluation;

import java.util.List;

public interface PeerEvaluationRepository extends JpaRepository<PeerEvaluation, Long> {
    List<PeerEvaluation> findByMilestoneId(Long milestoneId);

    List<PeerEvaluation> findByMilestoneIdAndEvaluatorId(Long milestoneId, Long evaluatorId);

    List<PeerEvaluation> findByMilestoneTeamId(Long teamId);
}
