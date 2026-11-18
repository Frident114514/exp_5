package edu.szu.moments.posts;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M01 禁用用户流程调用，调用方须先锁用户行并在同一事务完成互动与令牌处理。 */
@Service
public class AuthorPosts {
    private final PostRepository posts;
    private final Clock clock;

    public AuthorPosts(PostRepository posts, @Qualifier("postsClock") Clock clock) {
        this.posts = posts;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int hideByAuthor(long authorId) {
        var records = posts.lockActiveByAuthor(authorId);
        var now = clock.instant();
        records.forEach(post -> post.delete(now));
        return records.size();
    }
}

