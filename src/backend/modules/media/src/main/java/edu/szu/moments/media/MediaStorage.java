package edu.szu.moments.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaStorage {
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        .withZone(ZoneOffset.UTC);

    private final MediaProperties properties;

    public MediaStorage(MediaProperties properties) {
        this.properties = properties;
    }

    public Path rootDir() {
        return Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
    }

    public String generateStorageKey(String safeExtension, Instant now) {
        return DATE_DIR.format(now) + "/" + UUID.randomUUID().toString().toLowerCase() + "." + safeExtension;
    }

    public Path resolveSafePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..") || storageKey.startsWith("/")) {
            throw MediaFailure.invalidFile("非法的媒体存储键");
        }
        Path root = rootDir();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw MediaFailure.invalidFile("检测到非法的媒体目录访问");
        }
        return resolved;
    }

    public String store(MultipartFile file, String safeExtension, Instant now) {
        String storageKey = generateStorageKey(safeExtension, now);
        Path target = resolveSafePath(storageKey);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
            return storageKey;
        } catch ( Exception ex) {
            deleteIfExists(storageKey);
            throw MediaFailure.storageFailure("媒体文件写入失败");
        }
    }

    public boolean exists(String storageKey) {
        try {
            Path path = resolveSafePath(storageKey);
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    public byte[] readRange(String storageKey, long start, long length) {
        Path path = resolveSafePath(storageKey);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw MediaFailure.notFound();
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(start);
            int count = Math.toIntExact(length);
            byte[] buffer = new byte[count];
            int offset = 0;
            while (offset < count) {
                int read = raf.read(buffer, offset, count - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset < count) {
                throw MediaFailure.storageFailure("媒体文件字节长度异常");
            }
            return buffer;
        } catch (MediaFailure ex) {
            throw ex;
        } catch (IOException ex) {
            throw MediaFailure.storageFailure("媒体文件读取失败");
        }
    }

    public boolean deleteIfExists(String storageKey) {
        try {
            return Files.deleteIfExists(resolveSafePath(storageKey));
        } catch (Exception ignored) {
            return false;
        }
    }
}
