CREATE OR REPLACE FUNCTION truncate_all_except(exclusions text[])
  RETURNS VOID
AS
'
  DECLARE
    table_record RECORD;
  BEGIN
    FOR table_record IN SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = ''public''
                          AND table_type = ''BASE TABLE''
      LOOP
        IF NOT table_record.table_name = ANY (exclusions)
        THEN
          EXECUTE format(''TRUNCATE TABLE %I RESTART IDENTITY CASCADE'', table_record.table_name);
        END IF;
      END LOOP;
  END;
'
  LANGUAGE plpgsql;

SELECT truncate_all_except(ARRAY ['flyway_schema_history']);
