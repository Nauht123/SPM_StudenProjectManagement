package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.TaskHistory;

import java.util.List;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {
    List<TaskHistory> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);

    List<TaskHistory> findTop50ByTaskMilestoneTeamIdOrderByCreatedAtDesc(Long teamId);

    void deleteByTaskId(Long taskId);
}
