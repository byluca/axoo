BEGIN;

DROP TABLE IF EXISTS public.campus_events;
ALTER TABLE IF EXISTS public.users DROP COLUMN IF EXISTS can_create_events;

COMMIT;
