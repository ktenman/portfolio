-- Rename etf_positions to etf_position to match entity
ALTER TABLE etf_positions RENAME TO etf_position;

-- Add id column as primary key
ALTER TABLE etf_position ADD COLUMN id BIGSERIAL;

-- Add updated_at and version columns (required by BaseEntity)
ALTER TABLE etf_position ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE etf_position ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Drop the old composite primary key
ALTER TABLE etf_position DROP CONSTRAINT etf_positions_pkey;

-- Set id as the new primary key
ALTER TABLE etf_position ADD PRIMARY KEY (id);

-- Re-create the unique constraint for the composite key
ALTER TABLE etf_position ADD CONSTRAINT etf_position_unique_key
  UNIQUE (etf_instrument_id, holding_id, snapshot_date);

-- Update indexes to use new table name
DROP INDEX IF EXISTS idx_etf_positions_snapshot;
DROP INDEX IF EXISTS idx_etf_positions_weight;

CREATE INDEX idx_etf_position_snapshot ON etf_position (etf_instrument_id, snapshot_date);
CREATE INDEX idx_etf_position_weight ON etf_position (weight_percentage DESC);
