-- 1) In-app notifications
CREATE TABLE notifications (
    id           UUID         PRIMARY KEY,
    recipient_id UUID         NOT NULL REFERENCES users (id),
    complaint_id UUID         REFERENCES complaints (id),
    title        VARCHAR(150) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, is_read);

-- 2) New complaint status WITHDRAWN (citizen cancels before assignment)
ALTER TABLE complaints DROP CONSTRAINT ck_complaints_status;
ALTER TABLE complaints ADD CONSTRAINT ck_complaints_status CHECK (status IN
    ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED', 'WITHDRAWN'));
