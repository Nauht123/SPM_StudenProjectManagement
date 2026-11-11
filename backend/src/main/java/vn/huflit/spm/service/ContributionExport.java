package vn.huflit.spm.service;

import vn.huflit.spm.domain.TaskStatus;
import vn.huflit.spm.dto.Dtos.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dung file Excel bao cao dong gop (nhom va ca nhan) tu cac DTO da tinh san. */
public final class ContributionExport {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<TaskStatus, String> STATUS = Map.of(
            TaskStatus.SUGGESTED, "Đề xuất", TaskStatus.TODO, "Cần làm", TaskStatus.DOING, "Đang làm",
            TaskStatus.REVIEW, "Chờ xác nhận", TaskStatus.DONE, "Hoàn thành", TaskStatus.CANCELLED, "Đã hủy");
    private static final Map<String, String> OUTCOME = Map.of(
            "PENDING", "Chờ duyệt", "APPROVED", "Đã duyệt", "REVISION_REQUESTED", "Yêu cầu sửa");
    private static final Map<String, String> ROLE = Map.of(
            "PERFORMER", "Người thực hiện", "UPLOADER", "Người upload", "CONTRIBUTOR", "Người đóng góp",
            "UPLOADER_CONTRIBUTOR", "Người upload và đóng góp");

    private ContributionExport() {
    }

    static String time(LocalDateTime t) {
        return t == null ? "" : t.format(DATE_TIME);
    }

    static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private static String onTime(Boolean v) {
        return v == null ? "" : v ? "Đúng hạn" : "Trễ hạn";
    }

    private static List<Object> row(Object... cells) {
        return new ArrayList<>(java.util.Arrays.asList(cells));
    }

    /** File bao cao dong gop ca nhom (giang vien / nhom truong xuat). */
    public static byte[] team(ContributionReportDto report, String topicTitle, LocalDateTime now) {
        List<List<Object>> info = new ArrayList<>();
        info.add(row("Mục", "Giá trị"));
        info.add(row("Nhóm", report.teamName()));
        info.add(row("Đề tài", topicTitle == null ? "" : topicTitle));
        info.add(row("Tổng điểm đóng góp của nhóm", report.totalScore()));
        info.add(row("Ngày xuất", time(now)));
        info.add(row("Công thức tính điểm", report.formula()));

        List<List<Object>> members = new ArrayList<>();
        members.add(row("Thành viên", "MSSV", "Vai trò", "Task được giao", "Task hoàn thành", "Đúng hạn", "Trễ hạn",
                "Chưa xong", "Quá hạn chưa xong", "Số lần bị yêu cầu sửa", "Trọng số được giao", "Trọng số hoàn thành",
                "Tiến độ cá nhân (%)", "Giờ công hoàn thành", "Điểm đóng góp", "Tỷ lệ đóng góp (%)",
                "Bài nộp upload", "Bài nộp đóng góp"));
        List<List<Object>> tasks = new ArrayList<>();
        tasks.add(row("Thành viên", "Task", "Milestone phụ", "Trạng thái", "Trọng số", "Độ khó", "Giờ công", "Hạn",
                "Đúng hạn", "Số lần sửa", "Chất lượng (1-5)", "Điểm"));
        for (MemberContributionDto m : report.members()) {
            String name = m.member().fullName();
            members.add(row(name, m.member().studentCode(), m.leader() ? "Nhóm trưởng" : "Thành viên",
                    m.assignedTasks(), m.doneTasks(), m.onTimeTasks(), m.lateTasks(), m.unfinishedTasks(),
                    m.overdueOpenTasks(), m.revisionCount(), m.weightAssigned(), m.weightDone(), m.progressPercent(),
                    m.workloadHoursDone(), m.score(), m.sharePercent(), m.submissionsUploaded(),
                    m.submissionsContributed()));
            for (ContributionTaskDto t : m.tasks()) {
                tasks.add(row(name, t.title(), t.milestoneTitle(), STATUS.getOrDefault(t.status(), t.status().name()),
                        t.weight(), t.difficulty(), t.workloadHours(), date(t.dueDate()), onTime(t.onTime()),
                        t.revisions(), t.qualityRating(), t.status() == TaskStatus.DONE ? t.points() : null));
            }
        }
        return XlsxWriter.write(List.of(new XlsxWriter.Sheet("Thông tin", info),
                new XlsxWriter.Sheet("Đóng góp", members), new XlsxWriter.Sheet("Chi tiết task", tasks)));
    }

    /** File bao cao ca nhan cua mot thanh vien: tom tat, task, lich su nop, theo doi sua lai. */
    public static byte[] member(MemberDetailDto d, LocalDateTime now) {
        MemberContributionDto s = d.summary();
        int doneTotal = (int) s.doneTasks();
        List<List<Object>> summary = new ArrayList<>();
        summary.add(row("Mục", "Giá trị"));
        summary.add(row("Thành viên", s.member().fullName()));
        summary.add(row("MSSV", s.member().studentCode()));
        summary.add(row("Vai trò", s.leader() ? "Nhóm trưởng" : "Thành viên"));
        summary.add(row("Nhóm", d.teamName()));
        summary.add(row("Đề tài", d.topicTitle() == null ? "" : d.topicTitle()));
        summary.add(row("Điểm đóng góp", s.score()));
        summary.add(row("Tỷ lệ đóng góp trong nhóm (%)", s.sharePercent()));
        summary.add(row("Tiến độ cá nhân theo trọng số (%)", s.progressPercent()));
        summary.add(row("Task được giao", s.assignedTasks()));
        summary.add(row("Task hoàn thành", s.doneTasks()));
        summary.add(row("Hoàn thành đúng hạn", s.onTimeTasks()));
        summary.add(row("Hoàn thành trễ hạn", s.lateTasks()));
        summary.add(row("Tỷ lệ đúng hạn (%)", doneTotal == 0 ? 0 : Math.round(s.onTimeTasks() * 100.0 / doneTotal)));
        summary.add(row("Chưa xong", s.unfinishedTasks()));
        summary.add(row("Quá hạn chưa xong", s.overdueOpenTasks()));
        summary.add(row("Số lần bị yêu cầu sửa", s.revisionCount()));
        summary.add(row("Giờ công đã hoàn thành", s.workloadHoursDone()));
        summary.add(row("Bài milestone: upload / đóng góp", s.submissionsUploaded() + " / " + s.submissionsContributed()));
        summary.add(row("Ngày xuất", time(now)));
        summary.add(row("Công thức tính điểm", d.formula()));

        List<List<Object>> tasks = new ArrayList<>();
        tasks.add(row("Task", "Milestone phụ", "Trạng thái", "Trọng số", "Độ khó", "Giờ công", "Hạn", "Đúng hạn",
                "Số lần sửa", "Chất lượng (1-5)", "Điểm"));
        for (ContributionTaskDto t : s.tasks()) {
            tasks.add(row(t.title(), t.milestoneTitle(), STATUS.getOrDefault(t.status(), t.status().name()),
                    t.weight(), t.difficulty(), t.workloadHours(), date(t.dueDate()), onTime(t.onTime()),
                    t.revisions(), t.qualityRating(), t.status() == TaskStatus.DONE ? t.points() : null));
        }

        List<List<Object>> history = new ArrayList<>();
        history.add(row("Thời điểm", "Loại", "Nội dung", "Milestone", "Lần/Phiên bản", "Vai trò", "Kết quả duyệt",
                "Đúng hạn", "Ghi chú", "File"));
        for (IndividualSubmissionDto h : d.submissionHistory()) {
            history.add(row(time(h.at()), "TASK".equals(h.kind()) ? "Task" : "Bài nộp milestone", h.title(),
                    h.milestoneTitle(), h.attemptNo(), ROLE.getOrDefault(h.role(), h.role()),
                    h.outcome() == null ? "" : OUTCOME.getOrDefault(h.outcome(), h.outcome()),
                    h.late() ? "Trễ hạn" : "Đúng hạn", h.note(), h.fileName()));
        }

        List<List<Object>> revisions = new ArrayList<>();
        revisions.add(row("Task", "Milestone phụ", "Lần nộp", "Nộp lúc", "Yêu cầu sửa lúc", "Người duyệt", "Nội dung yêu cầu sửa"));
        for (RevisionEventDto r : d.revisions()) {
            revisions.add(row(r.taskTitle(), r.milestoneTitle(), r.attemptNo(), time(r.submittedAt()),
                    time(r.reviewedAt()), r.reviewedBy() == null ? "" : r.reviewedBy().fullName(), r.reviewNote()));
        }
        return XlsxWriter.write(List.of(new XlsxWriter.Sheet("Tóm tắt", summary), new XlsxWriter.Sheet("Task", tasks),
                new XlsxWriter.Sheet("Lịch sử nộp", history), new XlsxWriter.Sheet("Yêu cầu sửa", revisions)));
    }
}
