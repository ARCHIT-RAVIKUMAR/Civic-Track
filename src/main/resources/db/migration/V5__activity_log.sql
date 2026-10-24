-- What each account has done in the system (logins, security events, admin actions)
CREATE TABLE activity_log (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id),
    actor_id      UUID         REFERENCES users (id),
    activity_type VARCHAR(40)  NOT NULL,
    details       VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
CREATE INDEX idx_activity_user ON activity_log (user_id, created_at);
