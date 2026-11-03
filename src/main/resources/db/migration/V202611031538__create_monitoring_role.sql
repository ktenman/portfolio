DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'monitoring') THEN
        CREATE ROLE monitoring LOGIN;
    END IF;
END $$;

GRANT pg_monitor TO monitoring;
