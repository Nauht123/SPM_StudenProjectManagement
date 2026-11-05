package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.DashboardService;
import vn.huflit.spm.service.TeamService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TeamController {
    private final TeamService teams;
    private final DashboardService dashboard;

    public TeamController(TeamService teams, DashboardService dashboard) {
        this.teams = teams;
        this.dashboard = dashboard;
    }

    @GetMapping("/teams/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<TeamDto> myTeam() {
        return teams.myTeam().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/teams")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public List<TeamDto> list() {
        return teams.list();
    }

    @GetMapping("/teams/{id}")
    public TeamDto get(@PathVariable Long id) {
        return teams.get(id);
    }

    @PostMapping("/teams")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamDto create(@Valid @RequestBody TeamRequest req) {
        return teams.create(req);
    }

    @PostMapping("/teams/{id}/members")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamDto addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest req) {
        return teams.addMember(id, req);
    }

    @DeleteMapping("/teams/{id}/members/{userId}")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamDto removeMember(@PathVariable Long id, @PathVariable Long userId) {
        return teams.removeMember(id, userId);
    }

    @GetMapping("/teams/{id}/progress")
    public ProgressDto progress(@PathVariable Long id) {
        return dashboard.teamProgress(id);
    }

    @GetMapping("/dashboard/teams")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public List<ProgressDto> overview() {
        return dashboard.overview();
    }
}
