package edu.szu.moments.auth;

import edu.szu.moments.posts.IdentityPort;
import edu.szu.moments.posts.PostFailure;
import edu.szu.moments.posts.PostModels.Actor;
import edu.szu.moments.posts.PostModels.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class PostIdentityAdapter implements IdentityPort {
    private final UserRepository users;

    PostIdentityAdapter(UserRepository users) {
        this.users = users;
    }

    @Override
    public Actor requireActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserProfile profile)) {
            throw PostFailure.unauthenticated();
        }
        return new Actor(profile.id(), Role.valueOf(profile.role()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActiveActor(Actor actor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserProfile profile)
                || !(authentication.getDetails() instanceof Number version)
                || profile.id() != actor.id()) {
            throw PostFailure.unauthenticated();
        }
        UserAccount user = users.lockById(actor.id()).orElseThrow(PostFailure::unauthenticated);
        if (!user.active() || user.tokenVersion() != version.longValue()
                || !user.role().name().equals(actor.role().name())) {
            throw PostFailure.unauthenticated();
        }
    }
}
