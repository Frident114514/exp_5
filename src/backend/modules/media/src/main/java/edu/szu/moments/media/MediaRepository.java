package edu.szu.moments.media;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<MediaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MomentMedia m where m.id in :ids order by m.id asc")
    List<MediaEntity> lockByIds(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MomentMedia m where m.postId = :postId and m.status = edu.szu.moments.posts.PostModels$MediaStatus.ACTIVE order by m.id asc")
    List<MediaEntity> lockActiveByPostId(@Param("postId") long postId);

    @Query("select m from MomentMedia m where m.postId in :postIds and m.status = edu.szu.moments.posts.PostModels$MediaStatus.ACTIVE order by m.postId asc, m.sortOrder asc, m.id asc")
    List<MediaEntity> findActiveByPostIds(@Param("postIds") Collection<Long> postIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MomentMedia m where m.status = edu.szu.moments.posts.PostModels$MediaStatus.PENDING and m.updatedAt <= :cutoff order by m.id asc")
    List<MediaEntity> lockExpiredPending(@Param("cutoff") Instant cutoff);
}
