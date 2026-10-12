WITH constituent AS (
  SELECT DISTINCT fund.id AS fund_id,
         coin.id AS instrument_id,
         GREATEST(COALESCE(held.quantity, 0) * COALESCE(coin.current_price, 0), 0) AS worth
  FROM instrument fund
  JOIN etf_position ep ON ep.etf_instrument_id = fund.id
  JOIN etf_holding holding ON holding.id = ep.holding_id
  JOIN instrument coin ON coin.symbol = holding.ticker
  LEFT JOIN (
    SELECT instrument_id, SUM(CASE WHEN transaction_type = 'BUY' THEN quantity ELSE -quantity END) AS quantity
    FROM portfolio_transaction
    GROUP BY instrument_id
  ) held ON held.instrument_id = coin.id
  WHERE fund.provider_name = 'SYNTHETIC'
    AND ep.snapshot_date = (SELECT MAX(latest.snapshot_date) FROM etf_position latest WHERE latest.etf_instrument_id = fund.id)
),
split AS (
  SELECT fund_id,
         instrument_id,
         CASE WHEN SUM(worth) OVER per_fund > 0 THEN worth / SUM(worth) OVER per_fund ELSE 1.0 / COUNT(*) OVER per_fund END AS fraction,
         ROW_NUMBER() OVER (PARTITION BY fund_id ORDER BY worth DESC, instrument_id) AS place
  FROM constituent
  WINDOW per_fund AS (PARTITION BY fund_id)
),
part AS (
  SELECT config.id AS config_id,
         item.ordinal,
         item.allocation,
         split.instrument_id,
         split.place,
         (item.allocation ->> 'value')::numeric AS target,
         ROUND((item.allocation ->> 'value')::numeric * split.fraction, 2) AS amount
  FROM diversification_config config
  CROSS JOIN LATERAL jsonb_array_elements(config.config_data::jsonb -> 'allocations') WITH ORDINALITY AS item(allocation, ordinal)
  LEFT JOIN split ON to_jsonb(split.fund_id) = item.allocation -> 'instrumentId'
),
replaced AS (
  SELECT config_id,
         ordinal,
         place,
         instrument_id,
         CASE WHEN instrument_id IS NULL THEN allocation
              ELSE jsonb_build_object(
                'instrumentId', instrument_id,
                'value', trim_scale(amount + CASE WHEN place = 1 THEN target - SUM(amount) OVER (PARTITION BY config_id, ordinal) ELSE 0 END))
         END AS allocation
  FROM part
),
rewritten AS (
  SELECT config_id, jsonb_agg(allocation ORDER BY ordinal, place) AS allocations
  FROM replaced
  GROUP BY config_id
  HAVING COUNT(instrument_id) > 0
)
UPDATE diversification_config config
SET config_data = jsonb_set(config.config_data::jsonb, '{allocations}', rewritten.allocations)::text
FROM rewritten
WHERE rewritten.config_id = config.id;
