package vn.huflit.spm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Cai dat that bang java.net.http. Loi mang/quyen duoc doi thanh ApiException co thong bao tieng Viet. */
@Component
public class HttpGithubClient implements GithubClient {
    private static final String API = "https://api.github.com";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PAGE = 100;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public RepoInfo verify(String owner, String repo, String token) {
        JsonNode n = get(API + "/repos/" + owner + "/" + repo, token);
        return new RepoInfo(n.path("full_name").asText(owner + "/" + repo), n.path("private").asBoolean(),
                n.path("default_branch").asText(""));
    }

    @Override
    public List<Commit> commits(String owner, String repo, String token, LocalDateTime since) {
        List<Commit> out = new ArrayList<>();
        String sinceParam = since == null ? ""
                : "&since=" + DateTimeFormatter.ISO_INSTANT.format(since.atZone(ZONE).toInstant());
        for (int page = 1; page <= 5; page++) {
            JsonNode arr = get(API + "/repos/" + owner + "/" + repo + "/commits?per_page=" + PAGE
                    + "&page=" + page + sinceParam, token);
            if (!arr.isArray()) {
                break;
            }
            arr.forEach(n -> out.add(parseCommit(n)));
            if (arr.size() < PAGE) {
                break;
            }
        }
        return out;
    }

    @Override
    public List<Pull> pulls(String owner, String repo, String token) {
        List<Pull> out = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            JsonNode arr = get(API + "/repos/" + owner + "/" + repo
                    + "/pulls?state=all&sort=updated&direction=desc&per_page=" + PAGE + "&page=" + page, token);
            if (!arr.isArray()) {
                break;
            }
            arr.forEach(n -> out.add(parsePull(n)));
            if (arr.size() < PAGE) {
                break;
            }
        }
        return out;
    }

    public static Commit parseCommit(JsonNode n) {
        JsonNode c = n.path("commit");
        String message = c.path("message").asText("");
        int nl = message.indexOf('\n');
        String firstLine = (nl >= 0 ? message.substring(0, nl) : message).strip();
        if (firstLine.length() > 490) {
            firstLine = firstLine.substring(0, 490);
        }
        JsonNode login = n.path("author").path("login");
        return new Commit(n.path("sha").asText(), login.isMissingNode() || login.isNull() ? null : login.asText(),
                c.path("author").path("name").asText(null), firstLine,
                parseTime(c.path("author").path("date").asText(null)), n.path("html_url").asText(null));
    }

    public static Pull parsePull(JsonNode n) {
        LocalDateTime merged = parseTime(n.path("merged_at").asText(null));
        String state = merged != null ? "MERGED" : "open".equals(n.path("state").asText()) ? "OPEN" : "CLOSED";
        String title = n.path("title").asText("");
        return new Pull(n.path("number").asInt(), title.length() > 290 ? title.substring(0, 290) : title, state,
                n.path("user").path("login").asText(null), parseTime(n.path("created_at").asText(null)),
                parseTime(n.path("closed_at").asText(null)), merged, n.path("html_url").asText(null));
    }

    /** Doi thoi gian ISO-8601 cua GitHub (UTC) sang gio Viet Nam; null neu khong co. */
    public static LocalDateTime parseTime(String iso) {
        if (iso == null || iso.isBlank() || "null".equals(iso)) {
            return null;
        }
        return OffsetDateTime.parse(iso).atZoneSameInstant(ZONE).toLocalDateTime();
    }

    private JsonNode get(String url, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "spm-student-project-management");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> res = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) {
                return json.readTree(res.body());
            }
            if (code == 409) { // repo rong
                return json.createArrayNode();
            }
            if (code == 401) {
                throw ApiException.badRequest("Token GitHub không hợp lệ hoặc đã hết hạn");
            }
            if (code == 403 || code == 429) {
                throw ApiException.badRequest("GitHub từ chối yêu cầu (hết giới hạn truy cập hoặc token thiếu quyền). Thử lại sau ít phút");
            }
            if (code == 404) {
                throw ApiException.badRequest("Không tìm thấy repo trên GitHub (sai tên, hoặc repo riêng tư mà token không có quyền)");
            }
            throw ApiException.badRequest("GitHub trả về lỗi " + code);
        } catch (IOException e) {
            throw ApiException.badRequest("Không kết nối được tới GitHub: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.badRequest("Yêu cầu tới GitHub bị gián đoạn");
        }
    }

}
