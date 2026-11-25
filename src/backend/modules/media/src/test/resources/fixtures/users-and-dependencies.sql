-- 仅测试夹具：提供 M01 用户表及 M02 所需的评分/审计夹具，配合 M02 posts.sql 与 M03 media.sql 验证完整外键与跨模块事务。
CREATE TABLE users (
    id BIGINT NOT NULL PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

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
