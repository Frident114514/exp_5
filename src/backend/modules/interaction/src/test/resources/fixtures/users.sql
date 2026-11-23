-- M04 独立迁移测试夹具。正式 users 表由 M01 提供。
CREATE TABLE users (
    id BIGINT NOT NULL PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
