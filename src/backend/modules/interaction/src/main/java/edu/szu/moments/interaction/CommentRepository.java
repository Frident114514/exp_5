package edu.szu.moments.interaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    Page<CommentEntity> findByPostIdAndStatus(long postId, CommentEntity.Status status, Pageable pageable);

    long countByPostIdAndStatus(long postId, CommentEntity.Status status);

    @Modifying(flushAutomatically = true)
    @Query("update CommentEntity c set c.status = edu.szu.moments.interaction.CommentEntity$Status.DELETED "
        + "where c.userId = :userId and c.status = edu.szu.moments.interaction.CommentEntity$Status.ACTIVE")
    int softDeleteActiveByUserId(@Param("userId") long userId);
}
