UPDATE portfolio_transaction
SET instrument_id = (SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR')
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'QDVF:GER:EUR')
  AND transaction_date = '2026-03-11'
  AND platform = 'LIGHTYEAR'
  AND transaction_type = 'SELL'
  AND quantity = 5.506566604;
