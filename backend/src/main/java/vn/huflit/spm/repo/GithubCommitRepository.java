package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.GithubCommit;

import java.util.List;

public interface GithubCommitRepository extends JpaRepository<GithubCommit, Long> {
    List<GithubCommit> findByTeamId(Long teamId);

    List<GithubCommit> findTop30ByTeamIdOrderByCommittedAtDesc(Long teamId);

    void deleteByTeamId(Long teamId);
}
