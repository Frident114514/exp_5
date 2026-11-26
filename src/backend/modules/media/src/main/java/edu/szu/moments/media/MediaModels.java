package edu.szu.moments.media;

import java.time.Instant;
import java.util.List;

import edu.szu.moments.posts.PostModels.MediaStatus;
import edu.szu.moments.posts.PostModels.MediaType;

public final class MediaModels {
    private MediaModels() {}

    public record UploadedMedia(
        long mediaId,
        long id,
        MediaType type,
        String mimeType,
        long size,
        String url,
        MediaStatus status,
        Instant createdAt
    ) {}

    public record InspectedFile(
        MediaType type,
        String mimeType,
        String extension,
        String originalName,
        long size
    ) {}

    public record ByteRange(long start, long end, long total) {
        public long length() {
            return end - start + 1;
        }

        public String contentRangeHeader() {
            return "bytes " + start + "-" + end + "/" + total;
        }
    }

    public record MediaStream(
        String mimeType,
        long totalSize,
        ByteRange range,
        boolean partial,
        byte[] content
    ) {}

    public record CleanupSummary(int cleanedCount, List<Long> mediaIds) {}
}
