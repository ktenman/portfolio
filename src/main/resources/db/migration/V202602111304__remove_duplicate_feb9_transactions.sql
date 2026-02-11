DELETE FROM portfolio_transaction
WHERE ctid IN (
  SELECT ctid FROM portfolio_transaction
  WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR')
    AND transaction_date = '2026-02-09'
    AND platform = 'SWEDBANK'
    AND transaction_type = 'BUY'
  LIMIT 1
);

DELETE FROM portfolio_transaction
WHERE ctid IN (
  SELECT ctid FROM portfolio_transaction
  WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR')
    AND transaction_date = '2026-02-09'
    AND platform = 'SWEDBANK'
    AND transaction_type = 'SELL'
  LIMIT 1
);
