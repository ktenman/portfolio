UPDATE portfolio_transaction
SET transaction_date = '2025-11-17'
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR')
  AND transaction_type = 'BUY'
  AND quantity = 1
  AND price = 14.25
  AND transaction_date = '2025-11-14'
  AND platform = 'LHV';
