package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.Milestone;
import vn.huflit.spm.domain.RegStatus;
import vn.huflit.spm.domain.Team;
import vn.huflit.spm.dto.Dtos.MilestoneDto;
import vn.huflit.spm.dto.Dtos.MilestoneRequest;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.RegistrationRepository;
import vn.huflit.spm.repo.SubmissionRepository;
import vn.huflit.spm.repo.TaskRepository;

import java.util.List;

/**
 * Milestone hai cap:
 * - Milestone chinh (parent = null): do giang vien huong dan (hoac admin) quan ly, la dau moc nop bai.
 * - Milestone phu (parent != null): do nhom truong (hoac admin) quan ly, chua cac task cua thanh vien.
 */
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

    /** Tra ve phang ca milestone chinh va phu; client nhom theo parentId. */
    public List<MilestoneDto> list(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        return milestones.findByTeamIdOrderByDueDateAsc(teamId).stream().map(mapper::milestone).toList();
    }

    public MilestoneDto get(Long id) {
        Milestone m = find(id);
        access.requireView(m.getTeam());
        return mapper.milestone(m);
    }

    @Transactional
    public MilestoneDto create(Long teamId, MilestoneRequest req) {
        Team team = teamService.find(teamId);
        Milestone parent = null;
        if (req.parentId() == null) {
            access.requireSupervisorOrAdmin(team);
        } else {
            parent = find(req.parentId());
            if (!parent.getTeam().getId().equals(teamId)) {
                throw ApiException.badRequest("Milestone chính không thuộc nhóm này");
            }
            if (parent.getParent() != null) {
                throw ApiException.badRequest("Chỉ được tạo milestone phụ dưới một milestone chính");
            }
            access.requireLeaderOrAdmin(team);
            if (req.dueDate().isAfter(parent.getDueDate())) {
                throw ApiException.badRequest("Hạn milestone phụ không được sau hạn milestone chính ("
                        + parent.getDueDate() + ")");
            }
        }
        if (registrations.findFirstByTeamIdAndStatus(teamId, RegStatus.APPROVED).isEmpty()) {
            throw ApiException.conflict("Nhóm chưa được duyệt đề tài nên chưa thể tạo milestone");
        }
        Milestone m = new Milestone();
        m.setTeam(team);
        m.setParent(parent);
        m.setTitle(req.title().trim());
        m.setDescription(req.description());
        m.setDueDate(req.dueDate());
        milestones.save(m);
        notifications.notifyTeam(team, (parent == null ? "Milestone chính mới: \"" : "Milestone phụ mới: \"")
                + m.getTitle() + "\" (hạn " + m.getDueDate() + ")", access.current());
        return mapper.milestone(m);
    }

    @Transactional
    public MilestoneDto update(Long id, MilestoneRequest req) {
        Milestone m = find(id);
        if (m.getParent() == null) {
            access.requireSupervisorOrAdmin(m.getTeam());
            boolean childAfter = milestones.findByParentId(id).stream()
                    .anyMatch(s -> s.getDueDate().isAfter(req.dueDate()));
            if (childAfter) {
                throw ApiException.conflict("Có milestone phụ có hạn sau hạn mới của milestone chính");
            }
        } else {
            access.requireLeaderOrAdmin(m.getTeam());
            if (req.dueDate().isAfter(m.getParent().getDueDate())) {
                throw ApiException.badRequest("Hạn milestone phụ không được sau hạn milestone chính ("
                        + m.getParent().getDueDate() + ")");
            }
        }
        m.setTitle(req.title().trim());
        m.setDescription(req.description());
        m.setDueDate(req.dueDate());
        return mapper.milestone(milestones.save(m));
    }

    @Transactional
    public void delete(Long id) {
        Milestone m = find(id);
        if (m.getParent() == null) {
            access.requireSupervisorOrAdmin(m.getTeam());
            if (submissions.countByMilestoneId(id) > 0) {
                throw ApiException.conflict("Milestone đã có bài nộp, không thể xóa");
            }
            for (Milestone child : milestones.findByParentId(id)) {
                tasks.deleteByMilestoneId(child.getId());
                milestones.delete(child);
            }
        } else {
            access.requireLeaderOrAdmin(m.getTeam());
            tasks.deleteByMilestoneId(id);
        }
        milestones.delete(m);
    }

    private Milestone find(Long id) {
        return milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
    }
}
