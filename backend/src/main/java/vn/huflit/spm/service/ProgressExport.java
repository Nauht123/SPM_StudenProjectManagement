package vn.huflit.spm.service;

import vn.huflit.spm.domain.Task;
import vn.huflit.spm.domain.TaskStatus;
import vn.huflit.spm.dto.Dtos.MilestoneDto;
import vn.huflit.spm.dto.Dtos.ProgressDto;

import java.time.LocalDateTime;
import java.util.*;

/** File Excel bao cao tien do cua mot nhom: tong quan, milestone (chinh/phu) va danh sach task. */
public final class ProgressExport {
    private static final Map<TaskStatus, String> STATUS = Map.of(
            TaskStatus.SUGGESTED, "Đề xuất", TaskStatus.TODO, "Cần làm", TaskStatus.DOING, "Đang làm",
            TaskStatus.REVIEW, "Chờ xác nhận", TaskStatus.DONE, "Hoàn thành", TaskStatus.CANCELLED, "Đã hủy");

    private ProgressExport() {
    }

    private static List<Object> row(Object... cells) {
        return new ArrayList<>(Arrays.asList(cells));
    }

    static String milestoneStatus(MilestoneDto m) {
        if (m.archived()) {
            return "Đã lưu trữ";
        }
        if (m.overdue()) {
            return "Quá hạn";
        }
        boolean main = m.parentId() == null;
        boolean finished = main ? m.submissions() > 0 : m.totalTasks() > 0 && m.doneTasks() == m.totalTasks();
        return finished ? (main ? "Đã nộp" : "Hoàn thành") : "Đang thực hiện";
    }

    public static byte[] build(ProgressDto p, List<MilestoneDto> milestones, List<Task> tasks, LocalDateTime now) {
        List<List<Object>> overview = new ArrayList<>();
        overview.add(row("Mục", "Giá trị"));
        overview.add(row("Nhóm", p.teamName()));
        overview.add(row("Đề tài", p.topicTitle() == null ? "" : p.topicTitle()));
        overview.add(row("Tiến độ theo trọng số (%)", p.percent()));
        overview.add(row("Điểm trọng số hoàn thành / tổng", p.doneWeight() + " / " + p.totalWeight()));
        overview.add(row("Task hoàn thành / tổng", p.doneTasks() + " / " + p.totalTasks()));
        overview.add(row("Milestone chính quá hạn chưa nộp", p.overdueMilestones()));
        overview.add(row("Bài nộp trễ hạn", p.lateSubmissions()));
        overview.add(row("Tình trạng", p.atRisk() ? "Có nguy cơ chậm" : "Đúng tiến độ"));
        overview.add(row("Ngày xuất", ContributionExport.time(now)));

        Map<Long, String> titles = new HashMap<>();
        for (MilestoneDto m : milestones) {
            titles.put(m.id(), m.title());
        }
        List<List<Object>> ms = new ArrayList<>();
        ms.add(row("Loại", "Milestone", "Thuộc milestone chính", "Hạn", "Trạng thái", "Tiến độ (%)", "Task hoàn thành",
                "Tổng task", "Chờ xác nhận", "Bài nộp"));
        for (MilestoneDto m : milestones) {
            ms.add(row(m.parentId() == null ? "Chính" : "Phụ", m.title(), m.parentId() == null ? "" : titles.get(m.parentId()),
                    ContributionExport.date(m.dueDate()), milestoneStatus(m), m.percent(), m.doneTasks(),
                    m.totalTasks(), m.pendingReview(), m.submissions()));
        }

        List<List<Object>> ts = new ArrayList<>();
        ts.add(row("Milestone", "Task", "Trạng thái", "Người thực hiện", "Trọng số", "Độ khó", "Giờ công", "Hạn",
                "Nộp lần cuối", "Xác nhận lúc", "Số lần sửa", "Chất lượng (1-5)"));
        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparing((Task t) -> t.getMilestone().getTitle()).thenComparing(Task::getId));
        for (Task t : sorted) {
            ts.add(row(t.getMilestone().getTitle(), t.getTitle(), STATUS.getOrDefault(t.getStatus(), t.getStatus().name()),
                    t.getAssignee() == null ? "" : t.getAssignee().getFullName(), t.getWeight(), t.getDifficulty(),
                    t.getWorkloadHours(), ContributionExport.date(t.getDueDate()),
                    ContributionExport.time(t.getSubmittedAt()), ContributionExport.time(t.getConfirmedAt()),
                    t.getRevisionCount(), t.getQualityRating()));
        }
        return XlsxWriter.write(List.of(new XlsxWriter.Sheet("Tổng quan", overview),
                new XlsxWriter.Sheet("Milestone", ms), new XlsxWriter.Sheet("Task", ts)));
    }
}
