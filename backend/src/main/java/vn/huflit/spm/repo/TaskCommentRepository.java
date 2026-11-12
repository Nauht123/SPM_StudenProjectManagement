package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.TaskComment;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);

    List<TaskComment> findTop50ByTaskMilestoneTeamIdOrderByCreatedAtDesc(Long teamId);

    void deleteByTaskId(Long taskId);
}
