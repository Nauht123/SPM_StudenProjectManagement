package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.ContributionInsightService;
import vn.huflit.spm.service.ContributionService;
import vn.huflit.spm.service.ProgressReportService;
import vn.huflit.spm.service.DashboardService;
import vn.huflit.spm.service.TeamService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TeamController {
    private final TeamService teams;
    private final DashboardService dashboard;
    private final ContributionService contributions;
    private final ContributionInsightService insights;
    private final ProgressReportService progressReports;

    public TeamController(TeamService teams, DashboardService dashboard, ContributionService contributions,
                          ContributionInsightService insights, ProgressReportService progressReports) {
        this.progressReports = progressReports;
        this.teams = teams;
        this.dashboard = dashboard;
        this.contributions = contributions;
        this.insights = insights;
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

    /** Dong gop ca nhan: giang vien/admin/nhom truong xem ca nhom, thanh vien chi xem cua minh. */
    @GetMapping("/teams/{id}/contributions")
    public ContributionReportDto contributions(@PathVariable Long id) {
        return contributions.report(id);
    }

    @GetMapping("/dashboard/teams")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public List<ProgressDto> overview() {
        return dashboard.overview();
    }

    // ----- Dashboard dong gop (giai doan 2) -----
    /** Tong hop dong gop, hieu suat deadline va canh bao cua cac nhom (giang vien / admin). */
    @GetMapping("/dashboard/contributions")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    public ContributionOverviewDto contributionOverview() {
        return insights.overview();
    }

    /** Chi tiet mot thanh vien: task, deadline, sua lai, lich su nop. Thanh vien chi xem cua minh. */
    @GetMapping("/teams/{id}/members/{userId}/contribution")
    public MemberDetailDto memberContribution(@PathVariable Long id, @PathVariable Long userId) {
        return insights.memberDetail(id, userId);
    }

    /** Xuat Excel bao cao dong gop ca nhom (giang vien, nhom truong, admin). */
    @GetMapping("/teams/{id}/contributions/export")
    public ResponseEntity<byte[]> exportTeam(@PathVariable Long id) {
        return xlsx(insights.exportTeam(id), "dong-gop-nhom-" + id + ".xlsx");
    }

    /** Xuat Excel bao cao ca nhan cua mot thanh vien (giang vien, nhom truong, admin). */
    @GetMapping("/teams/{id}/members/{userId}/report")
    public ResponseEntity<byte[]> exportMember(@PathVariable Long id, @PathVariable Long userId) {
        return xlsx(insights.exportMember(id, userId), "bao-cao-ca-nhan-" + id + "-" + userId + ".xlsx");
    }

    /** Xuat Excel bao cao tien do cua nhom (giang vien, nhom truong, admin). */
    @GetMapping("/teams/{id}/progress/export")
    public ResponseEntity<byte[]> exportProgress(@PathVariable Long id) {
        return xlsx(progressReports.export(id), "tien-do-nhom-" + id + ".xlsx");
    }

    private static ResponseEntity<byte[]> xlsx(byte[] body, String fileName) {
        ContentDisposition cd = ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
