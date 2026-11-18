package edu.szu.moments.posts;

import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

final class PostSpecifications {
    private PostSpecifications() {}

    static Specification<PostEntity> visible(PostFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("status"), PostEntity.Status.ACTIVE));
            var author = query.subquery(Long.class);
            var users = author.from(AuthorRecord.class);
            author.select(users.get("id")).where(cb.equal(users.get("id"), root.get("authorId")),
                cb.equal(users.get("status"), "ACTIVE"));
            predicates.add(cb.exists(author));
            if (filter.authorId() != null) {
                predicates.add(cb.equal(root.get("authorId"), filter.authorId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.until() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.until()));
            }
            if (filter.tag() != null) {
                // 仅一次标签条件，post_tags 的复合主键保证每条动态最多一行匹配。
                predicates.add(cb.equal(root.join("tags").get("name"), filter.tag()));
            }
            if (filter.keyword() != null) {
                String keyword = filter.keyword().replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(root.get("text"), "%" + keyword + "%", '!'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

