UPDATE portfolio_transaction
SET quantity = 2895.34
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND transaction_date = '2026-02-25'
  AND platform = 'SWEDBANK'
  AND transaction_type = 'SELL'
  AND quantity = 2892.55;

DELETE FROM portfolio_transaction
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND transaction_date = '2026-03-11'
  AND platform = 'SWEDBANK'
  AND transaction_type = 'SELL'
  AND quantity = 2.79;
