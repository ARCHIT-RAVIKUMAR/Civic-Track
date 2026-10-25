-- 1) Every complaint gets a short reference number (e.g. CT-AB12-CD34) that citizens can use to track it
ALTER TABLE complaints ADD COLUMN reference_number VARCHAR(20);
UPDATE complaints
   SET reference_number = 'CT-' || upper(substr(md5(id::text), 1, 4)) || '-' || upper(substr(md5(id::text), 5, 4))
 WHERE reference_number IS NULL;
ALTER TABLE complaints ALTER COLUMN reference_number SET NOT NULL;
ALTER TABLE complaints ADD CONSTRAINT uq_complaints_reference UNIQUE (reference_number);

-- 2) Announcements shown to everyone (e.g. "Water supply off in Ward 5 tomorrow")
CREATE TABLE announcements (
    id         UUID          PRIMARY KEY,
    title      VARCHAR(150)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    severity   VARCHAR(10)   NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_by UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP     NOT NULL,
    updated_at TIMESTAMP     NOT NULL,
    CONSTRAINT ck_announcements_severity CHECK (severity IN ('INFO', 'WARNING', 'URGENT'))
);
CREATE INDEX idx_announcements_active ON announcements (active, created_at);
