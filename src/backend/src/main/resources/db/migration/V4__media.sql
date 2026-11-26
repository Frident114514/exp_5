-- M03 候选迁移：正式版本号由 M01 合入前登记。前置：users(id BIGINT)、posts(id BIGINT)。
CREATE TABLE media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_media_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_media_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_media_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT ck_media_type CHECK (type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_media_status CHECK (status IN ('PENDING', 'ACTIVE', 'DELETED')),
    INDEX idx_media_post_sort (post_id, status, sort_order, id),
    INDEX idx_media_status_updated (status, updated_at, id),
    INDEX idx_media_uploader_created (uploaded_by, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
