package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.DecisionRequest;
import vn.huflit.spm.dto.Dtos.TeamChangeRequestDto;
import vn.huflit.spm.dto.Dtos.TeamChangeRequestRequest;
import vn.huflit.spm.service.TeamChangeService;

import java.util.List;

/** Yeu cau thay doi thanh vien / nhom truong sau khi de tai duoc duyet (giang vien huong dan duyet). */
@RestController
@RequestMapping("/api")
public class TeamChangeController {
    private final TeamChangeService changes;

    public TeamChangeController(TeamChangeService changes) {
        this.changes = changes;
    }

    @PostMapping("/teams/{id}/change-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamChangeRequestDto create(@PathVariable Long id, @Valid @RequestBody TeamChangeRequestRequest req) {
        return changes.create(id, req);
    }

    @GetMapping("/teams/{id}/change-requests")
    public List<TeamChangeRequestDto> forTeam(@PathVariable Long id) {
        return changes.forTeam(id);
    }

    @PostMapping("/change-requests/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamChangeRequestDto cancel(@PathVariable Long id) {
        return changes.cancel(id);
    }

    @GetMapping("/change-requests/lecturer")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public List<TeamChangeRequestDto> forLecturer() {
        return changes.forLecturer();
    }

    @PostMapping("/change-requests/{id}/decision")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public TeamChangeRequestDto decide(@PathVariable Long id, @RequestBody DecisionRequest req) {
        return changes.decide(id, req);
    }
}
