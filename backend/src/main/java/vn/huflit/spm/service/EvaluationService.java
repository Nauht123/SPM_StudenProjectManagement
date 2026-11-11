package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.ContributionAdjustmentRepository;
import vn.huflit.spm.repo.MilestoneRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Diem dong gop tong hop: diem tu dong (theo task) + dieu chinh cua giang vien (kem ly do) + danh gia cheo tham khao.
 * Giang vien huong dan dieu chinh diem; moi dieu chinh duoc luu lai, co the thu hoi nhung khong bi xoa/sua.
 * Phan quyen: giang vien/admin xem ca nhom; thanh vien (ke ca nhom truong) chi xem diem va dieu chinh cua chinh minh.
 */
@Service
public class EvaluationService {
    private final ContributionAdjustmentRepository adjustments;
    private final ContributionService contributionService;
    private final PeerEvaluationService peerService;
    private final TeamService teamService;
    private final MilestoneRepository milestones;
    private final NotificationService notifications;
    private final AccessService access;
    private final Mapper mapper;

    public EvaluationService(ContributionAdjustmentRepository adjustments, ContributionService contributionService,
                             PeerEvaluationService peerService, TeamService teamService,
                             MilestoneRepository milestones, NotificationService notifications,
                             AccessService access, Mapper mapper) {
        this.adjustments = adjustments;
        this.contributionService = contributionService;
        this.peerService = peerService;
        this.teamService = teamService;
        this.milestones = milestones;
        this.notifications = notifications;
        this.access = access;
        this.mapper = mapper;
    }

    /** Diem cuoi cung cua mot thanh vien: diem tu dong + dieu chinh con hieu luc, khong am. */
    public static double finalScore(double autoScore, double adjustment) {
        return Math.max(0, round2(autoScore + adjustment));
    }

    public EvaluationSummaryDto summary(Long teamId) {
        Team team = teamService.find(teamId);
        access.requireView(team);
        User me = access.current();
        boolean seeAll = me.getRole() == Role.ADMIN || me.getRole() == Role.LECTURER;
        boolean canAdjust = me.getRole() == Role.LECTURER;

        ContributionReportDto report = contributionService.report(teamId);
        List<ContributionAdjustment> all = adjustments.findByTeamIdOrderByCreatedAtDescIdDesc(teamId);
        Map<Long, PeerEvaluationService.PeerAverage> peer = peerService.peerAverages(team, seeAll);

        List<MemberContributionDto> visible = new ArrayList<>();
        for (MemberContributionDto r : report.members()) {
            if (seeAll || r.member().id().equals(me.getId())) {
                visible.add(r);
            }
        }

        double[] finals = new double[visible.size()];
        double[] adjSums = new double[visible.size()];
        double totalFinal = 0;
        for (int i = 0; i < visible.size(); i++) {
            Long uid = visible.get(i).member().id();
            adjSums[i] = round2(all.stream()
                    .filter(a -> !a.isRevoked() && a.getMember().getId().equals(uid))
                    .mapToDouble(ContributionAdjustment::getDelta).sum());
            finals[i] = finalScore(visible.get(i).score(), adjSums[i]);
            totalFinal += finals[i];
        }

        List<EvaluationRowDto> rows = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            MemberContributionDto r = visible.get(i);
            Long uid = r.member().id();
            PeerEvaluationService.PeerAverage pa = peer.get(uid);
            List<AdjustmentDto> mine = all.stream()
                    .filter(a -> a.getMember().getId().equals(uid))
                    .map(this::toDto)
                    .toList();
            Double share = seeAll ? (totalFinal <= 0 ? 0.0 : round2(finals[i] * 100.0 / totalFinal)) : null;
            rows.add(new EvaluationRowDto(r.member(), r.leader(), r.score(), adjSums[i], finals[i], share,
                    pa == null ? null : pa.average(), pa == null ? 0 : pa.milestones(), mine));
        }
        return new EvaluationSummaryDto(team.getId(), team.getName(), canAdjust, seeAll,
                seeAll ? round2(totalFinal) : 0, rows);
    }

    @Transactional
    public AdjustmentDto adjust(Long teamId, AdjustmentRequest req) {
        Team team = teamService.find(teamId);
        access.requireSupervisor(team);
        User me = access.current();
        User member = team.getMembers().stream()
                .filter(u -> u.getId().equals(req.userId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Người được điều chỉnh phải là thành viên của nhóm"));
        double delta = round2(req.delta());
        if (delta == 0) {
            throw ApiException.badRequest("Số điểm điều chỉnh phải khác 0");
        }
        Milestone milestone = null;
        if (req.milestoneId() != null) {
            milestone = milestones.findById(req.milestoneId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
            if (!milestone.getTeam().getId().equals(teamId) || milestone.getParent() != null) {
                throw ApiException.badRequest("Milestone phải là milestone chính của nhóm này");
            }
        }
        ContributionAdjustment a = new ContributionAdjustment();
        a.setTeam(team);
        a.setMember(member);
        a.setMilestone(milestone);
        a.setDelta(delta);
        a.setReason(req.reason().trim());
        a.setAdjustedBy(me);
        a.setCreatedAt(LocalDateTime.now());
        adjustments.save(a);
        notifications.notify(member, "Giảng viên điều chỉnh điểm đóng góp của bạn " + signed(delta)
                + (milestone != null ? " (milestone \"" + milestone.getTitle() + "\")" : "") + ": " + a.getReason());
        return toDto(a);
    }

    /** Thu hoi mot dieu chinh (van giu lich su, khong tinh vao diem cuoi). */
    @Transactional
    public AdjustmentDto revoke(Long adjustmentId) {
        ContributionAdjustment a = adjustments.findById(adjustmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy điều chỉnh điểm"));
        access.requireSupervisor(a.getTeam());
        if (a.isRevoked()) {
            throw ApiException.conflict("Điều chỉnh này đã được thu hồi");
        }
        a.setRevoked(true);
        a.setRevokedAt(LocalDateTime.now());
        a.setRevokedBy(access.current());
        adjustments.save(a);
        notifications.notify(a.getMember(), "Giảng viên đã thu hồi điều chỉnh điểm " + signed(a.getDelta())
                + " của bạn (" + a.getReason() + ")");
        return toDto(a);
    }

    // ---------------- helpers ----------------
    private AdjustmentDto toDto(ContributionAdjustment a) {
        Milestone ms = a.getMilestone();
        return new AdjustmentDto(a.getId(), mapper.user(a.getMember()), mapper.user(a.getAdjustedBy()),
                ms == null ? null : ms.getId(), ms == null ? null : ms.getTitle(), a.getDelta(), a.getReason(),
                a.getCreatedAt(), a.isRevoked(), a.getRevokedAt());
    }

    private static String signed(double v) {
        String s = (v == Math.rint(v)) ? String.valueOf((long) v) : String.valueOf(v);
        return v > 0 ? "+" + s : s;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
