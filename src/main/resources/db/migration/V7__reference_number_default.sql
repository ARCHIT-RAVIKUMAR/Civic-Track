-- Safety net: if a complaint is ever inserted without a reference number (for example by an older version of
-- the application that is still running while the database is already updated), the database creates one itself.
-- The current application always sets its own reference number, so this default is normally never used.
ALTER TABLE complaints ALTER COLUMN reference_number SET DEFAULT (
    'CT-' || upper(substr(md5(random()::text), 1, 4)) || '-' || upper(substr(md5(random()::text), 1, 4))
);
