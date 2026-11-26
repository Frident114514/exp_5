package edu.szu.moments.interaction;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import edu.szu.moments.posts.PostFailure;

final class InteractionPage {
    private InteractionPage() {}

    static PageRequest of(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50
                || (long) (page - 1) * pageSize > Integer.MAX_VALUE) {
            throw PostFailure.invalid("分页参数超出范围");
        }
        return PageRequest.of(page - 1, pageSize,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    static void requirePostId(long postId) {
        if (postId < 1) {
            throw PostFailure.invalid("动态 ID 必须为正整数");
        }
    }
}
