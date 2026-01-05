UPDATE portfolio_transaction
SET commission = 1.00
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'WBIT:GER:EUR')
  AND transaction_date = '2026-01-02'
  AND transaction_type = 'SELL';
