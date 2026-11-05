package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.ProgressDto;
import vn.huflit.spm.repo.*;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {
    private final TeamService teamService;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final SubmissionRepository submissions;
    private final RegistrationRepository registrations;
    private final AccessService access;

    public DashboardService(TeamService teamService, MilestoneRepository milestones, TaskRepository tasks,
                            SubmissionRepository submissions, RegistrationRepository registrations,
                            AccessService access) {
        this.teamService = teamService;
        this.milestones = milestones;
        this.tasks = tasks;
        this.submissions = submissions;
        this.registrations = registrations;
        this.access = access;
    }

    public ProgressDto teamProgress(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        return compute(team);
    }

    /** Dashboard cho giang vien (cac nhom minh huong dan) hoac admin (tat ca nhom). */
    public List<ProgressDto> overview() {
        return teamService.list().stream()
                .map(dto -> compute(teamService.find(dto.id())))
                .toList();
    }

    /** Tien do tinh theo trong so Task (task bi huy / de xuat chua duyet / milestone luu tru khong tinh). */
    private ProgressDto compute(Team team) {
        long total = tasks.countInTeam(team.getId(), TaskStatus.ACTIVE);
        long done = tasks.countInTeam(team.getId(), List.of(TaskStatus.DONE));
        long totalWeight = tasks.weightInTeam(team.getId(), TaskStatus.ACTIVE);
        long doneWeight = tasks.weightInTeam(team.getId(), List.of(TaskStatus.DONE));
        int percent = Mapper.weightedPercent(doneWeight, totalWeight);
        long overdue = milestones.findByTeamIdOrderByDueDateAsc(team.getId()).stream()
                .filter(m -> m.getParent() == null && !m.isArchived() && m.getDueDate().isBefore(LocalDate.now())
                        && submissions.countByMilestoneId(m.getId()) == 0)
                .count();
        long late = submissions.countByMilestoneTeamIdAndLateTrue(team.getId());
        String topicTitle = registrations.findFirstByTeamIdAndStatus(team.getId(), RegStatus.APPROVED)
                .map(r -> r.getTopic().getTitle()).orElse(null);
        boolean atRisk = overdue > 0;
        return new ProgressDto(team.getId(), team.getName(), topicTitle, total, done, percent, overdue, late, atRisk,
                totalWeight, doneWeight);
    }
}
