UPDATE portfolio_transaction
SET transaction_date = '2026-02-25'
WHERE transaction_date = '2026-02-27'
  AND platform = 'LHV'
  AND transaction_type = 'BUY';
