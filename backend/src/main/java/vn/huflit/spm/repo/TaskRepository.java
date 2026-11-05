package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.huflit.spm.domain.Task;
import vn.huflit.spm.domain.TaskStatus;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByMilestoneIdOrderByIdAsc(Long milestoneId);

    long countByMilestoneId(Long milestoneId);

    /** Tat ca task cua nhom (ke ca milestone da luu tru) de tinh dong gop. */
    @Query("select t from Task t where t.milestone.team.id = :teamId order by t.id")
    List<Task> findByTeam(@Param("teamId") Long teamId);

    // ----- Dem va tong trong so theo trang thai (tien do co xet trong so) -----
    @Query("select count(t) from Task t where t.milestone.id = :id and t.status in :statuses")
    long countInMilestone(@Param("id") Long milestoneId, @Param("statuses") Collection<TaskStatus> statuses);

    @Query("select coalesce(sum(t.weight), 0) from Task t where t.milestone.id = :id and t.status in :statuses")
    long weightInMilestone(@Param("id") Long milestoneId, @Param("statuses") Collection<TaskStatus> statuses);

    @Query("select count(t) from Task t where t.milestone.parent.id = :id and t.milestone.archived = false and t.status in :statuses")
    long countUnderMain(@Param("id") Long mainId, @Param("statuses") Collection<TaskStatus> statuses);

    @Query("select coalesce(sum(t.weight), 0) from Task t where t.milestone.parent.id = :id and t.milestone.archived = false and t.status in :statuses")
    long weightUnderMain(@Param("id") Long mainId, @Param("statuses") Collection<TaskStatus> statuses);

    @Query("select count(t) from Task t where t.milestone.team.id = :teamId and t.milestone.archived = false and t.status in :statuses")
    long countInTeam(@Param("teamId") Long teamId, @Param("statuses") Collection<TaskStatus> statuses);

    @Query("select coalesce(sum(t.weight), 0) from Task t where t.milestone.team.id = :teamId and t.milestone.archived = false and t.status in :statuses")
    long weightInTeam(@Param("teamId") Long teamId, @Param("statuses") Collection<TaskStatus> statuses);
}
