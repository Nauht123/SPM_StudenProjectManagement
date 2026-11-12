package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.GithubPullRequest;

import java.util.List;

public interface GithubPullRequestRepository extends JpaRepository<GithubPullRequest, Long> {
    List<GithubPullRequest> findByTeamId(Long teamId);

    List<GithubPullRequest> findTop20ByTeamIdOrderByCreatedAtDesc(Long teamId);

    void deleteByTeamId(Long teamId);
}
