package edu.szu.moments.media;

import edu.szu.moments.media.MediaModels.ByteRange;

public final class RangeParser {
    private RangeParser() {}

    public static ByteRange parse(String header, long totalSize) {
        if (totalSize <= 0) {
            throw MediaFailure.rangeNotSatisfiable(totalSize);
        }
        if (header == null || header.isBlank()) {
            return new ByteRange(0, totalSize - 1, totalSize);
        }
        String trimmed = header.trim();
        if (!trimmed.startsWith("bytes=")) {
            throw MediaFailure.rangeNotSatisfiable(totalSize);
        }
        String spec = trimmed.substring("bytes=".length()).trim();
        if (spec.isEmpty() || spec.contains(",")) {
            throw MediaFailure.rangeNotSatisfiable(totalSize);
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            throw MediaFailure.rangeNotSatisfiable(totalSize);
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (startPart.isEmpty()) {
                if (endPart.isEmpty()) {
                    throw MediaFailure.rangeNotSatisfiable(totalSize);
                }
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    throw MediaFailure.rangeNotSatisfiable(totalSize);
                }
                start = Math.max(0, totalSize - suffix);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(startPart);
                if (start < 0 || start >= totalSize) {
                    throw MediaFailure.rangeNotSatisfiable(totalSize);
                }
                if (endPart.isEmpty()) {
                    end = totalSize - 1;
                } else {
                    end = Long.parseLong(endPart);
                    if (end < start) {
                        throw MediaFailure.rangeNotSatisfiable(totalSize);
                    }
                    if (end >= totalSize) {
                        end = totalSize - 1;
                    }
                }
            }
            return new ByteRange(start, end, totalSize);
        } catch (NumberFormatException ex) {
            throw MediaFailure.rangeNotSatisfiable(totalSize);
        }
    }
}
