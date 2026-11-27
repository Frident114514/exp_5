CREATE TABLE friend_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_low BIGINT NOT NULL,
    user_high BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    handled_at DATETIME(6) NULL,
    pending_key TINYINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN 1 ELSE NULL END) STORED,
    CONSTRAINT fk_friend_request_low FOREIGN KEY (user_low) REFERENCES users(id),
    CONSTRAINT fk_friend_request_high FOREIGN KEY (user_high) REFERENCES users(id),
    CONSTRAINT ck_friend_request_pair CHECK (user_low < user_high AND
      ((requester_id=user_low AND recipient_id=user_high) OR (requester_id=user_high AND recipient_id=user_low))),
    CONSTRAINT ck_friend_request_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    UNIQUE KEY uq_friend_pending (user_low,user_high,pending_key),
    INDEX idx_friend_incoming (recipient_id,status,id DESC),
    INDEX idx_friend_outgoing (requester_id,status,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE friendships (
    user_low BIGINT NOT NULL,
    user_high BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_low,user_high),
    CONSTRAINT fk_friend_low FOREIGN KEY (user_low) REFERENCES users(id),
    CONSTRAINT fk_friend_high FOREIGN KEY (user_high) REFERENCES users(id),
    CONSTRAINT ck_friend_pair CHECK (user_low < user_high),
    INDEX idx_friend_high (user_high,user_low)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE daily_user_activity (
    activity_day DATE NOT NULL,
    user_id BIGINT NOT NULL,
    first_active_at DATETIME(6) NOT NULL,
    last_active_at DATETIME(6) NOT NULL,
    PRIMARY KEY (activity_day,user_id),
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE activity_tracking (
    id INT NOT NULL PRIMARY KEY,
    started_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_activity_tracking_singleton CHECK (id=1)
) ENGINE=InnoDB;
INSERT INTO activity_tracking(id,started_at) VALUES (1,UTC_TIMESTAMP(6));
