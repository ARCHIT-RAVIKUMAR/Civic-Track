-- gen_random_uuid() is built into PostgreSQL 13+ (Supabase uses 15+)
INSERT INTO complaint_categories (id, name, description, active, created_at, updated_at) VALUES
 (gen_random_uuid(), 'Pothole / Damaged Road', 'Potholes, cracks and damaged road surfaces', TRUE, now(), now()),
 (gen_random_uuid(), 'Garbage Accumulation',   'Uncollected or overflowing garbage',         TRUE, now(), now()),
 (gen_random_uuid(), 'Broken Streetlight',     'Streetlights that are off or damaged',       TRUE, now(), now()),
 (gen_random_uuid(), 'Water Leakage',          'Leaking pipes and water supply problems',    TRUE, now(), now()),
 (gen_random_uuid(), 'Drainage Issue',         'Blocked or overflowing drains',              TRUE, now(), now()),
 (gen_random_uuid(), 'Other',                  'Any other civic infrastructure problem',     TRUE, now(), now());
