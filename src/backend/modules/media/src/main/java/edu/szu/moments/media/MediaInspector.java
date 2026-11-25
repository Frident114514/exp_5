package edu.szu.moments.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import edu.szu.moments.media.MediaModels.InspectedFile;
import edu.szu.moments.posts.PostModels.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaInspector {
    private static final Map<String, Set<String>> EXTENSIONS_BY_MIME = Map.of(
        "image/jpeg", Set.of("jpg", "jpeg"),
        "image/png", Set.of("png"),
        "image/webp", Set.of("webp"),
        "video/mp4", Set.of("mp4")
    );

    private final MediaProperties properties;

    public MediaInspector(MediaProperties properties) {
        this.properties = properties;
    }

    public InspectedFile inspect(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw MediaFailure.invalidFile("上传文件不能为空");
        }

        String rawFilename = sanitizeOriginalName(file.getOriginalFilename());
        String declaredMime = normalizeMime(file.getContentType());
        if (!declaredMime.isEmpty() && !EXTENSIONS_BY_MIME.containsKey(declaredMime)) {
            throw MediaFailure.unsupportedMediaType("不支持的文件格式，仅允许 JPEG/PNG/WebP 图片或 MP4 视频");
        }

        byte[] header = readHeader(file, 16);
        DetectedSignature detected = detectSignature(header);
        if (detected == null) {
            throw MediaFailure.unsupportedMediaType("文件内容校验未通过，仅允许合法的 JPEG/PNG/WebP 图片或 MP4 视频");
        }

        if (!declaredMime.isEmpty() && !declaredMime.equals(detected.mimeType())) {
            throw MediaFailure.unsupportedMediaType("声明的 MIME 类型与实际文件内容不一致");
        }

        String ext = extractExtension(rawFilename);
        if (!ext.isEmpty() && !EXTENSIONS_BY_MIME.get(detected.mimeType()).contains(ext)) {
            throw MediaFailure.unsupportedMediaType("文件扩展名与实际文件类型不匹配");
        }

        long size = file.getSize();
        if (detected.type() == MediaType.IMAGE && size > properties.getMaxImageBytes()) {
            throw MediaFailure.fileTooLarge("图片大小不能超过 10 MB");
        }
        if (detected.type() == MediaType.VIDEO && size > properties.getMaxVideoBytes()) {
            throw MediaFailure.fileTooLarge("视频大小不能超过 50 MB");
        }

        String finalOriginalName = rawFilename.isEmpty() ? ("unnamed." + detected.extension()) : rawFilename;
        return new InspectedFile(detected.type(), detected.mimeType(), detected.extension(), finalOriginalName, size);
    }

    private record DetectedSignature(MediaType type, String mimeType, String extension) {}

    private byte[] readHeader(MultipartFile file, int maxBytes) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(maxBytes);
        } catch (IOException ex) {
            throw MediaFailure.invalidFile("无法读取上传文件内容");
        }
    }

    private DetectedSignature detectSignature(byte[] h) {
        if (h == null || h.length < 4) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return new DetectedSignature(MediaType.IMAGE, "image/jpeg", "jpg");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (h.length >= 8
                && (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return new DetectedSignature(MediaType.IMAGE, "image/png", "png");
        }
        // WebP: "RIFF" + 4-byte size + "WEBP"
        if (h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return new DetectedSignature(MediaType.IMAGE, "image/webp", "webp");
        }
        // MP4: ISO Base Media file format ("ftyp" at offset 4..7)
        if (h.length >= 8) {
            String box = new String(h, 4, 4, StandardCharsets.US_ASCII);
            if ("ftyp".equals(box)) {
                return new DetectedSignature(MediaType.VIDEO, "video/mp4", "mp4");
            }
        }
        return null;
    }

    private String normalizeMime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(';');
        if (idx >= 0) {
            lower = lower.substring(0, idx).trim();
        }
        if ("image/jpg".equals(lower)) {
            return "image/jpeg";
        }
        return lower;
    }

    private String sanitizeOriginalName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.replaceAll("[\\r\\n\\t\\x00]", "").trim();
        if (".".equals(normalized) || "..".equals(normalized)) {
            return "";
        }
        if (normalized.length() > 200) {
            normalized = normalized.substring(normalized.length() - 200);
        }
        return normalized;
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
