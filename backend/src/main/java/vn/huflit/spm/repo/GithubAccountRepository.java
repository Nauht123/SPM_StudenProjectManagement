package vn.huflit.spm.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.huflit.spm.domain.GithubAccount;

import java.util.List;
import java.util.Optional;

public interface GithubAccountRepository extends JpaRepository<GithubAccount, Long> {
    List<GithubAccount> findByTeamId(Long teamId);

    Optional<GithubAccount> findByTeamIdAndUserId(Long teamId, Long userId);
}
