ALTER TABLE etf_holding ADD COLUMN name_block_key VARCHAR(255);

UPDATE etf_holding
SET name_block_key = split_part(
  trim(both ' ' from regexp_replace(lower(name), '[^a-z0-9]+', ' ', 'g')),
  ' ',
  1
);

CREATE INDEX idx_etf_holding_name_block_key ON etf_holding (name_block_key);
