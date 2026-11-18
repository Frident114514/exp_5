package edu.szu.moments.posts;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.*;

@Entity(name = "MomentPost")
@Table(name = "posts")
public class PostEntity {
    public enum Status { ACTIVE, DELETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "post_tags",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<TagEntity> tags = new LinkedHashSet<>();

    protected PostEntity() {}

    PostEntity(long authorId, String text, Instant now) {
        this.authorId = authorId;
        this.text = text;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public long getAuthorId() { return authorId; }
    public String getText() { return text; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void edit(String text, Set<TagEntity> tags, Instant now) {
        this.text = text;
        if (tags != null) {
            this.tags.clear();
            this.tags.addAll(tags);
        }
        this.updatedAt = now;
    }

    void delete(Instant now) {
        this.status = Status.DELETED;
        this.updatedAt = now;
    }
}

