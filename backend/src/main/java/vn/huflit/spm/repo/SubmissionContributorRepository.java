package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.huflit.spm.domain.SubmissionContributor;

import java.util.List;

public interface SubmissionContributorRepository extends JpaRepository<SubmissionContributor, Long> {
    List<SubmissionContributor> findBySubmissionId(Long submissionId);

    @Query("select c from SubmissionContributor c where c.submission.milestone.team.id = :teamId")
    List<SubmissionContributor> findByTeam(@Param("teamId") Long teamId);
}
