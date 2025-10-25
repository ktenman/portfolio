ALTER TABLE instrument
ADD COLUMN logo_url VARCHAR(500);

COMMENT ON COLUMN instrument.logo_url IS 'URL to the company logo image';
