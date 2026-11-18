-- 仅测试夹具：M01/M03/M04 尚未交付，不代表其正式表或服务已经实现。
CREATE TABLE users (
    id BIGINT NOT NULL PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE fixture_media (
    id BIGINT NOT NULL PRIMARY KEY,
    post_id BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE fixture_ratings (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    PRIMARY KEY (post_id, user_id)
) ENGINE=InnoDB;
CREATE TABLE fixture_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

