-- First-login welcome guide: a new account sees the introduction once.
-- DEFAULT FALSE keeps older versions of the application working while this migration is already applied.
ALTER TABLE users ADD COLUMN welcome_seen BOOLEAN NOT NULL DEFAULT FALSE;
-- people who already have an account do not need the introduction
UPDATE users SET welcome_seen = TRUE;
