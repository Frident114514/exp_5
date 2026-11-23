-- M04 候选迁移。由 M01 登记正式版本号，须在 users 和 posts 之后应用。
CREATE TABLE ratings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ratings_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_ratings_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_ratings_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_ratings_score CHECK (score BETWEEN 1 AND 5),
    INDEX idx_ratings_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_comments_content_length CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 500),
    CONSTRAINT ck_comments_status CHECK (status IN ('ACTIVE', 'DELETED')),
    INDEX idx_comments_post_page (post_id, status, created_at DESC, id DESC),
    INDEX idx_comments_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
