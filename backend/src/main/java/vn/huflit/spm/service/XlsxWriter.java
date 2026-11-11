package vn.huflit.spm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Ghi file .xlsx toi gian bang JDK (khong can Apache POI): moi sheet la danh sach dong,
 * dong dau tien dung kieu tieu de (dam, nen xam). O la So (Number) hoac chuoi (inline string).
 */
public final class XlsxWriter {
    public record Sheet(String name, List<List<Object>> rows) {
    }

    private XlsxWriter() {
    }

    public static byte[] write(List<Sheet> sheets) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml", workbook(sheets));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
            put(zip, "xl/styles.xml", styles());
            for (int i = 0; i < sheets.size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int n) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= n; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escape(sheetName(sheets.get(i).name(), i)))
                    .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private static String workbookRels(int n) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= n; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(n + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE5E9F2\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheetXml(Sheet sheet) {
        List<List<Object>> rows = sheet.rows();
        int cols = 0;
        for (List<Object> r : rows) {
            cols = Math.max(cols, r.size());
        }
        int[] width = new int[Math.max(cols, 1)];
        for (List<Object> r : rows) {
            for (int c = 0; c < r.size(); c++) {
                String s = text(r.get(c));
                int longest = 0;
                for (String line : s.split("\n")) {
                    longest = Math.max(longest, line.length());
                }
                width[c] = Math.max(width[c], longest);
            }
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (cols > 0) {
            sb.append("<cols>");
            for (int c = 0; c < cols; c++) {
                sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                        .append("\" width=\"").append(Math.min(60, Math.max(10, width[c] + 3))).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = new ArrayList<>(rows.get(r));
            sb.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < row.size(); c++) {
                Object v = row.get(c);
                String ref = columnName(c) + (r + 1);
                String style = r == 0 ? " s=\"1\"" : "";
                if (v == null || (v instanceof String str && str.isEmpty())) {
                    if (r == 0) {
                        sb.append("<c r=\"").append(ref).append("\"").append(style).append("/>");
                    }
                } else if (v instanceof Number n) {
                    sb.append("<c r=\"").append(ref).append("\"").append(style).append("><v>")
                            .append(number(n)).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\"").append(style).append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(v.toString())).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private static String number(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "0";
            }
            return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return n.toString();
    }

    private static String text(Object v) {
        return v == null ? "" : v.toString();
    }

    /** A, B, ..., Z, AA, AB ... */
    static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    static String sheetName(String name, int index) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Sheet" + (index + 1);
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** Loai ky tu dieu khien khong hop le trong XML 1.0 va escape ky tu dac biet. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        s.codePoints().forEach(cp -> {
            if (cp == '&') sb.append("&amp;");
            else if (cp == '<') sb.append("&lt;");
            else if (cp == '>') sb.append("&gt;");
            else if (cp == '"') sb.append("&quot;");
            else if (cp == '\t' || cp == '\n' || cp == '\r' || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}
