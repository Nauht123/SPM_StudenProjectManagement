package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.huflit.spm.domain.Submission;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByMilestoneIdOrderByVersionDesc(Long milestoneId);

    long countByMilestoneId(Long milestoneId);

    long countByMilestoneTeamIdAndLateTrue(Long teamId);

    @Query("select coalesce(max(s.version), 0) from Submission s where s.milestone.id = :milestoneId")
    int maxVersion(@Param("milestoneId") Long milestoneId);
}
