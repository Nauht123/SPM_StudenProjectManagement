package vn.huflit.spm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Luu/doc file upload tren dia (dung chung cho bai nop milestone va ket qua task). */
@Component
public class FileStorage {
    public record StoredFile(String originalName, String path) {
    }

    private final Path root;

    public FileStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile save(String folder, MultipartFile file) {
        String original = sanitize(file.getOriginalFilename());
        Path dir = root.resolve(folder).normalize();
        Path target = dir.resolve(UUID.randomUUID() + "_" + original).normalize();
        if (!dir.startsWith(root) || !target.startsWith(root)) {
            throw ApiException.badRequest("Tên file không hợp lệ");
        }
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được file");
        }
        return new StoredFile(original, root.relativize(target).toString());
    }

    public Resource load(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) {
            throw ApiException.notFound("Không tìm thấy file trên máy chủ");
        }
        return new FileSystemResource(p);
    }

    static String sanitize(String name) {
        String base = name == null ? "file" : Paths.get(name).getFileName().toString();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return cleaned.isBlank() ? "file" : cleaned;
    }
}
