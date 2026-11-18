package edu.szu.moments.posts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PostModels {
    private PostModels() {}

    public enum Role { USER, ADMIN }
    public enum MediaType { IMAGE, VIDEO }
    public enum MediaStatus { PENDING, ACTIVE, DELETED }

    public record Actor(long id, Role role) {}
    public record Author(long id, String nickname) {}
    public record CreatedPost(long id) {}
    public record MediaCandidate(long id, long uploadedBy, Long postId,
                                 MediaType type, MediaStatus status) {}
    public record MediaView(long id, MediaType type, String url) {}
    public record RatingSummary(BigDecimal average, long count) {
        public static RatingSummary empty() {
            return new RatingSummary(null, 0);
        }
    }
    public record PostView(long id, Author author, String text, List<MediaView> media,
                           List<String> tags, Instant createdAt, Instant updatedAt,
                           RatingSummary rating) {}
    public record PageView<T>(List<T> items, int page, int pageSize, long total) {}
    public record PostState(long id, long authorId) {}
    public record TagAssignment(long postId, String name) {}
}

