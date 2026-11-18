package edu.szu.moments.posts;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface AuthorRepository extends Repository<AuthorRecord, Long> {
    List<AuthorRecord> findByIdInAndStatus(Collection<Long> ids, String status);
    boolean existsByIdAndStatus(long id, String status);
}

