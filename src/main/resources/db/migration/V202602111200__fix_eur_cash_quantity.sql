UPDATE portfolio_transaction
SET quantity = 2872.19
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND transaction_date = '2026-02-09'
  AND platform = 'SWEDBANK'
  AND quantity = 2876.22;
