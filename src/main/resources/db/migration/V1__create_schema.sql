-- CivicTrack schema (PostgreSQL). Flyway runs this once, in order of version number.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CITIZEN', 'AUTHORITY', 'FIELD_WORKER', 'ADMIN'))
);

CREATE TABLE complaint_categories (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE complaints (
    id               UUID          PRIMARY KEY,
    title            VARCHAR(150)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    location         VARCHAR(255)  NOT NULL,
    category_id      UUID          NOT NULL REFERENCES complaint_categories (id),
    citizen_id       UUID          NOT NULL REFERENCES users (id),
    status           VARCHAR(20)   NOT NULL,
    priority         VARCHAR(10)   NOT NULL,
    rejection_reason VARCHAR(500),
    image_filename   VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    CONSTRAINT ck_complaints_status CHECK (status IN
        ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
    CONSTRAINT ck_complaints_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);
CREATE INDEX idx_complaints_citizen    ON complaints (citizen_id);
CREATE INDEX idx_complaints_category   ON complaints (category_id);
CREATE INDEX idx_complaints_status     ON complaints (status);
CREATE INDEX idx_complaints_created_at ON complaints (created_at);

CREATE TABLE assignments (
    id          UUID          PRIMARY KEY,
    complaint_id UUID         NOT NULL REFERENCES complaints (id),
    worker_id   UUID          NOT NULL REFERENCES users (id),
    assigned_by UUID          NOT NULL REFERENCES users (id),
    notes       VARCHAR(1000),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL
);
CREATE INDEX idx_assignments_worker ON assignments (worker_id);
-- A complaint can have only ONE active assignment at a time
CREATE UNIQUE INDEX ux_assignments_one_active ON assignments (complaint_id) WHERE active;

CREATE TABLE complaint_status_history (
    id           UUID          PRIMARY KEY,
    complaint_id UUID          NOT NULL REFERENCES complaints (id),
    old_status   VARCHAR(20),
    new_status   VARCHAR(20)   NOT NULL,
    changed_by   UUID          NOT NULL REFERENCES users (id),
    notes        VARCHAR(1000),
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL
);
CREATE INDEX idx_history_complaint ON complaint_status_history (complaint_id);

CREATE TABLE feedback (
    id           UUID          PRIMARY KEY,
    complaint_id UUID          NOT NULL REFERENCES complaints (id),
    citizen_id   UUID          NOT NULL REFERENCES users (id),
    rating       INTEGER       NOT NULL,
    comments     VARCHAR(1000),
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL,
    CONSTRAINT uq_feedback_complaint UNIQUE (complaint_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
