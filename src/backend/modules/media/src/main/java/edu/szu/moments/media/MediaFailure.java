package edu.szu.moments.media;

import edu.szu.moments.posts.PostFailure;

public class MediaFailure extends PostFailure {
    private final Long totalSize;

    public MediaFailure(int status, String code, String message) {
        this(status, code, message, null);
    }

    public MediaFailure(int status, String code, String message, Long totalSize) {
        super(status, code, message);
        this.totalSize = totalSize;
    }

    public Long totalSize() {
        return totalSize;
    }

    public static MediaFailure invalidFile(String message) {
        return new MediaFailure(400, "VALIDATION_ERROR", message);
    }

    public static MediaFailure fileTooLarge(String message) {
        return new MediaFailure(413, "FILE_TOO_LARGE", message);
    }

    public static MediaFailure unsupportedMediaType(String message) {
        return new MediaFailure(415, "UNSUPPORTED_MEDIA_TYPE", message);
    }

    public static MediaFailure notFound() {
        return new MediaFailure(404, "NOT_FOUND", "媒体不存在或已不可见");
    }

    public static MediaFailure rangeNotSatisfiable(long totalSize) {
        return new MediaFailure(416, "RANGE_NOT_SATISFIABLE", "请求字节范围不可满足", totalSize);
    }

    public static MediaFailure storageFailure(String message) {
        return new MediaFailure(500, "INTERNAL_ERROR", message);
    }
}
