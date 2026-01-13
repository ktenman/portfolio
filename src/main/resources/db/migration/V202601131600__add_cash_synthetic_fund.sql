INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name, created_at, updated_at, version)
VALUES ('CASH', 'Cash Holdings', 'ETF', 'EUR', 'SYNTHETIC', NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO etf_holding (name, ticker, sector, uuid, created_at, updated_at, version)
VALUES ('Euro Cash', 'EUR', 'Cash', gen_random_uuid(), NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO etf_position (etf_instrument_id, holding_id, snapshot_date, weight_percentage, created_at, updated_at, version)
SELECT
  (SELECT id FROM instrument WHERE symbol = 'CASH'),
  h.id,
  CURRENT_DATE,
  0,
  NOW(), NOW(), 0
FROM etf_holding h
WHERE h.ticker = 'EUR' AND h.name = 'Euro Cash'
  AND NOT EXISTS (
    SELECT 1 FROM etf_position p
    WHERE p.etf_instrument_id = (SELECT id FROM instrument WHERE symbol = 'CASH')
      AND p.holding_id = h.id
  );
