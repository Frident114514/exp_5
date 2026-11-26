package edu.szu.moments.interaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<RatingEntity, Long> {
    /**
     * The unique key serializes duplicate submissions for one user and post.
     * This avoids a check-then-insert race without a JVM-wide lock.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO ratings(post_id, user_id, score, created_at, updated_at) "
        + "VALUES (:postId, :userId, :score, :now, :now) "
        + "ON DUPLICATE KEY UPDATE score = VALUES(score), updated_at = VALUES(updated_at)",
        nativeQuery = true)
    int upsert(@Param("postId") long postId, @Param("userId") long userId,
               @Param("score") int score, @Param("now") Instant now);

    @Query(value = "SELECT post_id AS postId, AVG(score) AS average, COUNT(*) AS ratingCount "
        + "FROM ratings WHERE post_id IN (:postIds) GROUP BY post_id", nativeQuery = true)
    List<RatingAggregate> summarize(@Param("postIds") Collection<Long> postIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from RatingEntity r where r.userId = :userId")
    int deleteByUserId(@Param("userId") long userId);

    interface RatingAggregate {
        Long getPostId();
        BigDecimal getAverage();
        Long getRatingCount();
    }
}
