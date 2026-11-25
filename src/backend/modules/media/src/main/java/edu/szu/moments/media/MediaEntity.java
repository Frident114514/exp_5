package edu.szu.moments.media;

import java.time.Instant;

import edu.szu.moments.posts.PostModels.MediaStatus;
import edu.szu.moments.posts.PostModels.MediaType;
import jakarta.persistence.*;

@Entity(name = "MomentMedia")
@Table(name = "media")
public class MediaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "uploaded_by", nullable = false)
    private long uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaType type;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaEntity() {}

    public MediaEntity(long uploadedBy, MediaType type, String mimeType, String originalName,
                       long fileSize, String storageKey, Instant now) {
        this.uploadedBy = uploadedBy;
        this.type = type;
        this.mimeType = mimeType;
        this.originalName = originalName;
        this.fileSize = fileSize;
        this.storageKey = storageKey;
        this.status = MediaStatus.PENDING;
        this.sortOrder = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public long getUploadedBy() { return uploadedBy; }
    public MediaType getType() { return type; }
    public String getMimeType() { return mimeType; }
    public String getOriginalName() { return originalName; }
    public long getFileSize() { return fileSize; }
    public String getStorageKey() { return storageKey; }
    public MediaStatus getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void bindToPost(long postId, int sortOrder, Instant now) {
        this.postId = postId;
        this.status = MediaStatus.ACTIVE;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public void unbindToPending(Instant now) {
        this.postId = null;
        this.status = MediaStatus.PENDING;
        this.sortOrder = 0;
        this.updatedAt = now;
    }

    public void markDeleted(Instant now) {
        this.postId = null;
        this.status = MediaStatus.DELETED;
        this.sortOrder = 0;
        this.updatedAt = now;
    }
}
