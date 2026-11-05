package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.TaskAssignmentHistory;

import java.util.List;
import java.util.Optional;

public interface TaskAssignmentHistoryRepository extends JpaRepository<TaskAssignmentHistory, Long> {
    List<TaskAssignmentHistory> findByTaskIdOrderByAssignedAtAscIdAsc(Long taskId);

    Optional<TaskAssignmentHistory> findFirstByTaskIdAndEndedAtIsNullOrderByIdDesc(Long taskId);

    void deleteByTaskId(Long taskId);
}
