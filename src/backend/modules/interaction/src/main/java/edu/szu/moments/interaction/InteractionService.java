package edu.szu.moments.interaction;

import static edu.szu.moments.interaction.InteractionModels.*;
import static edu.szu.moments.posts.PostModels.*;

import java.math.RoundingMode;
import java.time.Clock;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.szu.moments.posts.AuthorRecord;
import edu.szu.moments.posts.AuthorRepository;
import edu.szu.moments.posts.IdentityPort;
import edu.szu.moments.posts.PostAccess;
import edu.szu.moments.posts.PostFailure;
import edu.szu.moments.posts.PostModels.Actor;
import edu.szu.moments.posts.PostModels.PostState;
import edu.szu.moments.posts.PostModels.Role;
import edu.szu.moments.posts.RatingPort;

@Service
@Transactional(readOnly = true)
public class InteractionService implements RatingPort {
    private final RatingRepository ratings;
    private final CommentRepository comments;
    private final AuthorRepository authors;
    private final PostAccess posts;
    private final IdentityPort identity;
    private final Clock clock;

    public InteractionService(RatingRepository ratings, CommentRepository comments,
                              AuthorRepository authors, PostAccess posts,
                              IdentityPort identity, @Qualifier("interactionClock") Clock clock) {
        this.ratings = ratings;
        this.comments = comments;
        this.authors = authors;
        this.posts = posts;
        this.identity = identity;
        this.clock = clock;
    }

    @Transactional
    public RatingResult rate(long postId, int score) {
        InteractionPage.requirePostId(postId);
        if (score < 1 || score > 5) {
            throw PostFailure.invalid("score 必须为 1–5 的 JSON 整数");
        }
        Actor actor = requireUser();
        identity.lockActiveActor(actor);
        PostState post = posts.lockVisibleForInteraction(postId);
        rejectAuthor(actor, post);

        ratings.upsert(postId, actor.id(), score, clock.instant());
        return new RatingResult(score, summarize(Set.of(postId)).get(postId));
    }

    @Transactional
    public CommentView addComment(long postId, String content) {
        InteractionPage.requirePostId(postId);
        String normalized = normalizeComment(content);
        Actor actor = requireUser();
        identity.lockActiveActor(actor);
        PostState post = posts.lockVisibleForInteraction(postId);
        rejectAuthor(actor, post);

        CommentEntity saved = comments.saveAndFlush(
            new CommentEntity(postId, actor.id(), normalized, clock.instant()));
        Author author = author(actor.id());
        if (author == null) {
            throw PostFailure.dependency();
        }
        return new CommentView(saved.getId(), author, saved.getContent(), saved.getCreatedAt());
    }

    public PageView<CommentView> listComments(long postId, int page, int pageSize) {
        InteractionPage.requirePostId(postId);
        var pageable = InteractionPage.of(page, pageSize);
        posts.requireVisible(postId);
        Page<CommentEntity> result = comments.findByPostIdAndStatus(
            postId, CommentEntity.Status.ACTIVE, pageable);
        Map<Long, Author> authorMap = authorMap(result.getContent().stream()
            .map(CommentEntity::getUserId).collect(Collectors.toSet()));
        var items = result.getContent().stream().map(comment -> {
            Author author = authorMap.get(comment.getUserId());
            if (author == null) {
                throw PostFailure.dependency();
            }
            return new CommentView(comment.getId(), author, comment.getContent(), comment.getCreatedAt());
        }).toList();
        return new PageView<>(items, page, pageSize, result.getTotalElements());
    }

    @Override
    public Map<Long, RatingSummary> summarize(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = postIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, RatingSummary> result = new HashMap<>();
        ids.forEach(id -> result.put(id, RatingSummary.empty()));
        if (ids.isEmpty()) {
            return result;
        }
        for (RatingRepository.RatingAggregate row : ratings.summarize(ids)) {
            BigDecimal average = row.getAverage().setScale(1, RoundingMode.HALF_UP);
            result.put(row.getPostId(), new RatingSummary(average, row.getRatingCount()));
        }
        return result;
    }

    /**
     * Called by M01's UserDisabled listener inside the existing user-disable transaction.
     * MANDATORY prevents partial cleanup in a separate transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cleanupDisabledUser(long userId) {
        if (userId < 1) {
            throw PostFailure.invalid("用户 ID 必须为正整数");
        }
        int deletedComments = comments.softDeleteActiveByUserId(userId);
        ratings.deleteByUserId(userId);
        return deletedComments;
    }

    private Actor requireUser() {
        Actor actor = identity.requireActor();
        if (actor == null || actor.id() < 1 || actor.role() == null) {
            throw PostFailure.unauthenticated();
        }
        if (actor.role() != Role.USER) {
            throw PostFailure.forbidden("管理员不能评分或评论");
        }
        return actor;
    }

    private void rejectAuthor(Actor actor, PostState post) {
        if (post.authorId() == actor.id()) {
            throw PostFailure.forbidden("不能对本人动态评分或评论");
        }
    }

    private Author author(long id) {
        return authorMap(Set.of(id)).get(id);
    }

    private Map<Long, Author> authorMap(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return authors.findByIdInAndStatus(ids, "ACTIVE").stream()
            .collect(Collectors.toMap(AuthorRecord::getId,
                author -> new Author(author.getId(), author.getNickname())));
    }

    private static String normalizeComment(String value) {
        if (value == null) {
            throw PostFailure.invalid("content 必须为字符串");
        }
        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 500) {
            throw PostFailure.invalid("评论长度须为 1–500 字符");
        }
        return normalized;
    }
}
