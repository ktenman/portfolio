-- Add provider_name column to instrument table
ALTER TABLE instrument
  ADD COLUMN provider_name VARCHAR(50);

-- Set default value for existing rows
UPDATE instrument
SET provider_name = 'ALPHA_VANTAGE';

-- Make the column NOT NULL after setting default values
ALTER TABLE instrument
  ALTER COLUMN provider_name SET NOT NULL;

-- Add a check constraint to ensure only valid values are inserted
ALTER TABLE instrument
  ADD CONSTRAINT check_provider_name
    CHECK (provider_name IN ('ALPHA_VANTAGE', 'BINANCE'));

-- Create an index on the new column for better query performance
CREATE INDEX idx_instrument_provider_name ON instrument (provider_name);

-- Update the updated_at timestamp for all rows
UPDATE instrument
SET updated_at = CURRENT_TIMESTAMP;
