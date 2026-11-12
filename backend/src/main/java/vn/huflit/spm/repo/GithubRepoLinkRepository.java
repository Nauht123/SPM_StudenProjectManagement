package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.GithubRepoLink;

import java.util.Optional;

public interface GithubRepoLinkRepository extends JpaRepository<GithubRepoLink, Long> {
    Optional<GithubRepoLink> findByTeamId(Long teamId);
}
