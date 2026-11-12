package vn.huflit.spm.service;

import java.time.LocalDateTime;
import java.util.List;

/** Cong truy cap GitHub REST API (tach rieng de unit test khong can mang). */
public interface GithubClient {
    record RepoInfo(String fullName, boolean isPrivate, String defaultBranch) {
    }

    record Commit(String sha, String authorLogin, String authorName, String message,
                  LocalDateTime committedAt, String url) {
    }

    /** state: OPEN, MERGED hoac CLOSED. */
    record Pull(int number, String title, String state, String authorLogin, LocalDateTime createdAt,
                LocalDateTime closedAt, LocalDateTime mergedAt, String url) {
    }

    RepoInfo verify(String owner, String repo, String token);

    /** Commit moi nhat (toi da 500); since = null nghia la lay tu dau. */
    List<Commit> commits(String owner, String repo, String token, LocalDateTime since);

    /** Pull request (moi trang thai), cap nhat gan day nhat truoc, toi da 300. */
    List<Pull> pulls(String owner, String repo, String token);
}
