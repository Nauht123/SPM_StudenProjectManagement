package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.Feedback;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);

    List<Feedback> findTop20BySubmissionMilestoneTeamIdOrderByCreatedAtDesc(Long teamId);
}
