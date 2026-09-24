UPDATE portfolio_transaction
SET platform = 'TRADING212'
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND platform = 'SWEDBANK'
  AND quantity = 13719.16
  AND price = 1
  AND commission = 0
  AND (
    (transaction_type = 'BUY' AND transaction_date = '2025-12-16')
    OR (transaction_type = 'SELL' AND transaction_date = '2025-12-17')
  );
