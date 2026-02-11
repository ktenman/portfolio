UPDATE portfolio_transaction
SET transaction_type = 'SELL'
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR')
  AND transaction_date = '2026-02-09'
  AND platform = 'SWEDBANK'
  AND transaction_type = 'BUY';

UPDATE portfolio_transaction
SET transaction_type = 'BUY'
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
  AND transaction_date = '2026-02-09'
  AND platform = 'SWEDBANK'
  AND transaction_type = 'SELL';
