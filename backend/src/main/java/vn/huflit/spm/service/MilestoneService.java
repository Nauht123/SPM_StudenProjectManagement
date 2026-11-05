package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.*;

import java.util.List;

/** Milestone va Task (Kanban) cua mot nhom. */
@Service
public class MilestoneService {
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final SubmissionRepository submissions;
    private final RegistrationRepository registrations;
    private final TeamService teamService;
    private final AccessService access;
    private final NotificationService notifications;
    private final Mapper mapper;

    public MilestoneService(MilestoneRepository milestones, TaskRepository tasks, SubmissionRepository submissions,
                            RegistrationRepository registrations, TeamService teamService, AccessService access,
                            NotificationService notifications, Mapper mapper) {
        this.milestones = milestones;
        this.tasks = tasks;
        this.submissions = submissions;
        this.registrations = registrations;
        this.teamService = teamService;
        this.access = access;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    // ---------------- Milestone ----------------
    public List<MilestoneDto> list(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        return milestones.findByTeamIdOrderByDueDateAsc(teamId).stream().map(mapper::milestone).toList();
    }

    public MilestoneDto get(Long id) {
        Milestone m = findMilestone(id);
        access.requireView(m.getTeam());
        return mapper.milestone(m);
    }

    @Transactional
    public MilestoneDto create(Long teamId, MilestoneRequest req) {
        Team team = teamService.find(teamId);
        access.requireLeaderOrSupervisor(team);
        if (registrations.findFirstByTeamIdAndStatus(teamId, RegStatus.APPROVED).isEmpty()) {
            throw ApiException.conflict("Nhóm chưa được duyệt đề tài nên chưa thể tạo milestone");
        }
        Milestone m = new Milestone();
        m.setTeam(team);
        apply(m, req);
        milestones.save(m);
        notifications.notifyTeam(team, "Milestone mới: \"" + m.getTitle() + "\" (hạn " + m.getDueDate() + ")",
                access.current());
        return mapper.milestone(m);
    }

    @Transactional
    public MilestoneDto update(Long id, MilestoneRequest req) {
        Milestone m = findMilestone(id);
        access.requireLeaderOrSupervisor(m.getTeam());
        apply(m, req);
        return mapper.milestone(milestones.save(m));
    }

    @Transactional
    public void delete(Long id) {
        Milestone m = findMilestone(id);
        access.requireLeaderOrSupervisor(m.getTeam());
        if (submissions.countByMilestoneId(id) > 0) {
            throw ApiException.conflict("Milestone đã có bài nộp, không thể xóa");
        }
        tasks.deleteByMilestoneId(id);
        milestones.delete(m);
    }

    // ---------------- Task ----------------
    public List<TaskDto> listTasks(Long milestoneId) {
        Milestone m = findMilestone(milestoneId);
        access.requireView(m.getTeam());
        return tasks.findByMilestoneIdOrderByIdAsc(milestoneId).stream().map(mapper::task).toList();
    }

    @Transactional
    public TaskDto createTask(Long milestoneId, TaskRequest req) {
        Milestone m = findMilestone(milestoneId);
        access.requireMember(m.getTeam());
        Task t = new Task();
        t.setMilestone(m);
        applyTask(t, req, m.getTeam());
        tasks.save(t);
        notifyAssignee(t);
        return mapper.task(t);
    }

    @Transactional
    public TaskDto updateTask(Long taskId, TaskRequest req) {
        Task t = findTask(taskId);
        access.requireMember(t.getMilestone().getTeam());
        User before = t.getAssignee();
        applyTask(t, req, t.getMilestone().getTeam());
        tasks.save(t);
        if (t.getAssignee() != null && (before == null || !before.getId().equals(t.getAssignee().getId()))) {
            notifyAssignee(t);
        }
        return mapper.task(t);
    }

    @Transactional
    public TaskDto changeStatus(Long taskId, StatusRequest req) {
        Task t = findTask(taskId);
        access.requireMember(t.getMilestone().getTeam());
        t.setStatus(req.status());
        return mapper.task(tasks.save(t));
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task t = findTask(taskId);
        access.requireLeader(t.getMilestone().getTeam());
        tasks.delete(t);
    }

    // ---------------- helpers ----------------
    private void apply(Milestone m, MilestoneRequest req) {
        m.setTitle(req.title().trim());
        m.setDescription(req.description());
        m.setDueDate(req.dueDate());
    }

    private void applyTask(Task t, TaskRequest req, Team team) {
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        if (req.assigneeId() == null) {
            t.setAssignee(null);
        } else {
            User assignee = team.getMembers().stream()
                    .filter(member -> member.getId().equals(req.assigneeId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Người được giao phải là thành viên của nhóm"));
            t.setAssignee(assignee);
        }
    }

    private void notifyAssignee(Task t) {
        User actor = access.current();
        if (t.getAssignee() != null && !t.getAssignee().getId().equals(actor.getId())) {
            notifications.notify(t.getAssignee(), "Bạn được giao task: \"" + t.getTitle() + "\"");
        }
    }

    private Milestone findMilestone(Long id) {
        return milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }

    private Task findTask(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy task"));
    }
}
