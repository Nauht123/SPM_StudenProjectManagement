package vn.huflit.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.repo.MilestoneRepository;
import vn.huflit.spm.repo.PeerEvaluationRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Danh gia cheo AN DANH theo tung milestone chinh. Moi thanh vien cham tat ca dong doi khac theo hai tieu chi
 * (muc do dong gop, do nang cua cong viec duoc giao), thang 1..5, co the sua lai cho den khi milestone bi luu tru.
 *
 * Bao ve an danh:
 * - Khong API nao tra ve nguoi danh gia cho nguoi khac; chi nguoi danh gia doc lai duoc danh gia cua chinh minh.
 * - Giang vien/admin xem ket qua tong hop va nhan xet (khong kem ten nguoi viet, sap xep theo chu cai de khong lo thu tu nop).
 * - Nguoi duoc danh gia chi thay ket qua cua chinh minh va chi khi co it nhat MIN_RATINGS_TO_SHOW danh gia.
 */
@Service
public class PeerEvaluationService {
    public static final int MIN_RATINGS_TO_SHOW = 2;

    /** Trung binh danh gia cheo (thang 5) qua cac milestone. */
    public record PeerAverage(double average, int milestones) {
    }

    private final PeerEvaluationRepository evaluations;
    private final MilestoneRepository milestones;
    private final AccessService access;
    private final Mapper mapper;

    public PeerEvaluationService(PeerEvaluationRepository evaluations, MilestoneRepository milestones,
                                 AccessService access, Mapper mapper) {
        this.evaluations = evaluations;
        this.milestones = milestones;
        this.access = access;
        this.mapper = mapper;
    }

    // ---------------- sinh vien cham ----------------
    public MyPeerEvaluationDto mine(Long milestoneId) {
        Milestone m = findMain(milestoneId);
        access.requireMember(m.getTeam());
        return myView(m, access.current());
    }

    @Transactional
    public MyPeerEvaluationDto save(Long milestoneId, PeerEvaluationRequest req) {
        Milestone m = findMain(milestoneId);
        Team team = m.getTeam();
        access.requireMember(team);
        User me = access.current();
        if (m.isArchived()) {
            throw ApiException.conflict("Milestone đã được lưu trữ, không thể đánh giá");
        }
        List<User> mates = teammates(team, me);
        if (mates.isEmpty()) {
            throw ApiException.conflict("Nhóm chỉ có một thành viên, không có ai để đánh giá");
        }
        Set<Long> mateIds = mates.stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (PeerRatingRequest r : req.ratings()) {
            if (r.evaluateeId().equals(me.getId())) {
                throw ApiException.badRequest("Bạn không thể tự đánh giá chính mình");
            }
            if (!mateIds.contains(r.evaluateeId())) {
                throw ApiException.badRequest("Người được đánh giá phải là thành viên khác trong nhóm");
            }
            if (!seen.add(r.evaluateeId())) {
                throw ApiException.badRequest("Mỗi thành viên chỉ được đánh giá một lần");
            }
        }
        if (seen.size() != mateIds.size()) {
            throw ApiException.badRequest("Hãy đánh giá tất cả các thành viên khác trong nhóm");
        }
        Map<Long, PeerEvaluation> existing = evaluations.findByMilestoneIdAndEvaluatorId(milestoneId, me.getId())
                .stream().collect(Collectors.toMap(e -> e.getEvaluatee().getId(), e -> e, (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        for (PeerRatingRequest r : req.ratings()) {
            PeerEvaluation e = existing.get(r.evaluateeId());
            if (e == null) {
                e = new PeerEvaluation();
                e.setMilestone(m);
                e.setEvaluator(me);
                e.setEvaluatee(mates.stream().filter(u -> u.getId().equals(r.evaluateeId())).findFirst().orElseThrow());
                e.setCreatedAt(now);
            }
            e.setContributionScore(r.contributionScore());
            e.setWorkloadScore(r.workloadScore());
            e.setComment(r.comment() == null || r.comment().isBlank() ? null : r.comment().trim());
            e.setUpdatedAt(now);
            evaluations.save(e);
        }
        return myView(m, me);
    }

    // ---------------- ket qua tong hop ----------------
    /** Giang vien huong dan / admin: ca nhom. Thanh vien (ke ca nhom truong): chi cua chinh minh. */
    public PeerResultsDto results(Long milestoneId) {
        Milestone m = findMain(milestoneId);
        Team team = m.getTeam();
        access.requireView(team);
        User me = access.current();
        boolean seeAll = isEvaluator(me);
        List<User> members = sortedMembers(team);
        List<PeerEvaluation> all = evaluations.findByMilestoneId(milestoneId);
        Map<Long, List<PeerEvaluation>> byEvaluatee = all.stream()
                .collect(Collectors.groupingBy(e -> e.getEvaluatee().getId()));

        List<PeerResultDto> rows = new ArrayList<>();
        for (User u : members) {
            if (!seeAll && !u.getId().equals(me.getId())) {
                continue;
            }
            rows.add(resultFor(u, byEvaluatee.getOrDefault(u.getId(), List.of()), seeAll));
        }

        int need = members.size() - 1;
        Map<Long, Long> ratedCount = all.stream()
                .collect(Collectors.groupingBy(e -> e.getEvaluator().getId(), Collectors.counting()));
        List<User> done = members.stream()
                .filter(u -> need > 0 && ratedCount.getOrDefault(u.getId(), 0L) >= need).toList();
        List<String> pending = seeAll
                ? members.stream().filter(u -> need > 0 && ratedCount.getOrDefault(u.getId(), 0L) < need)
                        .map(User::getFullName).toList()
                : List.of();
        return new PeerResultsDto(m.getId(), m.getTitle(), !m.isArchived(), done.size(),
                need > 0 ? members.size() : 0, pending, MIN_RATINGS_TO_SHOW, seeAll, rows);
    }

    /**
     * Trung binh danh gia cheo moi thanh vien qua cac milestone (dung cho diem tong hop).
     * full = true (giang vien/admin): tinh moi milestone co danh gia; false: chi milestone du so danh gia toi thieu.
     */
    public Map<Long, PeerAverage> peerAverages(Team team, boolean full) {
        Map<Long, Map<Long, List<PeerEvaluation>>> grouped = evaluations.findByMilestoneTeamId(team.getId()).stream()
                .collect(Collectors.groupingBy(e -> e.getMilestone().getId(),
                        Collectors.groupingBy(e -> e.getEvaluatee().getId())));
        Map<Long, List<Double>> perMember = new HashMap<>();
        for (Map<Long, List<PeerEvaluation>> byEvaluatee : grouped.values()) {
            for (Map.Entry<Long, List<PeerEvaluation>> en : byEvaluatee.entrySet()) {
                List<PeerEvaluation> list = en.getValue();
                if (full ? list.isEmpty() : list.size() < MIN_RATINGS_TO_SHOW) {
                    continue;
                }
                perMember.computeIfAbsent(en.getKey(), k -> new ArrayList<>()).add(overall(list));
            }
        }
        Map<Long, PeerAverage> out = new HashMap<>();
        perMember.forEach((id, vals) -> out.put(id,
                new PeerAverage(round2(vals.stream().mapToDouble(Double::doubleValue).average().orElse(0)), vals.size())));
        return out;
    }

    // ---------------- helpers ----------------
    private PeerResultDto resultFor(User u, List<PeerEvaluation> list, boolean full) {
        int n = list.size();
        boolean visible = full ? n > 0 : n >= MIN_RATINGS_TO_SHOW;
        if (!visible) {
            return new PeerResultDto(mapper.user(u), false, n, null, null, null, List.of());
        }
        double c = round2(list.stream().mapToInt(PeerEvaluation::getContributionScore).average().orElse(0));
        double w = round2(list.stream().mapToInt(PeerEvaluation::getWorkloadScore).average().orElse(0));
        List<String> comments = list.stream().map(PeerEvaluation::getComment)
                .filter(s -> s != null && !s.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new PeerResultDto(mapper.user(u), true, n, c, w, round2((c + w) / 2.0), comments);
    }

    /** Diem tong cua mot thanh vien trong mot milestone = trung binh hai tieu chi. */
    private static double overall(List<PeerEvaluation> list) {
        double c = list.stream().mapToInt(PeerEvaluation::getContributionScore).average().orElse(0);
        double w = list.stream().mapToInt(PeerEvaluation::getWorkloadScore).average().orElse(0);
        return (c + w) / 2.0;
    }

    private MyPeerEvaluationDto myView(Milestone m, User me) {
        List<UserDto> mates = teammates(m.getTeam(), me).stream().map(mapper::user).toList();
        List<PeerRatingDto> ratings = evaluations.findByMilestoneIdAndEvaluatorId(m.getId(), me.getId()).stream()
                .map(e -> new PeerRatingDto(e.getEvaluatee().getId(), e.getContributionScore(), e.getWorkloadScore(),
                        e.getComment()))
                .sorted(Comparator.comparing(PeerRatingDto::evaluateeId))
                .toList();
        return new MyPeerEvaluationDto(m.getId(), m.getTitle(), !m.isArchived(), mates, ratings);
    }

    private static boolean isEvaluator(User u) {
        return u.getRole() == Role.ADMIN || u.getRole() == Role.LECTURER;
    }

    private static List<User> sortedMembers(Team team) {
        return team.getMembers().stream().sorted(Comparator.comparing(User::getId)).toList();
    }

    private static List<User> teammates(Team team, User me) {
        return sortedMembers(team).stream().filter(u -> !u.getId().equals(me.getId())).toList();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Milestone findMain(Long id) {
        Milestone m = milestones.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy milestone"));
        if (m.getParent() != null) {
            throw ApiException.badRequest("Đánh giá chéo theo milestone chính, không phải milestone phụ");
        }
        return m;
    }
}
