package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.GithubService;

/** Tich hop GitHub cua nhom: lien ket repo, dong bo commit/PR, khai bao tai khoan GitHub thanh vien. */
@RestController
@RequestMapping("/api/teams/{teamId}/github")
public class GithubController {
    private final GithubService github;

    public GithubController(GithubService github) {
        this.github = github;
    }

    @GetMapping
    public GithubOverviewDto overview(@PathVariable Long teamId) {
        return github.overview(teamId);
    }

    @PutMapping
    public GithubOverviewDto link(@PathVariable Long teamId, @Valid @RequestBody GithubLinkRequest req) {
        return github.link(teamId, req);
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(@PathVariable Long teamId) {
        github.unlink(teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public GithubOverviewDto sync(@PathVariable Long teamId) {
        return github.sync(teamId);
    }

    @PutMapping("/accounts")
    public GithubOverviewDto account(@PathVariable Long teamId, @Valid @RequestBody GithubAccountRequest req) {
        return github.mapAccount(teamId, req);
    }
}
