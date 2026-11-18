package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.Team;
import vn.huflit.spm.dto.Dtos.MilestoneDto;
import vn.huflit.spm.dto.Dtos.ProgressDto;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.TaskRepository;

import java.time.LocalDateTime;
import java.util.List;

/** Xuat bao cao tien do (Excel) cho giang vien huong dan, nhom truong va admin. */
@Service
public class ProgressReportService {
    private final TeamService teamService;
    private final DashboardService dashboard;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final AccessService access;
    private final Mapper mapper;

    public ProgressReportService(TeamService teamService, DashboardService dashboard, MilestoneRepository milestones,
                                 TaskRepository tasks, AccessService access, Mapper mapper) {
        this.teamService = teamService;
        this.dashboard = dashboard;
        this.milestones = milestones;
        this.tasks = tasks;
        this.access = access;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public byte[] export(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        access.requireExport(team);
        ProgressDto progress = dashboard.teamProgress(teamId);
        List<MilestoneDto> ms = milestones.findByTeamIdOrderByDueDateAsc(teamId).stream().map(mapper::milestone).toList();
        return ProgressExport.build(progress, ms, tasks.findByTeam(teamId), LocalDateTime.now());
    }
}
