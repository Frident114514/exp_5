package edu.szu.moments.posts;

import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<TagEntity, Long> {
    @Modifying
    @Query(value = "INSERT INTO tags(name) VALUES (:name) ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void insertIfAbsent(@Param("name") String name);

    // upsert 可能等待另一个事务新建标签；锁定读取避免 RR 旧快照漏掉刚提交的标签。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TagEntity> findByNameIn(Collection<String> names);
}
