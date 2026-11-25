package edu.szu.moments.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
class UserAccount {
    enum Role { USER, ADMIN }
    enum Status { ACTIVE, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String account;
    @Column(nullable = false, length = 60)
    private String passwordHash;
    @Column(nullable = false, length = 32)
    private String nickname;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Column(nullable = false)
    private int tokenVersion;
    @Column(nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    UserAccount(String account, String passwordHash, String nickname) {
        this.account = account;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = Role.USER;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    Long id() { return id; }
    String passwordHash() { return passwordHash; }
    Role role() { return role; }
    boolean active() { return status == Status.ACTIVE; }
    int tokenVersion() { return tokenVersion; }
    String account() { return account; }
    String nickname() { return nickname; }
    Status status() { return status; }
    Instant createdAt() { return createdAt; }
    void changePassword(String hash) {
        passwordHash = hash;
        tokenVersion = Math.incrementExact(tokenVersion);
    }
    void disable() {
        status = Status.DISABLED;
        tokenVersion = Math.incrementExact(tokenVersion);
    }
    void makeAdmin() { role = Role.ADMIN; }
    UserProfile profile() { return new UserProfile(id, account, nickname, role.name()); }
}
