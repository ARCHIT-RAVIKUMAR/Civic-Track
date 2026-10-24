-- 1) Comments (conversation between citizen, authority and field worker)
CREATE TABLE complaint_comments (
    id           UUID          PRIMARY KEY,
    complaint_id UUID          NOT NULL REFERENCES complaints (id),
    author_id    UUID          NOT NULL REFERENCES users (id),
    message      VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL
);
CREATE INDEX idx_comments_complaint ON complaint_comments (complaint_id);

-- 2) Optional map location of a complaint
ALTER TABLE complaints ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE complaints ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE complaints ADD CONSTRAINT ck_complaints_coordinates CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));
