package vn.huflit.spm;

import org.junit.jupiter.api.Test;
import vn.huflit.spm.domain.*;
import vn.huflit.spm.dto.Dtos.MilestoneDto;
import vn.huflit.spm.dto.Dtos.ProgressDto;
import vn.huflit.spm.service.ProgressExport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProgressExportTest {
    private static Map<String, String> unzip(byte[] data) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static MilestoneDto milestone(long id, Long parent, String title, boolean overdue, boolean archived) {
        return new MilestoneDto(id, 1L, parent, title, null, LocalDate.of(2026, 12, 1), 0, 4, 2, 1, 0, overdue, 50,
                archived, null);
    }

    @Test
    void progressReportHasOverviewMilestonesAndTasks() throws IOException {
        ProgressDto p = new ProgressDto(1L, "Nhóm Alpha", "QL đồ án", 4, 2, 50, 1, 0, true, 20, 10);
        Milestone main = new Milestone();
        main.setTitle("Báo cáo giữa kỳ");
        User an = new User();
        an.setFullName("Nguyễn Văn An");
        Task t = new Task();
        t.setId(1L);
        t.setTitle("Viết chương 1");
        t.setMilestone(main);
        t.setAssignee(an);
        t.setStatus(TaskStatus.REVIEW);
        t.setDueDate(LocalDate.of(2026, 11, 20));
        List<MilestoneDto> ms = List.of(milestone(1, null, "Báo cáo giữa kỳ", true, false),
                milestone(2, 1L, "Chương 1–3", false, false));

        Map<String, String> files = unzip(ProgressExport.build(p, ms, List.of(t), LocalDateTime.now()));

        assertTrue(files.containsKey("xl/worksheets/sheet3.xml"));
        assertTrue(files.get("xl/worksheets/sheet1.xml").contains("Có nguy cơ chậm"));
        String sheet2 = files.get("xl/worksheets/sheet2.xml");
        assertTrue(sheet2.contains("Quá hạn") && sheet2.contains("Chương 1–3"));
        String sheet3 = files.get("xl/worksheets/sheet3.xml");
        assertTrue(sheet3.contains("Viết chương 1") && sheet3.contains("Chờ xác nhận") && sheet3.contains("Nguyễn Văn An"));
    }

    @Test
    void archivedMilestoneIsLabelledArchived() throws IOException {
        ProgressDto p = new ProgressDto(1L, "N", null, 0, 0, 0, 0, 0, false, 0, 0);
        Map<String, String> files = unzip(ProgressExport.build(p, List.of(milestone(1, null, "Cũ", false, true)),
                List.of(), LocalDateTime.now()));
        assertTrue(files.get("xl/worksheets/sheet2.xml").contains("Đã lưu trữ"));
    }
}
