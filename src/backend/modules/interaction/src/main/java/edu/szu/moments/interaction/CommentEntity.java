package edu.szu.moments.interaction;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class CommentEntity {
    public enum Status { ACTIVE, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private long postId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentEntity() {}

    CommentEntity(long postId, long userId, String content, Instant now) {
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.status = Status.ACTIVE;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public long getPostId() { return postId; }
    public long getUserId() { return userId; }
    public String getContent() { return content; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
