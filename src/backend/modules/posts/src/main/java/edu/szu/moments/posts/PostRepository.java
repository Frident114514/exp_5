package edu.szu.moments.posts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<PostEntity, Long>, JpaSpecificationExecutor<PostEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MomentPost p where p.id = :id")
    Optional<PostEntity> lockById(@Param("id") long id);

    /** Shared visibility lock for interactions; status-changing writes use lockById. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from MomentPost p where p.id = :id")
    Optional<PostEntity> lockByIdForInteraction(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MomentPost p where p.authorId = :authorId and p.status = edu.szu.moments.posts.PostEntity$Status.ACTIVE order by p.id")
    List<PostEntity> lockActiveByAuthor(@Param("authorId") long authorId);

    @Query("select new edu.szu.moments.posts.PostModels$TagAssignment(p.id, t.name) from MomentPost p join p.tags t where p.id in :ids order by t.name")
    List<PostModels.TagAssignment> findTagAssignments(@Param("ids") Collection<Long> ids);
}

