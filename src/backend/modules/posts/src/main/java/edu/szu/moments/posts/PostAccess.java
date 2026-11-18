package edu.szu.moments.posts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.szu.moments.posts.PostModels.PostState;

/** M03/M04 的轻量读取入口，不依赖媒体或互动服务，避免循环依赖。 */
@Service
public class PostAccess {
    private final PostRepository posts;
    private final AuthorRepository authors;

    public PostAccess(PostRepository posts, AuthorRepository authors) {
        this.posts = posts;
        this.authors = authors;
    }

    @Transactional(readOnly = true)
    public PostState requireVisible(long id) {
        PostEntity post = visibleEntity(id);
        return new PostState(post.getId(), post.getAuthorId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PostState lockVisibleForInteraction(long id) {
        PostEntity post = lockVisibleEntity(id);
        return new PostState(post.getId(), post.getAuthorId());
    }

    PostEntity visibleEntity(long id) {
        return check(posts.findById(id).orElseThrow(PostFailure::missing));
    }

    PostEntity lockVisibleEntity(long id) {
        return check(posts.lockById(id).orElseThrow(PostFailure::missing));
    }

    private PostEntity check(PostEntity post) {
        if (post.getStatus() != PostEntity.Status.ACTIVE
                || !authors.existsByIdAndStatus(post.getAuthorId(), "ACTIVE")) {
            throw PostFailure.missing();
        }
        return post;
    }
}

