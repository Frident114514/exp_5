package edu.szu.moments.auth;

import edu.szu.moments.interaction.InteractionService;
import edu.szu.moments.posts.AuthorPosts;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserDisabledCleanup {
    private final AuthorPosts authorPosts;
    private final InteractionService interactions;

    UserDisabledCleanup(AuthorPosts authorPosts, InteractionService interactions) {
        this.authorPosts = authorPosts;
        this.interactions = interactions;
    }

    @EventListener
    @org.springframework.core.annotation.Order(0)
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserDisabled(UserDisabled event) {
        authorPosts.hideByAuthor(event.userId());
        interactions.cleanupDisabledUser(event.userId());
    }
}
