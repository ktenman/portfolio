UPDATE portfolio_transaction
SET price = 31.47
WHERE instrument_id = (SELECT id
                       FROM instrument
                       WHERE symbol = 'QDVE:GER:EUR')
  AND transaction_type = 'BUY'
  AND quantity = 92
  AND transaction_date = '2025-01-27'
  AND platform = 'SWEDBANK';
