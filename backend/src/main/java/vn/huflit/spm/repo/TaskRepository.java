package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.Task;
import vn.huflit.spm.domain.TaskStatus;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByMilestoneIdOrderByIdAsc(Long milestoneId);

    long countByMilestoneId(Long milestoneId);

    long countByMilestoneIdAndStatus(Long milestoneId, TaskStatus status);

    long countByMilestoneTeamId(Long teamId);

    long countByMilestoneTeamIdAndStatus(Long teamId, TaskStatus status);

    void deleteByMilestoneId(Long milestoneId);
}
