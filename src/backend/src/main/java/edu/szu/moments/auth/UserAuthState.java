package edu.szu.moments.auth;

/** JWT validation never needs the password hash or the full managed user entity. */
public record UserAuthState(Long id, String account, String nickname,
                            UserAccount.Role role, UserAccount.Status status, int tokenVersion) {
    boolean active() { return status == UserAccount.Status.ACTIVE; }
    UserProfile profile() { return new UserProfile(id, account, nickname, role.name()); }
}
