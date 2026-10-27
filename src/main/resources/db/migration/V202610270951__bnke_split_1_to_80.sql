UPDATE portfolio_transaction
SET quantity = quantity * 80, price = price / 80
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR')
  AND transaction_date < '2026-10-27';

UPDATE daily_price
SET open_price = open_price / 80, high_price = high_price / 80, low_price = low_price / 80, close_price = close_price / 80
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR')
  AND entry_date < '2026-10-27';

UPDATE price_snapshot
SET price = price / 80
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR')
  AND snapshot_hour < '2026-10-27 07:00:00+00';

UPDATE instrument_minute_price
SET price = price / 80
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR')
  AND captured_at < '2026-10-27 07:00:00+00';

DELETE FROM portfolio_intraday_summary
WHERE platform_key IN ('', 'LHV')
  AND captured_at >= '2026-10-27 07:17:00+00';
