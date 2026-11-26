package edu.szu.moments.interaction;

import java.time.Instant;

import edu.szu.moments.posts.PostModels.Author;
import edu.szu.moments.posts.PostModels.RatingSummary;

public final class InteractionModels {
    private InteractionModels() {}

    public record RatingResult(int score, RatingSummary rating) {}

    public record CommentView(long id, Author author, String content, Instant createdAt) {}
}
