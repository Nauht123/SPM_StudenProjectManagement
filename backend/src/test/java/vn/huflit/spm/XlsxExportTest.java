package vn.huflit.spm;

import org.junit.jupiter.api.Test;
import vn.huflit.spm.domain.Role;
import vn.huflit.spm.domain.TaskStatus;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.ContributionExport;
import vn.huflit.spm.service.XlsxWriter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** File Excel bao cao: dung cau truc .xlsx, escape XML va noi dung bao cao dong gop. */
class XlsxExportTest {

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

    private static UserDto user(long id, String name) {
        return new UserDto(id, "u" + id, name, "SV00" + id, Role.STUDENT);
    }

    private static MemberContributionDto member(long id, String name, List<ContributionTaskDto> tasks) {
        return new MemberContributionDto(user(id, name), id == 1, 3, 2, 2, 0, 1, 1, 0, 1, 10, 6, 12, 2, 1, 3, 2,
                14.5, 70, 60, tasks);
    }

    @Test
    void writesValidWorkbookPartsAndEscapesText() throws IOException {
        byte[] data = XlsxWriter.write(List.of(new XlsxWriter.Sheet("Tổng hợp: <A&B>", List.of(
                List.<Object>of("Họ tên", "Điểm"),
                List.<Object>of("Trần \"B\" & <C>", 7.5)))));
        Map<String, String> files = unzip(data);
        assertTrue(files.containsKey("[Content_Types].xml"));
        assertTrue(files.containsKey("xl/workbook.xml"));
        assertTrue(files.containsKey("xl/worksheets/sheet1.xml"));
        String sheet = files.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("Trần &quot;B&quot; &amp; &lt;C&gt;"));
        assertTrue(sheet.contains("<v>7.5</v>"));
        assertFalse(files.get("xl/workbook.xml").contains("<A&B>"));
    }

    @Test
    void dropsInvalidXmlControlCharacters() throws IOException {
        byte[] data = XlsxWriter.write(List.of(new XlsxWriter.Sheet("S", List.of(
                List.<Object>of("Cot"), List.<Object>of("ok\u0001xong")))));
        assertTrue(unzip(data).get("xl/worksheets/sheet1.xml").contains("okxong"));
    }

    @Test
    void teamReportHasThreeSheetsWithMembersAndTasks() throws IOException {
        ContributionTaskDto done = new ContributionTaskDto(1L, "Viết báo cáo", "MS phụ 1", TaskStatus.DONE, 5, 3, 8,
                LocalDate.of(2026, 11, 10), true, 1, 4, 14.5);
        ContributionReportDto report = new ContributionReportDto(1L, "Nhóm Alpha", 20.7, "Công thức",
                List.of(member(1, "Nguyễn Văn A", List.of(done)), member(2, "Trần Thị B", List.of())));
        Map<String, String> files = unzip(ContributionExport.team(report, "QL đồ án", LocalDateTime.now()));
        assertTrue(files.containsKey("xl/worksheets/sheet3.xml"));
        assertFalse(files.containsKey("xl/worksheets/sheet4.xml"));
        String members = files.get("xl/worksheets/sheet2.xml");
        assertTrue(members.contains("Nguyễn Văn A") && members.contains("Trần Thị B"));
        assertTrue(files.get("xl/worksheets/sheet3.xml").contains("Viết báo cáo"));
    }

    @Test
    void memberReportHasSummaryTasksHistoryAndRevisions() throws IOException {
        MemberContributionDto summary = member(1, "Nguyễn Văn A", List.of());
        MemberDetailDto detail = new MemberDetailDto(1L, "Nhóm Alpha", "QL đồ án", summary,
                List.of(new RevisionEventDto(1L, "Viết báo cáo", "MS phụ 1", 1, LocalDateTime.now().minusDays(2),
                        LocalDateTime.now().minusDays(1), user(9, "Trưởng nhóm"), "Thiếu mục 2")),
                List.of(new IndividualSubmissionDto("MILESTONE", 3L, "bao-cao.docx", "Giữa kỳ", 1,
                        LocalDateTime.now(), null, "UPLOADER_CONTRIBUTOR", true, null, null)),
                true, "Công thức");
        Map<String, String> files = unzip(ContributionExport.member(detail, LocalDateTime.now()));
        assertTrue(files.containsKey("xl/worksheets/sheet4.xml"));
        assertTrue(files.get("xl/worksheets/sheet4.xml").contains("Thiếu mục 2"));
        assertTrue(files.get("xl/worksheets/sheet3.xml").contains("Người upload và đóng góp"));
    }
}
