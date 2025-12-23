UPDATE portfolio_transaction
SET transaction_date = '2025-12-16'
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND platform = 'SWEDBANK'
  AND transaction_type = 'BUY'
  AND quantity = 13719.16;

UPDATE daily_price
SET entry_date = '2025-12-16'
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND provider_name = 'MANUAL';
