package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.huflit.spm.domain.SubmissionOutcome;
import vn.huflit.spm.domain.TaskSubmissionHistory;

import java.util.List;
import java.util.Optional;

public interface TaskSubmissionHistoryRepository extends JpaRepository<TaskSubmissionHistory, Long> {
    List<TaskSubmissionHistory> findByTaskIdOrderByAttemptNoAsc(Long taskId);

    Optional<TaskSubmissionHistory> findFirstByTaskIdAndOutcomeOrderByAttemptNoDesc(Long taskId, SubmissionOutcome outcome);

    @Query("select coalesce(max(h.attemptNo), 0) from TaskSubmissionHistory h where h.task.id = :taskId")
    int maxAttempt(@Param("taskId") Long taskId);

    @Query("select h from TaskSubmissionHistory h where h.task.milestone.team.id = :teamId")
    List<TaskSubmissionHistory> findByTeam(@Param("teamId") Long teamId);
}
