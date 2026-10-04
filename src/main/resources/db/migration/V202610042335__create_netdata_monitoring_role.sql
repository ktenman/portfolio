DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'netdata') THEN
        CREATE ROLE netdata LOGIN;
    END IF;
END $$;

GRANT pg_monitor TO netdata;
